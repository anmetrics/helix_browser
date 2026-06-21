package com.helix.browser.utils

/**
 * Classifies a download as potentially dangerous (executable / installer /
 * script) so the UI can warn the user before saving — matching the spirit of
 * Chrome's "This type of file can harm your device" prompt.
 *
 * Pure and side-effect free (no Android dependencies) so it is unit-testable in
 * a plain JVM test. A false positive only adds a dismissible warning, while a
 * miss means no warning at all, so the list is deliberately broad.
 */
object DownloadSafety {

    // Lower-case extensions (no leading dot) that can execute code or install
    // software when opened, across the platforms a downloaded file might target.
    private val DANGEROUS_EXTENSIONS: Set<String> = setOf(
        // Android / mobile packages
        "apk", "apks", "xapk", "aab",
        // Windows executables, installers & scripts
        "exe", "msi", "msix", "msp", "mst", "com", "scr", "pif", "bat", "cmd",
        "vbs", "vbe", "js", "jse", "ws", "wsf", "wsh", "ps1", "psm1", "reg",
        "hta", "cpl", "msc", "gadget", "inf", "ins", "isp", "lnk", "sct",
        "shb", "shs", "dll", "sys",
        // macOS / Apple
        "dmg", "pkg", "mpkg", "app", "command",
        // Linux / Unix
        "deb", "rpm", "run", "bin", "sh", "bash", "appimage", "elf",
        // Cross-platform executable container
        "jar"
    )

    // MIME types that denote an executable / installer regardless of extension.
    private val DANGEROUS_MIMES: Set<String> = setOf(
        "application/vnd.android.package-archive",
        "application/x-msdownload",
        "application/x-msdos-program",
        "application/x-executable",
        "application/x-elf",
        "application/x-sh",
        "application/x-shellscript",
        "application/x-bat",
        "application/x-msi",
        "application/x-apple-diskimage",
        "application/x-debian-package",
        "application/x-redhat-package-manager",
        "application/java-archive"
    )

    /**
     * True when [fileName] or [mimeType] names an executable / installer /
     * script that could harm the device if opened.
     */
    fun isDangerous(fileName: String?, mimeType: String? = null): Boolean {
        if (extensionOf(fileName) in DANGEROUS_EXTENSIONS) return true
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return mime.isNotEmpty() && mime in DANGEROUS_MIMES
    }

    /** Lower-case extension (no dot) of the final path segment, or "" when none. */
    private fun extensionOf(fileName: String?): String {
        if (fileName.isNullOrBlank()) return ""
        val name = fileName.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('.')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }
}
