package com.helix.browser

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Catch-all UncaughtExceptionHandler that writes a crash report to internal
 * storage before the process is killed. The report is plain text and lives in
 * `filesDir/crashlogs/` so that:
 *
 *  - users can attach it to a support email,
 *  - the next launch can detect a prior crash and offer to send it,
 *  - QA can pull it via `adb shell run-as`.
 *
 * This is a safety net, not a replacement for a real crash reporter
 * (Crashlytics, Sentry). It avoids any third-party dependency.
 */
class HelixCrashHandler private constructor(
    private val appContext: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeReport(thread, throwable)
        } catch (t: Throwable) {
            // Don't crash inside the crash handler.
            Log.e(TAG, "Failed to persist crash report", t)
        }
        // Hand off so the platform / debugger still gets the original signal
        // (Logcat, ANR dialog, etc.). If no previous handler, terminate cleanly.
        val prior = previousHandler
        if (prior != null && prior !== this) {
            prior.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        val dir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
        // Rotate: keep at most MAX_LOGS most recent crash files.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS - 1)
            ?.forEach { runCatching { it.delete() } }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        file.writeText(buildString {
            append("Helix Browser crash report\n")
            append("Time: ").append(stamp).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ")
                .append(Build.VERSION.SDK_INT).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("App: ").append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(")\n")
            append("---\n")
            append(sw.toString())
        })
    }

    companion object {
        private const val TAG = "HelixCrashHandler"
        private const val DIR_NAME = "crashlogs"
        private const val MAX_LOGS = 5

        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            // Avoid double-installing if the Application is re-created.
            if (current is HelixCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                HelixCrashHandler(context.applicationContext, current)
            )
        }

        fun reportDir(context: Context): File =
            File(context.filesDir, DIR_NAME)
    }
}
