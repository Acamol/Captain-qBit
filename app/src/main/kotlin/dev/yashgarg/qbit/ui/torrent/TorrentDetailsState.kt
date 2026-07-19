package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.data.models.ContentTreeItem
import qbittorrent.models.*

data class TorrentDetailsState(
    val loading: Boolean = true,
    val peersLoading: Boolean = true,
    val peers: TorrentPeers? = null,
    val torrent: Torrent? = null,
    val contentTree: List<ContentTreeItem> = emptyList(),
    val contentLoading: Boolean = true,
    val trackers: List<TorrentTracker> = emptyList(),
    val torrentProperties: TorrentProperties? = null,
    val error: Exception? = null,
    /** Human-readable reason when [torrent] is in an error/missing-files state, if known. */
    val errorReason: String? = null,
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    /** Whether the server has torrent queueing enabled (gates the queue-priority actions). */
    val queueingEnabled: Boolean = false,
)
