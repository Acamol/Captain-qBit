package dev.yashgarg.qbit.utils

import dev.yashgarg.qbit.common.R as CommonR
import java.lang.StringBuilder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        val context = AppContextHolder.context
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
        if (absB < 1024) {
            return "$bytes ${context.getString(CommonR.string.unit_bytes)}"
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
        return String.format("%.2f %s", value / 1024.0, unit).trim()
    }

    fun millisToDate(millis: Long, zoneId: ZoneId?): String {
        val millisEpoch = millis * 1000
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")
        val instant = Instant.ofEpochMilli(millisEpoch)
        val date = LocalDateTime.ofInstant(instant, zoneId ?: ZoneId.systemDefault())
        return formatter.format(date).trim()
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
        val timeStr = StringBuilder()
        if (days != 0L) {
            val daysSuffix =
                context.resources.getQuantityString(CommonR.plurals.unit_days_suffix, days.toInt())
            timeStr.append("${days}${daysSuffix}")
        }
        if (hours != 0L) {
            timeStr.append(" ${hours}${context.getString(CommonR.string.unit_hours_suffix)}")
        }
        if (minutes != 0L) {
            timeStr.append(" ${minutes}${context.getString(CommonR.string.unit_minutes_suffix)}")
        }
        if (secs != 0L) {
            timeStr.append(" ${secs}${context.getString(CommonR.string.unit_seconds_suffix)}")
        }

        return timeStr.toString().trim()
    }
}

fun Long.toHumanReadable(): String = NumberFormat.bytesToHumanReadable(this)

fun Long.toTime(): String = NumberFormat.secondsToTime(this)

fun Long.toDate(zoneId: ZoneId? = null): String = NumberFormat.millisToDate(this, zoneId)
