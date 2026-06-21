package com.helix.browser.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// DownloadSafety is pure Kotlin (no Android framework calls), so it needs no
// Robolectric shadows — a plain JVM unit test exercises it directly.
class DownloadSafetyTest {

    // --- dangerous by extension ---

    @Test fun `apk is dangerous`() = assertTrue(DownloadSafety.isDangerous("app-release.apk"))
    @Test fun `exe is dangerous`() = assertTrue(DownloadSafety.isDangerous("setup.exe"))
    @Test fun `msi installer is dangerous`() = assertTrue(DownloadSafety.isDangerous("Installer.MSI"))
    @Test fun `shell script is dangerous`() = assertTrue(DownloadSafety.isDangerous("install.sh"))
    @Test fun `dmg is dangerous`() = assertTrue(DownloadSafety.isDangerous("Helix.dmg"))
    @Test fun `deb package is dangerous`() = assertTrue(DownloadSafety.isDangerous("helix-browser_3.0.0_amd64.deb"))
    @Test fun `jar is dangerous`() = assertTrue(DownloadSafety.isDangerous("payload.jar"))

    // --- case-insensitivity & path handling ---

    @Test fun `extension match is case insensitive`() =
        assertTrue(DownloadSafety.isDangerous("Game.EXE"))
    @Test fun `only the final path segment counts`() =
        assertTrue(DownloadSafety.isDangerous("downloads/2026/trojan.apk"))
    @Test fun `query string after name is ignored`() =
        assertTrue(DownloadSafety.isDangerous("update.apk?token=abc#frag"))

    // --- safe by extension ---

    @Test fun `pdf is safe`() = assertFalse(DownloadSafety.isDangerous("report.pdf"))
    @Test fun `image is safe`() = assertFalse(DownloadSafety.isDangerous("photo.jpg"))
    @Test fun `zip archive is safe`() = assertFalse(DownloadSafety.isDangerous("photos.zip"))
    @Test fun `no extension is safe`() = assertFalse(DownloadSafety.isDangerous("README"))
    @Test fun `trailing dot is safe`() = assertFalse(DownloadSafety.isDangerous("weird."))
    @Test fun `null name is safe`() = assertFalse(DownloadSafety.isDangerous(null))
    @Test fun `blank name is safe`() = assertFalse(DownloadSafety.isDangerous("   "))

    // --- dangerous by MIME even when the name looks benign ---

    @Test fun `android package mime is dangerous`() =
        assertTrue(DownloadSafety.isDangerous("file", "application/vnd.android.package-archive"))
    @Test fun `windows executable mime is dangerous`() =
        assertTrue(DownloadSafety.isDangerous("download", "application/x-msdownload"))
    @Test fun `mime with charset suffix still matches`() =
        assertTrue(DownloadSafety.isDangerous("x", "application/x-sh; charset=utf-8"))
    @Test fun `benign mime is safe`() =
        assertFalse(DownloadSafety.isDangerous("doc", "application/pdf"))
}
