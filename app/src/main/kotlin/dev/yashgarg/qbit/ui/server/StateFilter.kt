package dev.yashgarg.qbit.ui.server

import qbittorrent.models.Torrent

enum class StateFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    STOPPED("Stopped"),
    COMPLETED("Completed"),
    ERRORED("Errored"),
}

fun Torrent.matchesFilter(filter: StateFilter): Boolean =
    when (filter) {
        StateFilter.ALL -> true
        StateFilter.ACTIVE -> dlspeed > 0 || uploadSpeed > 0
        StateFilter.DOWNLOADING ->
            state in
                setOf(
                    Torrent.State.DOWNLOADING,
                    Torrent.State.FORCED_DL,
                    Torrent.State.META_DL,
                    Torrent.State.FORCED_META_DL,
                    Torrent.State.ALLOCATING,
                    Torrent.State.QUEUED_DL,
                    Torrent.State.STALLED_DL,
                )
        StateFilter.SEEDING ->
            state in
                setOf(
                    Torrent.State.UPLOADING,
                    Torrent.State.FORCED_UP,
                    Torrent.State.STALLED_UP,
                    Torrent.State.QUEUED_UP,
                )
        StateFilter.STOPPED ->
            state in
                setOf(
                    Torrent.State.PAUSED_DL,
                    Torrent.State.STOPPED_DL,
                    Torrent.State.PAUSED_UP,
                    Torrent.State.STOPPED_UP,
                )
        StateFilter.COMPLETED -> amountLeft == 0L
        StateFilter.ERRORED -> state in setOf(Torrent.State.ERROR, Torrent.State.MISSING_FILES)
    }
