package dev.yashgarg.qbit.utils

import dev.yashgarg.qbit.data.models.EventAlertMode
import java.util.Calendar

/** Minutes since midnight, as stored in preferences and shown by the time pickers. */
const val MINUTES_PER_DAY = 24 * 60

/**
 * Whether [nowMinutes] falls inside the quiet window [startMinutes] until [endMinutes], all as
 * minutes since midnight.
 *
 * The window is half-open - it includes its start and excludes its end - so a window ending at
 * 07:00 is over at 07:00 rather than lasting a minute longer. It may wrap past midnight, which is
 * the usual case: 22:00 to 07:00 is the evening plus the following morning, not the daytime between
 * them. Equal start and end is an empty window, not a whole day, because that is what someone who
 * set both to the same time by accident would expect.
 */
fun isWithinQuietHours(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean =
    when {
        startMinutes == endMinutes -> false
        startMinutes < endMinutes -> nowMinutes >= startMinutes && nowMinutes < endMinutes
        else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
    }

/** Local wall-clock time as minutes since midnight. */
fun nowMinutesOfDay(calendar: Calendar = Calendar.getInstance()): Int =
    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

/**
 * Whether an event alert should be posted silently, given the user's [mode] and the current time.
 *
 * Silent means the alert still appears, just on a channel that makes no sound - it is never
 * dropped, so something that happened overnight is still waiting in the morning.
 */
fun shouldSilenceEventAlert(
    mode: EventAlertMode,
    nowMinutes: Int,
    quietStartMinutes: Int,
    quietEndMinutes: Int,
): Boolean =
    when (mode) {
        EventAlertMode.ALWAYS -> false
        EventAlertMode.NEVER -> true
        EventAlertMode.OUTSIDE_QUIET_HOURS ->
            isWithinQuietHours(nowMinutes, quietStartMinutes, quietEndMinutes)
    }
