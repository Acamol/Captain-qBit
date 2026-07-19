package qbittorrent

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import qbittorrent.internal.orThrow
import qbittorrent.models.TorrentTracker

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.editTrackers(hash: String, originalUrl: String, newUrl: String) {
    // qBittorrent 5.2 renamed the original-URL parameter from `origUrl` to `url`; the docs still
    // say
    // `origUrl`. Send both (same value) so it works across versions — qBit ignores the unknown one.
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/editTracker",
            formParameters =
                Parameters.build {
                    append("hash", hash)
                    append("origUrl", originalUrl)
                    append("url", originalUrl)
                    append("newUrl", newUrl)
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.addTrackers(hash: String, urls: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/addTrackers",
            formParameters =
                Parameters.build {
                    append("hash", hash)
                    append("urls", urls.joinToString("\n"))
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeTrackers(hash: String, urls: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/removeTrackers",
            formParameters =
                Parameters.build {
                    append("hash", hash)
                    append("urls", urls.joinToString("|"))
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTrackers(hash: String): List<TorrentTracker>? {
    val response =
        http.get("${config.baseUrl}/api/v2/torrents/trackers") { parameter("hash", hash) }
    return if (response.status.isSuccess()) {
        response.body()
    } else {
        if (response.status != HttpStatusCode.NotFound) {
            response.orThrow()
        }
        null
    }
}
