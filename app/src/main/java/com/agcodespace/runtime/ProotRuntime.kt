package com.agcodespace.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

sealed interface RuntimeStatus {
    data object Idle : RuntimeStatus
    data class Checking(val message: String) : RuntimeStatus
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progress: Float) : RuntimeStatus
    data class Extracting(val currentFile: String, val count: Int, val progress: Float) : RuntimeStatus
    data object Configuring : RuntimeStatus
    data object Ready : RuntimeStatus
    data class Error(val message: String) : RuntimeStatus
}

/**
 * Owns the app-private Ubuntu/PRoot Linux guest.
 *
 * Runs a real Linux userspace (Ubuntu Base 26.04) via PRoot with fake-root (-0)
 * and Termux PTY emulation, without requiring device root privileges.
 */
class ProotRuntime(private val context: Context) {
    companion object {
        private const val TAG = "ProotRuntime"

        const val UBUNTU_BASE_ARM64_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
        const val UBUNTU_BASE_ARM64_SHA256 =
            "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
        const val UBUNTU_BASE_ARM64_SIZE_BYTES = 35_092_106L

        const val UBUNTU_BASE_X86_64_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-amd64.tar.gz"
        const val UBUNTU_BASE_X86_64_SHA256 =
            "a496a960472ce474a59590b8987d3a1135d3cbef1991f3b1abe8cacfea8bf85a"
        const val UBUNTU_BASE_X86_64_SIZE_BYTES = 34_931_253L

        // Backward compatibility constants
        const val UBUNTU_BASE_URL = UBUNTU_BASE_ARM64_URL
        const val UBUNTU_BASE_SHA256 = UBUNTU_BASE_ARM64_SHA256
        const val UBUNTU_BASE_SIZE_BYTES = UBUNTU_BASE_ARM64_SIZE_BYTES

        private val _status = MutableStateFlow<RuntimeStatus>(RuntimeStatus.Idle)
        val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

        private val isInstalling = AtomicBoolean(false)

        fun autoInstallIfNeeded(context: Context) {
            val runtime = ProotRuntime(context.applicationContext)
            if (runtime.isInstalled()) {
                _status.value = RuntimeStatus.Ready
                return
            }
            if (isInstalling.compareAndSet(false, true)) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        runtime.prepareInternal()
                    } finally {
                        isInstalling.set(false)
                    }
                }
            }
        }
    }

    val root = File(context.filesDir, "linux")
    val binDir = File(root, "bin")
    val libDir = File(root, "lib")
    val tmpDir = File(root, "tmp")
    val rootfs = File(root, "rootfs")
    val proot = File(binDir, "proot")
    val loader = File(binDir, "loader")
    val home = File(rootfs, "home/agcodespace")
    val launchScript = File(root, "launch.sh")
    private val ready = File(root, ".ready")

    fun getExecutableProot(): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        return if (nativeLib.isFile && nativeLib.canExecute()) nativeLib else proot
    }

    fun getExecutableLoader(): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so")
        return if (nativeLib.isFile && nativeLib.canExecute()) nativeLib else loader
    }

    fun getAbi(): String {
        val supported = Build.SUPPORTED_ABIS ?: emptyArray()
        for (abi in supported) {
            if (abi == "arm64-v8a" || abi == "x86_64") return abi
        }
        return if (supported.any { it.contains("64") }) "arm64-v8a" else "arm64-v8a"
    }

    private fun getRootfsUrl(abi: String): String {
        return runCatching {
            context.assets.open("runtime/$abi/ROOTFS.URL").bufferedReader().use { it.readText().trim() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (abi == "x86_64") UBUNTU_BASE_X86_64_URL else UBUNTU_BASE_ARM64_URL
    }

    private fun getRootfsSha256(abi: String): String {
        return runCatching {
            val text = context.assets.open("runtime/$abi/ROOTFS.SHA256").bufferedReader().use { it.readText().trim() }
            text.split("\\s+".toRegex()).firstOrNull()?.trim()
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: if (abi == "x86_64") UBUNTU_BASE_X86_64_SHA256 else UBUNTU_BASE_ARM64_SHA256
    }

    private fun getRootfsExpectedSize(abi: String): Long {
        return if (abi == "x86_64") UBUNTU_BASE_X86_64_SIZE_BYTES else UBUNTU_BASE_ARM64_SIZE_BYTES
    }

    fun isInstalled(): Boolean {
        val prootExec = getExecutableProot()
        return ready.isFile && prootExec.canExecute() && launchScript.canExecute() && File(rootfs, "bin/sh").canExecute() && home.isDirectory
    }

    fun prepare(): Boolean {
        if (isInstalled()) {
            _status.value = RuntimeStatus.Ready
            return true
        }
        return synchronized(isInstalling) {
            if (isInstalled()) {
                _status.value = RuntimeStatus.Ready
                return true
            }
            prepareInternal()
        }
    }

    private fun prepareInternal(): Boolean {
        val abi = getAbi()
        Log.i(TAG, "Preparing Linux runtime for ABI: $abi")
        _status.value = RuntimeStatus.Checking("Checking Linux environment ($abi)…")

        return try {
            root.mkdirs()
            binDir.mkdirs()
            libDir.mkdirs()
            tmpDir.mkdirs()

            installProotAndLibraries(abi)

            val archive = File(root, "ubuntu-base-$abi.tar.gz")
            downloadRootfs(abi, archive)

            extractRootfs(archive)

            _status.value = RuntimeStatus.Configuring
            configureGuest()
            createLauncherScript()

            ready.writeText("ubuntu-base-26.04.1-$abi\n${getRootfsSha256(abi)}\n")

            // Clean up archive to save flash storage
            archive.delete()

            val success = isInstalled()
            if (success) {
                Log.i(TAG, "Linux runtime installation completed successfully.")
                _status.value = RuntimeStatus.Ready
            } else {
                val err = "Installation completed but verification check failed."
                Log.e(TAG, err)
                _status.value = RuntimeStatus.Error(err)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Linux runtime", e)
            _status.value = RuntimeStatus.Error(e.message ?: "Unknown installation error")
            false
        }
    }

    private fun installProotAndLibraries(abi: String) {
        val assetDir = "runtime/$abi"
        val assets = runCatching { context.assets.list(assetDir) ?: emptyArray() }.getOrDefault(emptyArray())

        for (asset in assets) {
            val isExecutable = asset == "proot" || asset == "loader" || asset.endsWith(".so") || asset.contains(".so.")
            val targetFile = if (asset == "proot" || asset == "loader") File(binDir, asset) else File(libDir, asset)

            if (!targetFile.exists() || targetFile.length() == 0L) {
                context.assets.open("$assetDir/$asset").use { input ->
                    targetFile.parentFile?.mkdirs()
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (isExecutable) {
                targetFile.setReadable(true, false)
                targetFile.setExecutable(true, false)
            }
            // Also mirror libraries and loader in binDir so PRoot unbundled loader works
            if (asset.endsWith(".so") || asset.contains(".so.") || asset == "loader") {
                val binCopy = File(binDir, asset)
                if (!binCopy.exists()) {
                    runCatching {
                        Files.copy(targetFile.toPath(), binCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        binCopy.setReadable(true, false)
                        binCopy.setExecutable(true, false)
                    }
                }
            }
        }

        val prootExec = getExecutableProot()
        check(prootExec.isFile && prootExec.canExecute()) {
            "LinuxDroid PRoot executable could not be verified for ABI $abi"
        }
    }

    private fun downloadRootfs(abi: String, archive: File) {
        val expectedSha = getRootfsSha256(abi)
        val expectedSize = getRootfsExpectedSize(abi)
        val downloadUrl = getRootfsUrl(abi)

        if (archive.isFile && archive.length() > 0L) {
            _status.value = RuntimeStatus.Checking("Verifying cached rootfs archive…")
            if (sha256(archive).equals(expectedSha, ignoreCase = true)) {
                Log.i(TAG, "Cached rootfs archive is valid.")
                return
            }
            archive.delete()
        }

        Log.i(TAG, "Downloading rootfs from $downloadUrl")
        val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AGCodespace/1.0 (Android; Linux)")
        }

        try {
            val code = conn.responseCode
            check(code in 200..299) { "HTTP download failed with status code $code" }

            val totalBytes = conn.getHeaderFieldLong("Content-Length", expectedSize).let {
                if (it > 0) it else expectedSize
            }

            _status.value = RuntimeStatus.Downloading(0L, totalBytes, 0f)

            val partFile = File(archive.parentFile, "${archive.name}.part")
            partFile.delete()

            var bytesRead = 0L
            val buffer = ByteArray(64 * 1024)
            var lastUpdate = System.currentTimeMillis()

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        bytesRead += n

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || bytesRead == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                            _status.value = RuntimeStatus.Downloading(bytesRead, totalBytes, progress)
                        }
                    }
                    output.flush()
                }
            }

            partFile.renameTo(archive)
        } finally {
            conn.disconnect()
        }

        _status.value = RuntimeStatus.Checking("Verifying download checksum…")
        val actualSha = sha256(archive)
        check(actualSha.equals(expectedSha, ignoreCase = true)) {
            archive.delete()
            "Rootfs checksum mismatch. Expected $expectedSha but got $actualSha"
        }
    }

    private fun extractRootfs(archive: File) {
        Log.i(TAG, "Extracting rootfs from ${archive.name}…")
        if (rootfs.exists()) {
            rootfs.deleteRecursively()
        }
        rootfs.mkdirs()

        val extractingMarker = File(root, ".extracting")
        extractingMarker.createNewFile()

        try {
            var entryCount = 0
            var lastReport = System.currentTimeMillis()

            archive.inputStream().use { raw ->
                GZIPInputStream(raw, 128 * 1024).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            entryCount++
                            val out = safePath(rootfs, entry.name)

                            when {
                                entry.isDirectory -> {
                                    out.mkdirs()
                                }
                                entry.isSymbolicLink -> {
                                    out.parentFile?.mkdirs()
                                    runCatching {
                                        if (out.exists() || Files.isSymbolicLink(out.toPath())) {
                                            Files.delete(out.toPath())
                                        }
                                        Files.createSymbolicLink(out.toPath(), Paths.get(entry.linkName))
                                    }
                                }
                                entry.isLink -> {
                                    out.parentFile?.mkdirs()
                                    val target = safePath(rootfs, entry.linkName)
                                    runCatching {
                                        Files.deleteIfExists(out.toPath())
                                        Files.createLink(out.toPath(), target.toPath())
                                    }.onFailure {
                                        // SELinux policy on Android blocks link() in app data; fall back to symlink or copy
                                        runCatching {
                                            Files.createSymbolicLink(out.toPath(), target.toPath())
                                        }.onFailure {
                                            if (target.exists()) {
                                                Files.copy(target.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    out.parentFile?.mkdirs()
                                    FileOutputStream(out).use { fos ->
                                        tar.copyTo(fos, 64 * 1024)
                                    }
                                }
                            }

                            applyMode(out, entry.mode.toInt())

                            val now = System.currentTimeMillis()
                            if (now - lastReport > 150) {
                                lastReport = now
                                _status.value = RuntimeStatus.Extracting(entry.name, entryCount, (entryCount % 1000) / 1000f)
                            }

                            entry = tar.nextEntry
                        }
                    }
                }
            }
        } finally {
            extractingMarker.delete()
        }
    }

    private fun configureGuest() {
        home.mkdirs()
        File(rootfs, "tmp").mkdirs()
        tmpDir.mkdirs()

        // 1. DNS Resolution
        val resolvContent = "nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 1.0.0.1\n"
        File(root, "resolv.conf").writeText(resolvContent)
        File(rootfs, "etc/resolv.conf").let { resolv ->
            runCatching {
                if (Files.isSymbolicLink(resolv.toPath()) || resolv.exists()) {
                    Files.deleteIfExists(resolv.toPath())
                }
                resolv.parentFile?.mkdirs()
                resolv.writeText(resolvContent)
            }
        }

        // 2. Hosts & Hostname
        val hostsContent = "127.0.0.1 localhost\n127.0.0.1 agcodespace\n::1 localhost ip6-localhost ip6-loopback\n"
        File(root, "hosts").writeText(hostsContent)
        File(rootfs, "etc/hosts").let { hosts ->
            runCatching {
                if (Files.isSymbolicLink(hosts.toPath()) || hosts.exists()) {
                    Files.deleteIfExists(hosts.toPath())
                }
                hosts.parentFile?.mkdirs()
                hosts.writeText(hostsContent)
            }
        }
        File(rootfs, "etc/hostname").writeText("agcodespace\n")

        // 3. User configuration in /etc/passwd & /etc/group
        val passwdFile = File(rootfs, "etc/passwd")
        val currentPasswd = if (passwdFile.exists()) passwdFile.readText() else ""
        if (!currentPasswd.contains("agcodespace:")) {
            passwdFile.appendText("agcodespace:x:1000:1000:AGCodespace User:/home/agcodespace:/bin/bash\n")
        }
        if (!currentPasswd.contains("root:")) {
            passwdFile.writeText("root:x:0:0:root:/root:/bin/bash\n" + passwdFile.readText())
        }

        val groupFile = File(rootfs, "etc/group")
        val currentGroup = if (groupFile.exists()) groupFile.readText() else ""
        if (!currentGroup.contains("agcodespace:")) {
            groupFile.appendText("agcodespace:x:1000:\n")
        }

        // 4. Colorful bash shell configuration
        File(rootfs, "home/agcodespace/.bashrc").apply {
            parentFile?.mkdirs()
            writeText(
                """
export PS1='\[\033[01;32m\]agcodespace@android\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
export TERM=xterm-256color
export COLORTERM=truecolor
export HOME=/home/agcodespace
export USER=agcodespace
export PATH=/home/agcodespace/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
alias ll='ls -la --color=auto'
alias ls='ls --color=auto'
alias grep='grep --color=auto'
cd /home/agcodespace
""".trimIndent() + "\n"
            )
        }

        File(rootfs, "home/agcodespace/.profile").apply {
            parentFile?.mkdirs()
            writeText(
                """
if [ -n "${'$'}BASH_VERSION" ]; then
    if [ -f "${'$'}HOME/.bashrc" ]; then
        . "${'$'}HOME/.bashrc"
    fi
fi
""".trimIndent() + "\n"
            )
        }

        // 5. Bootstrap script
        File(rootfs, "root/agcodespace-bootstrap.sh").apply {
            parentFile?.mkdirs()
            writeText(
                """
#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive
export PATH=/home/agcodespace/.local/bin:/usr/local/bin:/usr/bin:/bin
mkdir -p /home/agcodespace/.local/bin
if [ ! -x /usr/bin/ssh ]; then
  apt-get update || true
  apt-get install -y --no-install-recommends ca-certificates curl git openssh-client bash coreutils tar xz-utils gzip procps iproute2 || true
fi
if ! command -v gh >/dev/null 2>&1; then
  apt-get install -y --no-install-recommends gh || true
fi
if [ ! -x /home/agcodespace/.local/bin/agy ]; then
  curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --skip-aliases --skip-path || true
fi
""".trimIndent() + "\n"
            )
            setExecutable(true, false)
        }
    }

    private fun createLauncherScript() {
        val resolvFile = File(root, "resolv.conf").absolutePath
        val hostsFile = File(root, "hosts").absolutePath
        val hostAppDir = context.filesDir.absolutePath
        val prootExec = getExecutableProot().absolutePath
        val loaderExec = getExecutableLoader().absolutePath

        val script = """
#!/system/bin/sh
export LD_LIBRARY_PATH="${libDir.absolutePath}:${binDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
export PROOT_TMP_DIR="${tmpDir.absolutePath}"
export PROOT_LOADER="$loaderExec"
export PROOT_UNBUNDLE_LOADER="${binDir.absolutePath}"
export HOME=/home/agcodespace
export USER=agcodespace
export TERM=xterm-256color
export COLORTERM=truecolor
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

cd "${root.absolutePath}"

SHELL_CMD="/bin/bash"
if [ ! -x "${rootfs.absolutePath}/bin/bash" ]; then
  SHELL_CMD="/bin/sh"
fi

PROOT_BIN="$prootExec"

if [ ${'$'}# -gt 0 ]; then
  exec "${'$'}PROOT_BIN" \
    -0 \
    --link2symlink \
    -r "${rootfs.absolutePath}" \
    -b /dev \
    -b /proc \
    -b /sys \
    -b /dev/urandom:/dev/random \
    -b "$resolvFile":/etc/resolv.conf \
    -b "$hostsFile":/etc/hosts \
    -b "$hostAppDir":/host-app \
    -w /home/agcodespace \
    "${'$'}@"
else
  exec "${'$'}PROOT_BIN" \
    -0 \
    --link2symlink \
    -r "${rootfs.absolutePath}" \
    -b /dev \
    -b /proc \
    -b /sys \
    -b /dev/urandom:/dev/random \
    -b "$resolvFile":/etc/resolv.conf \
    -b "$hostsFile":/etc/hosts \
    -b "$hostAppDir":/host-app \
    -w /home/agcodespace \
    "${'$'}SHELL_CMD" -l
fi
""".trimIndent() + "\n"

        launchScript.writeText(script)
        launchScript.setReadable(true, false)
        launchScript.setExecutable(true, false)
    }

    /** Host-side command that enters the Ubuntu guest through the packaged PRoot binary. */
    fun command(cmd: String): String {
        check(isInstalled()) { "Linux userspace is not installed." }
        return "${launchScript.absolutePath} /bin/bash -lc ${shellQuote(cmd)}"
    }

    fun interactiveShell(): String {
        return launchScript.absolutePath
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
