package dev.yashgarg.qbit.ui.rss

import qbittorrent.models.RssArticle
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule

data class RssState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<RssItem> = emptyList(),
    val sortDescending: Boolean = false,
    val rules: Map<String, RssRule> = emptyMap(),
    val refreshIntervalMinutes: Int = 30,
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    /**
     * Hashes of torrents already added to the active server, lowercased - compare against
     * [RssArticle.magnetHash].
     */
    val existingTorrentHashes: Set<String> = emptySet(),
)

private val MAGNET_HASH_REGEX = Regex("urn:btih:([A-Fa-f0-9]{40})")

/**
 * The torrent info-hash embedded in this article's magnet link, lowercased - null if
 * [RssArticle.torrentURL] isn't a magnet URI, or its hash isn't in the (far more common) 40-char
 * hex form this can compare directly against [qbittorrent.models.Torrent.hash].
 */
fun RssArticle.magnetHash(): String? = torrentURL?.let {
    MAGNET_HASH_REGEX.find(it)?.groupValues?.get(1)?.lowercase()
}

/** Every feed in the tree, depth-first - used by the rule editor's "affected feeds" picker. */
fun List<RssItem>.flattenFeeds(): List<RssFeed> = flatMap { item ->
    when (item) {
        is RssFeed -> listOf(item)
        is RssFolder -> item.children.flattenFeeds()
    }
}

/** Every folder path in the tree, depth-first - used by the "move to folder" picker. */
fun List<RssItem>.flattenFolderPaths(): List<String> = flatMap { item ->
    when (item) {
        is RssFolder -> listOf(item.path) + item.children.flattenFolderPaths()
        is RssFeed -> emptyList()
    }
}

/**
 * Re-sorts the tree (case-insensitively by name, folders and feeds interleaved) recursively, so
 * toggling direction doesn't require a re-fetch.
 */
fun List<RssItem>.sortedTree(descending: Boolean): List<RssItem> {
    val sorted =
        if (descending) sortedByDescending { it.name.lowercase() }
        else sortedBy { it.name.lowercase() }
    return sorted.map { item ->
        if (item is RssFolder) item.copy(children = item.children.sortedTree(descending)) else item
    }
}
