package dev.yashgarg.qbit.utils

import android.os.Build
import dev.yashgarg.qbit.BuildConfig
import java.net.URLEncoder

/**
 * Builds a GitHub "new issue" URL pre-filled with device/app info, so bug reports arrive with the
 * basics already attached.
 */
object GitHubIssueLink {

    private const val NEW_ISSUE_URL = "https://github.com/Acamol/Captain-qBit/issues/new"
    private const val MAX_CRASH_REPORT_CHARS = 3000

    fun url(crashReport: String? = null): String {
        val body = buildString {
            appendLine("**App version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "**Android version:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            )
            appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
            if (crashReport != null) {
                appendLine()
                appendLine("**Crash report:**")
                appendLine("```")
                append(crashReport.take(MAX_CRASH_REPORT_CHARS))
                if (crashReport.length > MAX_CRASH_REPORT_CHARS)
                    appendLine("\n…(truncated, see full report via Copy)")
                appendLine("```")
            }
        }
        val encodedBody = URLEncoder.encode(body, "UTF-8").replace("+", "%20")
        return "$NEW_ISSUE_URL?body=$encodedBody"
    }
}
