package dev.yashgarg.qbit.ui.server

import dev.yashgarg.qbit.common.R
import qbittorrent.models.Torrent

internal fun Torrent.State.labelRes(): Int =
    when (this) {
        Torrent.State.PAUSED_DL,
        Torrent.State.STOPPED_DL,
        Torrent.State.PAUSED_UP,
        Torrent.State.STOPPED_UP -> R.string.stopped
        Torrent.State.UPLOADING,
        Torrent.State.FORCED_UP,
        Torrent.State.STALLED_UP -> R.string.seeding
        Torrent.State.DOWNLOADING,
        Torrent.State.FORCED_DL -> R.string.filter_downloading
        Torrent.State.STALLED_DL -> R.string.stalled
        Torrent.State.ERROR -> R.string.state_errored
        Torrent.State.MISSING_FILES -> R.string.state_missing_files
        Torrent.State.META_DL,
        Torrent.State.FORCED_META_DL -> R.string.state_downloading_metadata
        Torrent.State.ALLOCATING -> R.string.state_allocating
        Torrent.State.MOVING -> R.string.state_moving
        Torrent.State.CHECKING_DL,
        Torrent.State.CHECKING_UP -> R.string.state_checking
        Torrent.State.CHECKING_RESUME_DATA -> R.string.state_checking_resume_data
        Torrent.State.QUEUED_DL,
        Torrent.State.QUEUED_UP -> R.string.state_queued
        Torrent.State.UNKNOWN -> R.string.state_unknown
    }
