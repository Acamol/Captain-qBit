package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep

@Keep
enum class TrackerStatus {
    DISABLED,
    UPDATING,
    NOT_CONTACTED,
    CONTACTED_WORKING,
    CONTACTED_NOT_WORKING;

    companion object {
        fun statusOf(status: Int): TrackerStatus {
            return when (status) {
                0 -> DISABLED
                1 -> NOT_CONTACTED
                2 -> CONTACTED_WORKING
                3 -> UPDATING
                4 -> CONTACTED_NOT_WORKING
                // Never crash on an unexpected/out-of-spec status from the server — treat it as a
                // neutral "disabled" entry (this is what qBittorrent uses for special rows too).
                else -> DISABLED
            }
        }
    }
}
