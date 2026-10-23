package app.agcodespace

import app.agcodespace.runtime.UbuntuInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UbuntuInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsInstalledWhenEmpty() {
        val baseDir = tempFolder.newFolder("ubuntu")
        val installer = UbuntuInstaller(context = null, baseDir = baseDir)
        assertFalse(installer.isInstalled())
    }

    @Test
    fun testIsInstalledWithMissingComponents() {
        val baseDir = tempFolder.newFolder("ubuntu")
        val rootfs = File(baseDir, "rootfs").apply { mkdirs() }
        val installer = UbuntuInstaller(context = null, baseDir = baseDir)

        // Only marker
        File(rootfs, ".agcodespace-installed").writeText("installed")
        assertFalse(installer.isInstalled())

        // Marker + etc/os-release but no bin
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/os-release").writeText("ID=ubuntu")
        assertFalse(installer.isInstalled())

        // Marker + etc/os-release + bin
        File(rootfs, "bin").mkdirs()
        assertTrue(installer.isInstalled())
    }

    @Test
    fun testIsInstalledWithUsrLibOsReleaseAndUsrBin() {
        val baseDir = tempFolder.newFolder("ubuntu")
        val rootfs = File(baseDir, "rootfs").apply { mkdirs() }
        val installer = UbuntuInstaller(context = null, baseDir = baseDir)

        File(rootfs, ".agcodespace-installed").writeText("installed")
        File(rootfs, "usr/lib").mkdirs()
        File(rootfs, "usr/lib/os-release").writeText("ID=ubuntu")
        File(rootfs, "usr/bin").mkdirs()
        assertTrue(installer.isInstalled())
    }

    @Test
    fun testIsAssetBundledReturnsFalseWithNullContext() {
        val baseDir = tempFolder.newFolder("ubuntu")
        val installer = UbuntuInstaller(context = null, baseDir = baseDir)
        // No Android context → asset open always fails → returns false
        assertFalse(installer.isAssetBundled())
    }

    @Test
    fun testIsSafeSymlinkTargetValidPaths() {
        val staging = tempFolder.newFolder("staging")

        // Standard Ubuntu guest symlinks with absolute target
        val link1 = File(staging, "usr/bin/awk")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link1, "/etc/alternatives/awk"))

        val link2 = File(staging, "var/lock")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link2, "/run/lock"))

        // Relative target pointing within rootfs
        val link3 = File(staging, "etc/os-release")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link3, "../usr/lib/os-release"))

        val link4 = File(staging, "bin/sh")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link4, "dash"))

        val link5 = File(staging, "usr/bin/pager")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link5, "/etc/alternatives/pager"))

        // Ubuntu 24.04 LTS UsrMerge root symlinks
        val link6 = File(staging, "bin")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link6, "usr/bin"))

        val link7 = File(staging, "lib")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link7, "usr/lib"))

        val link8 = File(staging, "sbin")
        assertTrue(UbuntuInstaller.isSafeSymlinkTarget(staging, link8, "usr/sbin"))
    }

    @Test
    fun testIsSafeSymlinkTargetEscapingPaths() {
        val staging = tempFolder.newFolder("staging")

        // Traversal out of staging via relative path
        val link1 = File(staging, "usr/bin/malicious")
        assertFalse(UbuntuInstaller.isSafeSymlinkTarget(staging, link1, "../../../../../../system/bin/sh"))

        // Traversal out of staging via root path
        val link2 = File(staging, "usr/bin/malicious2")
        assertFalse(UbuntuInstaller.isSafeSymlinkTarget(staging, link2, "/../../../../etc/shadow"))

        // Traversal out of staging via multiple leading slashes
        val link3 = File(staging, "usr/bin/malicious3")
        assertFalse(UbuntuInstaller.isSafeSymlinkTarget(staging, link3, "///../../../../etc/shadow"))
    }

    @Test
    fun testIsSafeSymlinkTargetWithSymlinkedStagingDirectory() {
        val realDir = tempFolder.newFolder("real_data")
        val realStaging = File(realDir, "staging").apply { mkdirs() }
        val symlinkDir = File(tempFolder.root, "user_alias")
        java.nio.file.Files.createSymbolicLink(symlinkDir.toPath(), realDir.toPath())
        val aliasStaging = File(symlinkDir, "staging")

        // 1. Staging passed as symlinked alias, linkFile under real (canonical) staging
        val linkCanonical = File(realStaging, "bin")
        assertTrue(
            "Symlink target bin -> usr/bin should be safe when staging is an alias path",
            UbuntuInstaller.isSafeSymlinkTarget(aliasStaging, linkCanonical, "usr/bin")
        )

        // 2. Staging passed as canonical, linkFile under alias staging
        val linkAlias = File(aliasStaging, "bin")
        assertTrue(
            "Symlink target bin -> usr/bin should be safe when linkFile is an alias path",
            UbuntuInstaller.isSafeSymlinkTarget(realStaging, linkAlias, "usr/bin")
        )

        // 3. Both staging and linkFile passed as alias
        assertTrue(
            "Symlink target bin -> usr/bin should be safe when both are alias paths",
            UbuntuInstaller.isSafeSymlinkTarget(aliasStaging, linkAlias, "usr/bin")
        )

        // 4. Absolute symlink target under alias staging
        assertTrue(
            "Absolute symlink target should be safe under alias staging",
            UbuntuInstaller.isSafeSymlinkTarget(aliasStaging, linkCanonical, "/etc/alternatives/awk")
        )

        // 5. Malicious symlink target under alias staging should still be blocked
        assertFalse(
            "Escaping symlink target should be blocked even under alias staging",
            UbuntuInstaller.isSafeSymlinkTarget(aliasStaging, linkCanonical, "../../../../../../system/bin/sh")
        )
    }

    @Test
    fun testLtsConstants() {
        assertTrue(UbuntuInstaller.ARCHIVE_URL.contains("26.04.1"))
        assertTrue(UbuntuInstaller.ARCHIVE_URL.endsWith("arm64.tar.gz"))
        assertTrue(UbuntuInstaller.SUMS_URL.contains("resolute"))
        assertEquals("5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd", UbuntuInstaller.FALLBACK_SHA256)
        assertEquals("ubuntu-base-26.04.1-base-arm64.tar.gz", UbuntuInstaller.BUNDLED_ASSET_NAME)
    }

    @Test
    fun testUninstallCleansUp() {
        val baseDir = tempFolder.newFolder("ubuntu")
        val rootfs = File(baseDir, "rootfs").apply { mkdirs() }
        val marker = File(rootfs, ".agcodespace-installed").apply { writeText("done") }
        val archive = File(baseDir, "ubuntu-base-arm64.tar.gz.part").apply { writeText("archive") }
        val sums = File(baseDir, "SHA256SUMS").apply { writeText("sums") }

        val installer = UbuntuInstaller(context = null, baseDir = baseDir)
        installer.uninstall()

        assertFalse(rootfs.exists())
        assertFalse(marker.exists())
        assertFalse(archive.exists())
        assertFalse(sums.exists())
        assertFalse(installer.isInstalled())
    }
}
