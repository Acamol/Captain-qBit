package dev.yashgarg.qbit.utils

import android.content.Context
import android.os.Build
import android.os.Debug
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
    private const val BYTES_PER_MB = 1024.0 * 1024

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
            appendLine("Memory: ${memorySummary()}")
            appendLine()
            append(redact(throwable.stackTraceToString()))
        }
        File(context.filesDir, FILE_NAME).writeText(report)
    }

    // Kept allocation-light: this runs mid-crash, often an OutOfMemoryError, and a failure here
    // must not cost the rest of the report. The values are sampled after the stack has unwound.
    internal fun memorySummary(nativeHeapBytes: () -> Long = Debug::getNativeHeapAllocatedSize) =
        runCatching {
            val runtime = Runtime.getRuntime()
            val used = toMb(runtime.totalMemory() - runtime.freeMemory())
            val max = toMb(runtime.maxMemory())
            val native = toMb(nativeHeapBytes())
            "heap $used/$max MB, native $native MB, ${Thread.activeCount()} threads"
        }
        .getOrDefault("unavailable")

    private fun toMb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / BYTES_PER_MB)

    // Client/network exceptions routinely embed the user's self-hosted server URL or LAN IP in
    // their message (e.g. Ktor's ResponseException, or the client's own baseUrl validation) —
    // redact it before the report is even saved, since it can end up pasted into a public GitHub
    // issue via GitHubIssueLink.
    internal fun redact(text: String): String =
        text
            .replace(Regex("""https?://[^\s"'()<>\[\]{},;]+"""), "[redacted-url]")
            .replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"""), "[redacted-ip]")
}
