package qbittorrent.models

/**
 * A node in the RSS folder/feed tree returned by `/api/v2/rss/items`. [path] joins every ancestor
 * folder name with `\`, matching the `itemPath` argument every mutation endpoint expects.
 */
sealed interface RssItem {
    val name: String
    val path: String
}

data class RssFolder(
    override val name: String,
    override val path: String,
    val children: List<RssItem>,
) : RssItem

data class RssFeed(
    override val name: String,
    override val path: String,
    val uid: String,
    val url: String,
    val title: String,
    val lastBuildDate: String?,
    val isLoading: Boolean,
    val hasError: Boolean,
    val articles: List<RssArticle>,
) : RssItem
