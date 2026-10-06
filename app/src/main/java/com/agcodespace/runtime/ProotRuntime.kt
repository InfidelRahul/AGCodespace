package com.agcodespace.runtime

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Owns the app-private Ubuntu/PRoot guest.
 *
 * AGCodespace deliberately uses the exact Ubuntu Base 26.04.1 ARM64 archive supplied for the
 * project. The archive is downloaded once, verified against Ubuntu's published SHA-256, and
 * extracted into app-private storage. No root permission is required.
 */
class ProotRuntime(private val context: Context) {
    companion object {
        const val UBUNTU_BASE_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
        const val UBUNTU_BASE_SHA256 =
            "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
        const val UBUNTU_BASE_SIZE_BYTES = 35_092_106L

        private const val BOOTSTRAP_SCRIPT = """
set -eu
export DEBIAN_FRONTEND=noninteractive
export PATH=/home/agcodespace/.local/bin:/usr/local/bin:/usr/bin:/bin
mkdir -p /home/agcodespace/.local/bin
if [ ! -x /usr/bin/ssh ]; then
  apt-get update
  apt-get install -y --no-install-recommends ca-certificates curl git openssh-client bash coreutils tar xz-utils gzip procps iproute2
fi
if ! command -v gh >/dev/null 2>&1; then
  apt-get update
  apt-get install -y --no-install-recommends gh || true
fi
if ! command -v gh >/dev/null 2>&1; then
  echo 'GitHub CLI was not available from the Ubuntu repositories; install a verified ARM64 gh package before using Codespaces.' >&2
  exit 20
fi
if [ ! -x /home/agcodespace/.local/bin/agy ]; then
  curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --skip-aliases --skip-path
fi
[ -x /home/agcodespace/.local/bin/agy ]
"""
    }

    private val root = File(context.filesDir, "linux")
    val rootfs = File(root, "rootfs")
    val proot = File(root, "bin/proot")
    val home = File(rootfs, "home/agcodespace")
    private val archive = File(root, "ubuntu-base-26.04.1-base-arm64.tar.gz")
    private val ready = File(root, ".ready")

