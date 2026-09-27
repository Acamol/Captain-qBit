package qbittorrent.models

/**
 * Which days the alternate-speed-limit schedule applies on.
 *
 * The [value]s are qBittorrent's own `Scheduler::Days` enum, sent as the `scheduler_days`
 * preference. They are a plain ordinal, not a bitmask - a schedule runs either every day, on
 * weekdays, on weekends, or on one specific day.
 */
enum class SchedulerDays(val value: Int) {
    EVERY_DAY(0),
    WEEKDAY(1),
    WEEKEND(2),
    MONDAY(3),
    TUESDAY(4),
    WEDNESDAY(5),
    THURSDAY(6),
    FRIDAY(7),
    SATURDAY(8),
    SUNDAY(9);

    companion object {
        /** Falls back to [EVERY_DAY] for a value this build doesn't know. */
        fun fromValue(value: Int): SchedulerDays =
            entries.firstOrNull { it.value == value } ?: EVERY_DAY
    }
}
