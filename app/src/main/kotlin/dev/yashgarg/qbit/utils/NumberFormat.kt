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
        val context = AppContextHolder.localized
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
        val context = AppContextHolder.localized
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
        // Also when every larger unit was zero, so a torrent that has just started reads "0s"
        // rather than coming out blank.
        if (secs != 0L || timeStr.isEmpty()) {
            timeStr.append(" ").append(context.getString(CommonR.string.unit_seconds_suffix, secs))
        }

        return timeStr.toString().trim().isolateLtr()
    }
}

fun Long.toHumanReadable(): String = NumberFormat.bytesToHumanReadable(this)

fun Long.toTime(): String = NumberFormat.secondsToTime(this)

fun Long.toDate(zoneId: ZoneId? = null): String = NumberFormat.millisToDate(this, zoneId)
