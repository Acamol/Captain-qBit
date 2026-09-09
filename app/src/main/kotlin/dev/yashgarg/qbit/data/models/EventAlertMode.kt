package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * When torrent and RSS alerts are allowed to make a sound.
 *
 * One setting rather than a pair of switches, because "always silent" and a quiet-hours window are
 * mutually exclusive answers to the same question - as two toggles they could be set to contradict
 * each other.
 */
@Keep
@Serializable
enum class EventAlertMode {
    /** Alert normally, whatever the time. */
    ALWAYS,
    /** Never make a sound, whatever the time. */
    NEVER,
    /** Alert except inside the quiet-hours window. */
    OUTSIDE_QUIET_HOURS,
}
