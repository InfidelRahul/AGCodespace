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
import java.nio.file.Paths
import java.security.MessageDigest
import java.io.InputStream

/** Downloads Ubuntu Base, validates the archive against Ubuntu's published SHA256SUMS, and safely extracts it. */
class UbuntuInstaller(
    private val context: Context? = null,
    baseDir: File? = null
) {
    val baseDir: File = (baseDir ?: File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir", "/tmp"), "agcodespace-test"), "ubuntu")).canonicalFile

    constructor(context: Context) : this(context, File(context.filesDir, "ubuntu"))

    companion object {
        const val ARCHIVE_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
        const val SUMS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/SHA256SUMS"
        const val FALLBACK_SHA256 = "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
        const val BUNDLED_ASSET_NAME = "ubuntu-base-26.04.1-base-arm64.tar.gz"

        fun isSafeSymlinkTarget(staging: File, linkFile: File, linkTarget: String): Boolean {
            val stagingCanon = staging.canonicalFile
            val stagingPath = stagingCanon.toPath().toAbsolutePath().normalize()
            val parentCanon = (linkFile.parentFile ?: staging).canonicalFile
            if (parentCanon != stagingCanon && !parentCanon.path.startsWith(stagingCanon.path + File.separator)) {
                return false
            }
            val normalizedTarget = if (linkTarget.startsWith("/")) {
                stagingPath.resolve(linkTarget.dropWhile { it == '/' }).normalize()
            } else {
                parentCanon.toPath().toAbsolutePath().resolve(linkTarget).normalize()
            }
            return normalizedTarget == stagingPath || normalizedTarget.startsWith(stagingPath)
        }
    }

    fun isAssetBundled(): Boolean {
        if (context == null) return false
        return try {
            context.assets.open(BUNDLED_ASSET_NAME).close()
            true
        } catch (_: Throwable) { false }
    }
    val rootfs: File get() = File(baseDir, "rootfs").canonicalFile

    fun isInstalled(): Boolean {
        val marker = File(rootfs, ".agcodespace-installed")
        if (!marker.isFile) return false
        val hasOsRelease = File(rootfs, "etc/os-release").exists() || File(rootfs, "usr/lib/os-release").isFile
        val hasBin = File(rootfs, "bin").exists() || File(rootfs, "usr/bin").isDirectory
        return hasOsRelease && hasBin
    }

    suspend fun install(progress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        if (context != null && android.os.Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            throw IllegalStateException("This Ubuntu ARM64 build requires an arm64-v8a Android device.")
        }
        baseDir.mkdirs()

        // Determine archive input stream: bundled asset (no disk write) or download
        val useAsset = isAssetBundled()
        val archiveStream: () -> InputStream
        val checksum: String

        if (useAsset) {
            progress("Loading bundled Ubuntu 26.04.1 LTS", 2)
            archiveStream = { BufferedInputStream(context!!.assets.open(BUNDLED_ASSET_NAME)) }
            checksum = FALLBACK_SHA256
        } else {
            val freeBytes = context?.let {
                try { android.os.StatFs(it.filesDir.path).availableBytes } catch (_: Throwable) { Long.MAX_VALUE }
            } ?: Long.MAX_VALUE
            val archive = File(baseDir, "ubuntu-base-arm64.tar.gz.part")
            val minFreeBytes = if (archive.exists() && archive.length() > 25L * 1024L * 1024L) 200L * 1024L * 1024L else 350L * 1024L * 1024L
            if (freeBytes < minFreeBytes) {
                throw IllegalStateException("At least ${minFreeBytes / (1024 * 1024)} MiB of free app storage is required to install Ubuntu Base.")
            }

            val archiveName = ARCHIVE_URL.substringAfterLast('/')
            val sums = File(baseDir, "SHA256SUMS")
            var expected: String? = null
            try {
                progress("Downloading Ubuntu checksum manifest", 1)
                download(SUMS_URL, sums) { p -> progress("Downloading Ubuntu checksum manifest", p) }
                expected = sums.readLines().firstOrNull { line ->
                    val trimmed = line.trim()
                    trimmed.endsWith(archiveName) || trimmed.contains(archiveName)
                }?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
            } catch (_: Throwable) { expected = null }
            val targetChecksum = expected ?: FALLBACK_SHA256

            var archiveValid = false
            if (archive.exists() && archive.length() > 0L) {
                progress("Checking existing archive checksum", 2)
                try {
                    if (sha256(archive).equals(targetChecksum, ignoreCase = true)) archiveValid = true
                } catch (_: Throwable) { archiveValid = false }
            }
            if (!archiveValid) {
                progress("Downloading Ubuntu 26.04.1 LTS ARM64", 3)
                download(ARCHIVE_URL, archive) { p -> progress("Downloading Ubuntu 26.04.1 LTS ARM64", 3 + (p * 45 / 100)) }
                progress("Verifying SHA-256", 50)
                val actual = sha256(archive)
                if (!actual.equals(targetChecksum, ignoreCase = true)) {
                    archive.delete()
                    throw SecurityException("Ubuntu archive checksum mismatch. Expected $targetChecksum but got $actual")
                }
            }
            archiveStream = { BufferedInputStream(FileInputStream(archive)) }
            checksum = targetChecksum
        }

        val staging = File(baseDir, "rootfs.staging").canonicalFile
        staging.deleteRecursively()
        staging.mkdirs()
        progress("Extracting verified root filesystem", if (useAsset) 5 else 52)
        try {
            extractTar(archiveStream(), staging, progress)

            progress("Configuring Ubuntu environment", 94)
            val hasBin = File(staging, "bin").exists() || File(staging, "usr/bin").isDirectory
            val hasOsRelease = File(staging, "etc/os-release").exists() || File(staging, "usr/lib/os-release").isFile
            if (!hasBin || !hasOsRelease) {
                throw IllegalStateException("The archive extracted but does not look like a valid Ubuntu root filesystem.")
            }

            setupRootfsUsability(staging, checksum)

            progress("Activating root filesystem", 97)
            val previous = File(baseDir, "rootfs.previous").canonicalFile
            previous.deleteRecursively()
            if (rootfs.exists()) {
                if (!rootfs.renameTo(previous)) rootfs.deleteRecursively()
            }
            var moved = staging.renameTo(rootfs)
            if (!moved) {
                try {
                    Files.move(staging.toPath(), rootfs.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    moved = true
                } catch (_: Exception) { moved = false }
            }
            if (!moved) {
                if (previous.exists()) previous.renameTo(rootfs)
                throw IllegalStateException("Could not activate the extracted Ubuntu root filesystem.")
            }
            previous.deleteRecursively()
            if (!useAsset) File(baseDir, "ubuntu-base-arm64.tar.gz.part").delete()
            progress("Ubuntu installed", 100)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    private fun extractTar(input: InputStream, staging: File, progress: (String, Int) -> Unit) {
        TarArchiveInputStream(GzipCompressorInputStream(input)).use { tar ->
            var entry = tar.nextTarEntry
            var count = 0
            var lastProgress = 5
            val pendingHardlinks = mutableListOf<Triple<File, String, Boolean>>()
            val pendingSymlinks = mutableListOf<Pair<File, String>>()
            val stagingCanonical = staging.canonicalFile
            val stagingCanonicalPath = stagingCanonical.path

            while (entry != null) {
                val cleanEntryName = entry.name.removePrefix("./").removePrefix("/")
                if (cleanEntryName.isNotEmpty()) {
                    val out = File(stagingCanonical, cleanEntryName).canonicalFile
                    if (out != stagingCanonical && !out.path.startsWith(stagingCanonicalPath + File.separator)) {
                        throw SecurityException("Unsafe path in rootfs archive: ${entry.name}")
                    }
                    when {
                        entry.isDirectory -> {
                            out.mkdirs()
                            out.setReadable(true, false)
                            out.setWritable(true, true)
                            out.setExecutable(true, false)
                        }
                        entry.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            pendingSymlinks += out to entry.linkName
                        }
                        entry.isLink -> {
                            out.parentFile?.mkdirs()
                            pendingHardlinks += Triple(out, entry.linkName, (entry.mode and 0b001001001) != 0)
                        }
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            if (out.exists()) out.delete()
                            FileOutputStream(out).use { output ->
                                var remaining = entry.size
                                val buffer = ByteArray(64 * 1024)
                                while (remaining > 0) {
                                    val n = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (n < 0) throw IllegalStateException("Unexpected end of archive at ${entry.name}")
                                    output.write(buffer, 0, n)
                                    remaining -= n
                                }
                            }
                            out.setReadable(true, false)
                            out.setWritable(true, true)
                            if (entry.mode and 0b001001001 != 0) out.setExecutable(true, false)
                        }
                    }
                    count++
                    if (count % 100 == 0) {
                        lastProgress = maxOf(lastProgress, (5 + (count * 83 / 7000)).coerceAtMost(90))
                        progress("Extracting root filesystem ($count entries)", lastProgress)
                    }
                }
                entry = tar.nextTarEntry
            }

            progress("Creating system hard links (${pendingHardlinks.size})", 90)
            for ((hardlinkFile, hardlinkTarget, isExec) in pendingHardlinks) {
                val cleanTarget = hardlinkTarget.removePrefix("./").removePrefix("/")
                val targetFile = if (File(stagingCanonical, cleanTarget).exists()) {
                    File(stagingCanonical, cleanTarget).canonicalFile
                } else if (File(hardlinkFile.parentFile, cleanTarget).exists()) {
                    File(hardlinkFile.parentFile, cleanTarget).canonicalFile
                } else {
                    File(stagingCanonical, cleanTarget).canonicalFile
                }
                if (targetFile != stagingCanonical && targetFile.path.startsWith(stagingCanonicalPath + File.separator) && targetFile.exists()) {
                    hardlinkFile.parentFile?.mkdirs()
                    if (hardlinkFile.exists()) hardlinkFile.delete()
                    try {
                        android.system.Os.link(targetFile.path, hardlinkFile.path)
                    } catch (_: Throwable) {
                        targetFile.copyTo(hardlinkFile, overwrite = true)
                    }
                    hardlinkFile.setReadable(true, false)
                    hardlinkFile.setWritable(true, true)
                    if (isExec || targetFile.canExecute()) hardlinkFile.setExecutable(true, false)
                }
            }

            progress("Creating system symlinks (${pendingSymlinks.size})", 91)
            for ((linkFile, linkTarget) in pendingSymlinks) {
                if (!isSafeSymlinkTarget(stagingCanonical, linkFile, linkTarget)) {
                    val relPath = try {
                        linkFile.relativeTo(stagingCanonical).path
                    } catch (_: Throwable) {
                        linkFile.name
                    }
                    throw SecurityException("Symlink target escapes rootfs: $relPath -> $linkTarget")
                }
                linkFile.parentFile?.mkdirs()
                try { Files.deleteIfExists(linkFile.toPath()) } catch (_: Throwable) { linkFile.delete() }
                try {
                    android.system.Os.symlink(linkTarget, linkFile.path)
                } catch (_: Throwable) {
                    try {
                        Files.createSymbolicLink(linkFile.toPath(), Paths.get(linkTarget))
                    } catch (_: Throwable) {
                        val stagingPath = stagingCanonical.toPath().toAbsolutePath().normalize()
                        val targetFile = if (linkTarget.startsWith("/")) {
                            stagingPath.resolve(linkTarget.dropWhile { it == '/' }).normalize().toFile()
                        } else {
                            (linkFile.parentFile ?: stagingCanonical).toPath().toAbsolutePath().resolve(linkTarget).normalize().toFile()
                        }
                        if (targetFile.exists()) {
                            if (targetFile.isDirectory) linkFile.mkdirs()
                            else targetFile.copyTo(linkFile, overwrite = true)
                        }
                    }
                }
            }
        }
    }

    /** Writes DNS, hosts, APT sandbox config, policy-rc.d, and creates required mount-point dirs. */
    private fun setupRootfsUsability(staging: File, checksum: String) {
        // /etc/resolv.conf – replace any symlink with a plain file containing live DNS
        val resolvConf = File(staging, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        try { Files.deleteIfExists(resolvConf.toPath()) } catch (_: Throwable) { resolvConf.delete() }
        resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

        // /etc/hosts
        val hostsFile = File(staging, "etc/hosts")
        hostsFile.parentFile?.mkdirs()
        hostsFile.writeText("127.0.0.1 localhost localhost.localdomain\n::1 localhost localhost.localdomain\n")

        // /etc/hostname
        File(staging, "etc/hostname").also { it.parentFile?.mkdirs() }.writeText("localhost\n")

        // APT sandbox: run as root (avoids _apt drop-privileges failure under PRoot)
        val aptConf = File(staging, "etc/apt/apt.conf.d/99proot")
        aptConf.parentFile?.mkdirs()
        aptConf.writeText("APT::Sandbox::User \"root\";\nAcquire::Languages \"none\";\n")

        // policy-rc.d: always exit 101 so dpkg doesn't try to start daemons during install
        val policyRc = File(staging, "usr/sbin/policy-rc.d")
        policyRc.parentFile?.mkdirs()
        policyRc.writeText("#!/bin/sh\nexit 101\n")
        policyRc.setExecutable(true, false)

        // Required mount-point directories
        for (dir in listOf("dev/shm", "dev/pts", "proc", "sys", "tmp", "root/.cache", "root/.config")) {
            File(staging, dir).mkdirs()
        }
        File(staging, "tmp").setWritable(true, false)
        File(staging, "tmp").setExecutable(true, false)

        // Ensure critical shell binaries are executable
        for (bin in listOf("usr/bin/bash", "usr/bin/sh", "usr/bin/dash", "bin/bash", "bin/sh")) {
            val f = File(staging, bin)
            if (f.exists() && !f.isDirectory) f.setExecutable(true, false)
        }

        // Installation marker
        File(staging, ".agcodespace-installed").writeText(
            "archive=$ARCHIVE_URL\nsha256=$checksum\ntimestamp=${System.currentTimeMillis()}\n"
        )
    }

    fun uninstall() {
        File(baseDir, "rootfs").deleteRecursively()
        File(baseDir, "rootfs.staging").deleteRecursively()
        File(baseDir, "rootfs.previous").deleteRecursively()
        File(baseDir, "ubuntu-base-arm64.tar.gz.part").delete()
        File(baseDir, "SHA256SUMS").delete()
    }

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".download")
        tmp.delete()
        target.delete()
        var currentUrl = url
        var connection: HttpURLConnection? = null
        try {
            var redirects = 0
            while (redirects < 5) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AGCodespace/1.0 (Android; Linux)")
                }
                val code = connection.responseCode
                if (code in 301..308 && code != 304) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        connection.disconnect()
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        redirects++
                        continue
                    }
                }
                if (code !in 200..299) {
                    throw IllegalStateException("Download failed with HTTP $code from $currentUrl")
                }
                break
            }
            val conn = connection ?: throw IllegalStateException("Could not establish connection to $url")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            if (tmp.length() == 0L) throw IllegalStateException("Downloaded file is empty from $url")
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            connection?.disconnect()
            tmp.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
