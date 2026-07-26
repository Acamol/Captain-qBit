package dev.yashgarg.qbit.utils

import android.content.Context
import android.os.Build
import dev.yashgarg.qbit.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves uncaught exceptions to a private file on-device so the next launch can offer the user a
 * report to review — nothing is ever sent automatically, and no network call is made.
 */
object CrashHandler {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Reads and deletes the pending crash report, if any, so it's surfaced only once. */
    fun consumePendingReport(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.also { file.delete() }
    }

    private fun writeReport(context: Context, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("Captain qBit ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: $timestamp")
            appendLine()
            append(redact(throwable.stackTraceToString()))
        }
        File(context.filesDir, FILE_NAME).writeText(report)
    }

    // Client/network exceptions routinely embed the user's self-hosted server URL or LAN IP in
    // their message (e.g. Ktor's ResponseException, or the client's own baseUrl validation) —
    // redact it before the report is even saved, since it can end up pasted into a public GitHub
    // issue via GitHubIssueLink.
    internal fun redact(text: String): String =
        text
            .replace(Regex("""https?://[^\s"'()<>\[\]{},;]+"""), "[redacted-url]")
            .replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"""), "[redacted-ip]")
}
