package dev.yashgarg.qbit.ui.rss

import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule

data class RssState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<RssItem> = emptyList(),
    val rules: Map<String, RssRule> = emptyMap(),
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
)

/** Every feed in the tree, depth-first - used by the rule editor's "affected feeds" picker. */
fun List<RssItem>.flattenFeeds(): List<RssFeed> = flatMap { item ->
    when (item) {
        is RssFeed -> listOf(item)
        is RssFolder -> item.children.flattenFeeds()
    }
}
