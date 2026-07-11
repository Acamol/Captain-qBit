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
    http
        .get("${config.baseUrl}/api/v2/torrents/editTracker") {
            parameter("hash", hash)
            parameter("origUrl", originalUrl)
            parameter("newUrl", newUrl)
        }
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
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeTrackers(hash: String, urls: List<String>) {
    http
        .get("${config.baseUrl}/api/v2/torrents/removeTrackers") {
            parameter("hash", hash)
            parameter("urls", urls.joinToString("|"))
        }
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
