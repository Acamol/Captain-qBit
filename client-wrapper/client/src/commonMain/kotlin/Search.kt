package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import qbittorrent.internal.bodyOrThrow
import qbittorrent.internal.orThrow
import qbittorrent.models.SearchPlugin
import qbittorrent.models.SearchResultsPage

@Serializable private data class SearchStartResponse(val id: Int)

/** Plugin selector accepted by [startSearch] in place of explicit names. */
const val SEARCH_PLUGINS_ENABLED = "enabled"

/** Category selector accepted by [startSearch] to search every category. */
const val SEARCH_CATEGORY_ALL = "all"

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getSearchPlugins(): List<SearchPlugin> {
    return http.get("${config.baseUrl}/api/v2/search/plugins").bodyOrThrow()
}

/**
 * Starts a search and returns its job id. [plugins] is [SEARCH_PLUGINS_ENABLED] or a list of plugin
 * names.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.startSearch(
    pattern: String,
    plugins: List<String> = listOf(SEARCH_PLUGINS_ENABLED),
    category: String = SEARCH_CATEGORY_ALL,
): Int {
    return http
        .submitForm(
            "${config.baseUrl}/api/v2/search/start",
            formParameters =
                Parameters.build {
                    append("pattern", pattern)
                    append("plugins", plugins.joinToString("|"))
                    append("category", category)
                },
        )
        .bodyOrThrow<SearchStartResponse>()
        .id
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getSearchResults(
    id: Int,
    limit: Int = 0,
    offset: Int = 0,
): SearchResultsPage {
    return http
        .get("${config.baseUrl}/api/v2/search/results") {
            parameter("id", id)
            parameter("limit", limit)
            parameter("offset", offset)
        }
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.stopSearch(id: Int) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/search/stop",
            formParameters = Parameters.build { append("id", id.toString()) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.deleteSearch(id: Int) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/search/delete",
            formParameters = Parameters.build { append("id", id.toString()) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.enableSearchPlugins(names: List<String>, enabled: Boolean) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/search/enablePlugin",
            formParameters =
                Parameters.build {
                    append("names", names.joinToString("|"))
                    append("enable", enabled.toString())
                },
        )
        .orThrow()
}

/** Installs plugins from URLs or magnet-style sources; the server downloads and runs them. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.installSearchPlugins(sources: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/search/installPlugin",
            formParameters = Parameters.build { append("sources", sources.joinToString("|")) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.uninstallSearchPlugins(names: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/search/uninstallPlugin",
            formParameters = Parameters.build { append("names", names.joinToString("|")) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.updateSearchPlugins() {
    http.submitForm("${config.baseUrl}/api/v2/search/updatePlugins").orThrow()
}
