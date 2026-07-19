package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import qbittorrent.internal.orThrow

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.addPeers(hashes: List<String>, peers: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/addPeers",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("peers", peers.joinToString("|"))
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.banPeers(peers: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/transfer/banPeers",
            formParameters = Parameters.build { append("peers", peers.joinToString("|")) },
        )
        .orThrow()
}
