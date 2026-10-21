package app.agcodespace.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

/** Downloads Ubuntu Base, validates the archive against Ubuntu's published SHA256SUMS, and safely extracts it. */
class UbuntuInstaller(private val context: Context) {
    companion object {
        const val ARCHIVE_URL = "https://cdimages.ubuntu.com/cdimage/ubuntu-base/releases/resolute/release/ubuntu-base-26.04-base-arm64.tar.gz"
        const val SUMS_URL = "https://cdimages.ubuntu.com/cdimage/ubuntu-base/releases/resolute/release/SHA256SUMS"
    }
    val rootfs: File get() = File(context.filesDir, "ubuntu/rootfs")
    private val baseDir get() = File(context.filesDir, "ubuntu")
    fun isInstalled(): Boolean = File(rootfs, "etc/os-release").isFile && File(rootfs, ".agcodespace-installed").isFile

    suspend fun install(progress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        if (android.os.Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) throw IllegalStateException("This Ubuntu ARM64 build requires an arm64-v8a Android device.")
        val freeBytes = android.os.StatFs(context.filesDir.path).availableBytes
        if (freeBytes < 500L * 1024L * 1024L) throw IllegalStateException("At least 500 MiB of free app storage is required to download, extract, and verify Ubuntu Base.")
        baseDir.mkdirs()
        val archive = File(baseDir, "ubuntu-base-arm64.tar.gz.part")
        val sums = File(baseDir, "SHA256SUMS")
        progress("Downloading Ubuntu checksum manifest", 1)
        download(SUMS_URL, sums) { p -> progress("Downloading Ubuntu checksum manifest", p) }
        val expected = sums.readLines().firstOrNull { it.trim().endsWith("ubuntu-base-26.04-base-arm64.tar.gz") }
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
            ?: throw IllegalStateException("Ubuntu SHA256SUMS does not contain the expected ARM64 archive.")
        progress("Downloading Ubuntu Base ARM64", 3)
        download(ARCHIVE_URL, archive) { p -> progress("Downloading Ubuntu Base ARM64", 3 + (p * 45 / 100)) }
        progress("Verifying SHA-256", 50)
        val actual = sha256(archive)
        if (!actual.equals(expected, ignoreCase = true)) {
            archive.delete()
            throw SecurityException("Ubuntu archive checksum mismatch. Expected $expected but got $actual")
        }
        val staging = File(baseDir, "rootfs.staging")
        staging.deleteRecursively(); staging.mkdirs()
        progress("Extracting verified root filesystem", 52)
        try {
            TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
                var entry = tar.nextTarEntry
                var count = 0
                var lastProgress = 52
                val pendingSymlinks = mutableListOf<Pair<File, String>>()
                while (entry != null) {
                    val out = File(staging, entry.name).canonicalFile
                    if (out != staging.canonicalFile && !out.path.startsWith(staging.canonicalPath + File.separator)) throw SecurityException("Unsafe path in rootfs archive: ${entry.name}")
                    when {
                        entry.isDirectory -> out.mkdirs()
                        entry.isSymbolicLink -> {
                            // Defer links until regular files are extracted so archive paths cannot
                            // traverse a symlink and write outside the staging root.
                            out.parentFile?.mkdirs()
                            pendingSymlinks += out to entry.linkName
                        }
                        entry.isLink -> {
                            out.parentFile?.mkdirs()
                            val target = if (entry.linkName.startsWith("/")) File(staging, entry.linkName.removePrefix("/")).canonicalFile else File(staging, entry.linkName).canonicalFile
                            if (target == staging.canonicalFile || !target.path.startsWith(staging.canonicalPath + File.separator)) throw SecurityException("Unsafe hardlink target: ${entry.linkName}")
                            if (!target.exists()) throw IllegalStateException("Hardlink target is missing: ${entry.linkName}")
                            android.system.Os.link(target.path, out.path)
                        }
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { output ->
                                var remaining = entry.size
                                val buffer = ByteArray(64 * 1024)
                                while (remaining > 0) {
                                    val n = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (n < 0) throw IllegalStateException("Unexpected end of archive at ${entry.name}")
                                    output.write(buffer, 0, n); remaining -= n
                                }
                            }
                            out.setReadable(true, false)
                            if (entry.mode and 0b001001001 != 0) out.setExecutable(true, false)
                            if (entry.mode and 0b010000000 != 0) out.setWritable(true, false)
                        }
                    }
                    count++
                    if (count % 100 == 0) {
                        lastProgress = maxOf(lastProgress, (52 + count / 100).coerceAtMost(92))
                        progress("Extracting root filesystem ($count entries)", lastProgress)
                    }
                    entry = tar.nextTarEntry
                }
                for ((linkFile, linkTarget) in pendingSymlinks) {
                    val guestTarget = if (linkTarget.startsWith("/")) File(staging, linkTarget.removePrefix("/")) else File(linkFile.parentFile, linkTarget)
                    val resolved = guestTarget.canonicalFile
                    if (resolved != staging.canonicalFile && !resolved.path.startsWith(staging.canonicalPath + File.separator)) {
                        throw SecurityException("Symlink target escapes rootfs: ${linkFile.relativeTo(staging)} -> $linkTarget")
                    }
                    android.system.Os.symlink(linkTarget, linkFile.path)
                }
            }
            if (!File(staging, "bin").exists() || !File(staging, "etc/os-release").exists()) throw IllegalStateException("The archive extracted but does not look like a valid Ubuntu root filesystem.")
            val resolvConf = File(staging, "etc/resolv.conf")
            if (Files.isSymbolicLink(resolvConf.toPath())) android.system.Os.unlink(resolvConf.path)
            resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(staging, "root").mkdirs()
            File(staging, ".agcodespace-installed").writeText("archive=$ARCHIVE_URL\nsha256=$actual\n")
            val previous = File(baseDir, "rootfs.previous")
            previous.deleteRecursively()
            if (rootfs.exists() && !rootfs.renameTo(previous)) throw IllegalStateException("Could not preserve the existing Ubuntu root filesystem before update.")
            if (!staging.renameTo(rootfs)) {
                if (previous.exists()) previous.renameTo(rootfs)
                throw IllegalStateException("Could not activate the extracted Ubuntu root filesystem.")
            }
            previous.deleteRecursively()
            archive.delete()
            progress("Ubuntu installed", 100)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".download")
        tmp.delete()
        target.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 30000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "AGCodespace/1.0") }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Download failed with HTTP ${connection.responseCode}: $url")
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input -> FileOutputStream(tmp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); done += n; if (total > 0) onProgress((done * 100 / total).toInt()) }
                output.fd.sync()
            } }
            if (!tmp.renameTo(target)) throw IllegalStateException("Unable to finalize download: ${target.name}")
        } finally { connection.disconnect(); tmp.delete() }
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
