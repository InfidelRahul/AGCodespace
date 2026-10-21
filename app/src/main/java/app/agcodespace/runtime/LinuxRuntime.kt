package app.agcodespace.runtime

import android.content.Context
import java.io.File

class LinuxRuntime(context: Context) {
    private val app = context.applicationContext
    val installer = UbuntuInstaller(app)
    private val nativeDir = File(app.applicationInfo.nativeLibraryDir)
    fun runtimeReady(): Boolean = File(nativeDir, "libproot.so").canExecute() && File(nativeDir, "libproot_loader.so").isFile && File(nativeDir, "libtalloc.so").isFile && File(nativeDir, "libandroid-shmem.so").isFile
    fun launchArgv(): Array<String> {
        check(installer.isInstalled()) { "Install Ubuntu before starting a Linux shell." }
        check(runtimeReady()) { "The PRoot runtime is missing. Install/build the app through the GitHub Actions workflow so the pinned LinuxDroidapp/proot native runtime is packaged." }
        val root = installer.rootfs.absolutePath
        val args = mutableListOf(
            File(nativeDir, "libproot.so").absolutePath,
            "--link2symlink", "-0", "-r", root,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/system", "-b", "/apex", "-b", "/vendor", "-b", "/product",
            "-w", "/root", "/bin/bash", "--login"
        )
        return args.toTypedArray()
    }
    fun configureEnvironment(pty: NativePty) {
        pty.setEnv("PROOT_LOADER", File(nativeDir, "libproot_loader.so").absolutePath)
        pty.setEnv("PROOT_TMP_DIR", File(app.cacheDir, "proot-tmp").apply { mkdirs() }.absolutePath)
        pty.setEnv("LD_LIBRARY_PATH", nativeDir.absolutePath)
        pty.setEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=0")
        pty.setEnv("PROOT_NO_SECCOMP", "1")
        pty.setEnv("LANG", "C.UTF-8")
        pty.setEnv("TERM", "xterm-256color")
    }
}