    fun prepare(): Boolean {
        if (isInstalled()) return true
        if (android.os.Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return false

        return runCatching {
            root.mkdirs()
            installProotFromAsset()
            downloadUbuntuBaseIfNeeded()
            extractRootfs()
            configureGuest()
            ready.writeText("ubuntu-base-26.04.1-arm64\n$UBUNTU_BASE_SHA256\n")
            isInstalled()
        }.getOrDefault(false)
    }

    fun isInstalled(): Boolean =
        ready.isFile && proot.canExecute() && File(rootfs, "bin/sh").canExecute() && home.isDirectory

    /** Host-side command that enters the Ubuntu guest through the packaged PRoot binary. */
    fun command(command: String): String {
        check(isInstalled()) { "Linux userspace is not installed for this ABI." }
        root.mkdirs(); home.mkdirs()
        return buildString {
            append(proot.absolutePath)
            append(" -0 -r ").append(shellQuote(rootfs.absolutePath))
            append(" -b /proc:/proc -b /sys:/sys -b /dev:/dev")
            append(" -b ").append(shellQuote(context.filesDir.absolutePath)).append(":/host-app")
            append(" ").append(File(rootfs, "bin/sh").absolutePath)
            append(" -lc ").append(shellQuote(command))
        }
    }

    fun interactiveShell(): String = command(
        "export HOME=/home/agcodespace; export USER=agcodespace; " +
            "export PATH=\"/home/agcodespace/.local/bin:/usr/local/bin:/usr/bin:/bin\"; " +
            "if [ ! -f /var/lib/agcodespace/bootstrap.done ]; then /bin/bash /root/agcodespace-bootstrap.sh && mkdir -p /var/lib/agcodespace && touch /var/lib/agcodespace/bootstrap.done; fi; " +
            "cd \"\$HOME\"; exec \"\${SHELL:-/bin/bash}\" -l"
    )

    private fun installProotFromAsset() {
        val abi = "arm64-v8a"
        val asset = "runtime/$abi/proot"
        context.assets.open(asset).use { input ->
            proot.parentFile?.mkdirs()
            proot.outputStream().use { input.copyTo(it) }
        }
        check(proot.setExecutable(true, false)) { "Unable to mark PRoot executable" }
    }

    private fun downloadUbuntuBaseIfNeeded() {
        if (archive.isFile && archive.length() == UBUNTU_BASE_SIZE_BYTES && sha256(archive) == UBUNTU_BASE_SHA256) {
            return
        }
        archive.delete()
        val connection = (URL(UBUNTU_BASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AGCodespace/1.0")
        }
        try {
            check(connection.responseCode in 200..299) { "Ubuntu Base download failed: HTTP ${connection.responseCode}" }
            val expected = connection.getHeaderFieldLong("Content-Length", -1L)
            if (expected > 0) check(expected == UBUNTU_BASE_SIZE_BYTES) { "Unexpected Ubuntu Base size: $expected" }
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output -> input.copyTo(output, 256 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
        check(archive.length() == UBUNTU_BASE_SIZE_BYTES) { "Incomplete Ubuntu Base download" }
        check(sha256(archive) == UBUNTU_BASE_SHA256) { "Ubuntu Base SHA-256 verification failed" }
    }

    private fun extractRootfs() {
        if (rootfs.exists()) rootfs.deleteRecursively()
        rootfs.mkdirs()
        FileOutputStream(File(root, ".extracting")).use { }
        try {
            archive.inputStream().use { raw ->
                GZIPInputStream(raw, 256 * 1024).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            val out = safePath(rootfs, entry.name)
                            when {
                                entry.isDirectory -> out.mkdirs()
                                entry.isSymbolicLink -> {
                                    out.parentFile?.mkdirs()
                                    if (out.exists() || Files.isSymbolicLink(out.toPath())) Files.delete(out.toPath())
                                    Files.createSymbolicLink(out.toPath(), Paths.get(entry.linkName))
                                }
                                entry.isLink -> {
                                    out.parentFile?.mkdirs()
                                    val target = safePath(rootfs, entry.linkName)
                                    Files.deleteIfExists(out.toPath())
                                    Files.createLink(out.toPath(), target.toPath())
                                }
                                else -> {
                                    out.parentFile?.mkdirs()
                                    FileOutputStream(out).use { tar.copyTo(it, 256 * 1024) }
                                }
                            }
                            applyMode(out, entry.mode.toInt())
                            entry = tar.nextTarEntry
                        }
                    }
                }
            }
        } finally {
            File(root, ".extracting").delete()
        }
    }

    private fun configureGuest() {
        home.mkdirs()
        File(rootfs, "etc/resolv.conf").let { resolv ->
            runCatching {
                if (Files.isSymbolicLink(resolv.toPath()) || resolv.exists()) Files.deleteIfExists(resolv.toPath())
                resolv.parentFile?.mkdirs()
                resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            }
        }
        File(rootfs, "etc/hostname").writeText("agcodespace\n")
        File(rootfs, "home/agcodespace/.profile").apply {
            parentFile?.mkdirs()
            writeText(
                "export HOME=/home/agcodespace\n" +
                    "export PATH=\"/home/agcodespace/.local/bin:/usr/local/bin:/usr/bin:/bin\"\n" +
                    "export TERM=xterm-256color\n"
            )
        }
        // The Ubuntu Base archive is intentionally minimal. Dependencies such as OpenSSH, Git,
        // GitHub CLI and the Antigravity CLI are installed by the runtime bootstrap command.
        File(rootfs, "root/agcodespace-bootstrap.sh").apply {
            parentFile?.mkdirs()
            writeText(BOOTSTRAP_SCRIPT)
            setExecutable(true, false)
        }
    }

    private fun applyMode(file: File, mode: Int) {
        if (!file.exists()) return
        file.setReadable(mode and 0b100100100 != 0, false)
        file.setWritable(mode and 0b010010010 != 0, false)
        file.setExecutable(mode and 0b001001001 != 0, false)
    }

    private fun safePath(base: File, name: String): File {
        val normalized = name.removePrefix("/")
        val candidate = File(base, normalized).canonicalFile
        require(candidate.path == base.canonicalPath || candidate.path.startsWith(base.canonicalPath + File.separator)) {
            "Unsafe rootfs path: $name"
        }
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

}
