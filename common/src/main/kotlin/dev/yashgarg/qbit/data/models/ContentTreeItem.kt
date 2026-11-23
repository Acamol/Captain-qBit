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
    /**
     * On a folder the file search trimmed to its matches: the complete folder, which checkbox state
     * and bulk actions must use so they also cover the files the filter hides.
     */
    val complete: ContentTreeItem? = null,
) {
    /** This node as the torrent has it, ignoring any trimming by the file search. */
    fun whole(): ContentTreeItem = complete ?: this
}
