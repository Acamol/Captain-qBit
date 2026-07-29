package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import qbittorrent.internal.bodyOrThrow
import qbittorrent.internal.orThrow
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule
import qbittorrent.models.parseRssTree

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getRssItems(): List<RssItem> {
    return http
        .get("${config.baseUrl}/api/v2/rss/items") { parameter("withData", true) }
        .bodyOrThrow<JsonObject>()
        .let(::parseRssTree)
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.addRssFolder(path: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/addFolder",
            formParameters = Parameters.build { append("path", path) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.addRssFeed(url: String, path: String? = null) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/addFeed",
            formParameters =
                Parameters.build {
                    append("url", url)
                    path?.let { append("path", it) }
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeRssItem(itemPath: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/removeItem",
            formParameters = Parameters.build { append("path", itemPath) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.moveRssItem(itemPath: String, destPath: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/moveItem",
            formParameters =
                Parameters.build {
                    append("itemPath", itemPath)
                    append("destPath", destPath)
                },
        )
        .orThrow()
}

/** @param articleId When null, marks every article in the feed/folder as read. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.markRssItemAsRead(itemPath: String, articleId: String? = null) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/markAsRead",
            formParameters =
                Parameters.build {
                    append("itemPath", itemPath)
                    articleId?.let { append("id", it) }
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.refreshRssItem(itemPath: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/refreshItem",
            formParameters = Parameters.build { append("itemPath", itemPath) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getRssRules(): Map<String, RssRule> {
    return http.get("${config.baseUrl}/api/v2/rss/rules").bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setRssRule(ruleName: String, rule: RssRule) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/setRule",
            formParameters =
                Parameters.build {
                    append("ruleName", ruleName)
                    append("ruleDef", json.encodeToString(rule))
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.renameRssRule(ruleName: String, newRuleName: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/renameRule",
            formParameters =
                Parameters.build {
                    append("ruleName", ruleName)
                    append("newRuleName", newRuleName)
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeRssRule(ruleName: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/rss/removeRule",
            formParameters = Parameters.build { append("ruleName", ruleName) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getRssMatchingArticles(ruleName: String): Map<String, List<String>> {
    return http
        .get("${config.baseUrl}/api/v2/rss/matchingArticles") { parameter("ruleName", ruleName) }
        .bodyOrThrow()
}

/**
 * Minutes between qBittorrent's own automatic RSS feed refreshes - a single global server setting,
 * not per-feed. Anything that wants to know how often feed data can actually change (e.g. a "check
 * for new articles" poll) should use this rather than an independent interval, since polling faster
 * than the server itself refreshes just re-reads the same cached articles.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getRssRefreshIntervalMinutes(): Int =
    getPreferences()["rss_refresh_interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
