package qbittorrent.models

/**
 * The server's alternate-speed-limit schedule: when the alternate limits take over from the global
 * ones, and on which days.
 *
 * Times are minutes from midnight rather than qBittorrent's separate hour/minute preference pairs,
 * matching how the app carries its other times of day.
 */
data class AltSpeedSchedule(
    val enabled: Boolean,
    val fromMinutesOfDay: Int,
    val toMinutesOfDay: Int,
    val days: SchedulerDays,
)
