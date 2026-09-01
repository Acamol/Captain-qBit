package dev.yashgarg.qbit.utils

import dev.yashgarg.qbit.common.R as CommonR
import java.lang.StringBuilder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private object NumberFormat {
    // These values are always LTR content (digits + Latin unit abbreviations). Isolating them
    // with Unicode directional isolate marks stops the bidi algorithm from reordering them
    // unpredictably when they're embedded in a larger RTL sentence (e.g. via a %s placeholder
    // in a translated string template) - equivalent to android.text.BidiFormatter.unicodeWrap(),
    // done in plain Kotlin so it works in JVM unit tests without Robolectric.
    private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
    private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

    private fun String.isolateLtr(): String = "$LEFT_TO_RIGHT_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"

    // Indexed by unitIndex below: 0=Ki, 1=Mi, 2=Gi, 3=Ti, 4=Pi, 5=Ei.
    private val BYTE_UNIT_RES_IDS =
        intArrayOf(
            CommonR.string.unit_kibibytes,
            CommonR.string.unit_mebibytes,
            CommonR.string.unit_gibibytes,
            CommonR.string.unit_tebibytes,
            CommonR.string.unit_pebibytes,
            CommonR.string.unit_exbibytes,
        )

    fun bytesToHumanReadable(bytes: Long): String {
        val context = AppContextHolder.context
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
        if (absB < 1024) {
            return "$bytes ${context.getString(CommonR.string.unit_bytes)}".isolateLtr()
        }
        var value = absB
        var unitIndex = 0
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            unitIndex++
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        val unit = context.getString(BYTE_UNIT_RES_IDS[unitIndex])
        // Locale.ROOT keeps the decimal separator a fixed "." regardless of device locale - the
        // isolateLtr() below only fixes character order, not which character renders as the
        // separator, so a locale-dependent format would still show "1,50" in e.g. German.
        return String.format(Locale.ROOT, "%.2f %s", value / 1024.0, unit).trim().isolateLtr()
    }

    fun millisToDate(millis: Long, zoneId: ZoneId?): String {
        val millisEpoch = millis * 1000
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")
        val instant = Instant.ofEpochMilli(millisEpoch)
        val date = LocalDateTime.ofInstant(instant, zoneId ?: ZoneId.systemDefault())
        return formatter.format(date).trim().isolateLtr()
    }

    fun secondsToTime(seconds: Long): String {
        val context = AppContextHolder.context
        var duration = seconds
        val days: Long = TimeUnit.SECONDS.toDays(duration)
        duration -= TimeUnit.DAYS.toSeconds(days)
        val hours: Long = TimeUnit.SECONDS.toHours(duration)
        duration -= TimeUnit.HOURS.toSeconds(hours)
        val minutes: Long = TimeUnit.SECONDS.toMinutes(duration)
        duration -= TimeUnit.MINUTES.toSeconds(minutes)
        val secs: Long = TimeUnit.SECONDS.toSeconds(duration)
        // Each unit's count is a format argument of its own string rather than being concatenated
        // on, so a translation can place it, space it, or leave it out - Hebrew's dual form
        // ("יומיים" = "two days") carries the count in the word itself and must not be prefixed
        // with a numeral.
        val timeStr = StringBuilder()
        if (days != 0L) {
            timeStr.append(
                context.resources.getQuantityString(
                    CommonR.plurals.unit_days_suffix,
                    days.toInt(),
                    days,
                )
            )
        }
        if (hours != 0L) {
            timeStr.append(" ").append(context.getString(CommonR.string.unit_hours_suffix, hours))
        }
        if (minutes != 0L) {
            timeStr
                .append(" ")
                .append(context.getString(CommonR.string.unit_minutes_suffix, minutes))
        }
        if (secs != 0L) {
            timeStr.append(" ").append(context.getString(CommonR.string.unit_seconds_suffix, secs))
        }

        return timeStr.toString().trim().isolateLtr()
    }
}

fun Long.toHumanReadable(): String = NumberFormat.bytesToHumanReadable(this)

fun Long.toTime(): String = NumberFormat.secondsToTime(this)

fun Long.toDate(zoneId: ZoneId? = null): String = NumberFormat.millisToDate(this, zoneId)
