package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import qbittorrent.models.TorrentFile

@Keep
data class ContentTreeItem(
    val id: Int,
    val name: String,
    // Full path relative to the torrent root (e.g. "movies/action/film.mkv").
    val path: String,
    val item: TorrentFile? = null,
    val children: List<ContentTreeItem>? = null,
    val size: Long,
    val progress: Long,
)
