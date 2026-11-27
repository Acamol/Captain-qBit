package dev.yashgarg.qbit.ui.rss

import qbittorrent.models.RssArticle
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule

sealed interface RssLoad {
    data object Loading : RssLoad

    data object Refreshing : RssLoad

    data object Ready : RssLoad

    data class Failed(val message: String) : RssLoad
}

data class RssState(
    val itemsLoad: RssLoad = RssLoad.Loading,
    val rulesLoad: RssLoad = RssLoad.Loading,
    val items: List<RssItem> = emptyList(),
    val sortDescending: Boolean = false,
    val rules: Map<String, RssRule> = emptyMap(),
    val refreshIntervalMinutes: Int = 30,
    /**
     * qBittorrent's own switch for fetching RSS feeds at all - defaults true to avoid a false
     * warning flash before the real value loads.
     */
    val rssProcessingEnabled: Boolean = true,
    /** qBittorrent's own switch for RSS rules auto-downloading matches - same load-flicker note. */
    val rssAutoDownloadingEnabled: Boolean = true,
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    /**
     * Hashes of torrents already added to the active server, lowercased - compare against
     * [RssArticle.magnetHash].
     */
    val existingTorrentHashes: Set<String> = emptySet(),
) {
    fun canSaveRule(editingName: String?, name: String): Boolean =
        rulesLoad == RssLoad.Ready &&
            if (editingName != null) name == editingName && editingName in rules
            else name.isNotBlank() && name.trim() !in rules
}

private val MAGNET_HASH_REGEX = Regex("urn:btih:([A-Fa-f0-9]{40})")

/**
 * The torrent info-hash embedded in this article's magnet link, lowercased - null if
 * [RssArticle.torrentURL] isn't a magnet URI, or its hash isn't in the (far more common) 40-char
 * hex form this can compare directly against [qbittorrent.models.Torrent.hash].
 *
 * Deliberately doesn't fall back to downloading and parsing a `.torrent`-file link to derive the
 * hash: many private trackers count that download itself as a "grab" against the user's account
 * (ratio, per-link single-use tokens, etc.), so silently fetching one just to populate an "already
 * added" indicator isn't an acceptable trade-off. Articles with a `.torrent`-file link simply can't
 * show that indicator.
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

/** Only the listed URLs are editable. A deleted or unavailable feed can still be assigned. */
internal fun RssState.applyFeedSelection(
    assigned: List<String>,
    listed: List<String>,
    checked: Set<String>,
): List<String>? {
    if (itemsLoad != RssLoad.Ready) return null
    val retained = assigned.filter { it !in listed || it in checked }
    return retained + listed.filter { it in checked && it !in retained }.distinct()
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
