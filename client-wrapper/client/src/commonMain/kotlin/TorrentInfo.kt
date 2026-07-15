package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import qbittorrent.internal.bodyOrThrow
import qbittorrent.internal.orThrow
import qbittorrent.models.GlobalTransferInfo
import qbittorrent.models.PieceState
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile
import qbittorrent.models.TorrentFilter
import qbittorrent.models.TorrentProperties
import qbittorrent.models.Webseed

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTorrents(
    filter: TorrentFilter = TorrentFilter.ALL,
    category: String = "",
    sort: String = "",
    reverse: Boolean = false,
    limit: Int = 0,
    offset: Int = 0,
    tag: String? = null,
    hashes: List<String> = emptyList(),
): List<Torrent> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/info") {
            parameter("filter", filter.name.lowercase())
            parameter("reverse", reverse)
            parameter("limit", limit)
            parameter("offset", offset)
            if (hashes.isNotEmpty()) {
                parameter("hashes", hashes.joinToString("|"))
            }
            if (category.isNotBlank()) {
                parameter("category", category)
            }
            if (sort.isNotBlank()) {
                parameter("sort", sort)
            }
            if (tag != null) {
                parameter("tag", tag)
            }
        }
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTorrentProperties(hash: String): TorrentProperties {
    return http
        .get("${config.baseUrl}/api/v2/torrents/properties") { parameter("hash", hash) }
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getGlobalTransferInfo(): GlobalTransferInfo {
    return http.get("${config.baseUrl}/api/v2/transfer/info").bodyOrThrow()
}

/** Get the [TorrentFile]s for [hash] or an empty list if not yet not available. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTorrentFiles(
    hash: String,
    indexes: List<Int> = emptyList(),
): List<TorrentFile> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/files") {
            parameter("hash", hash)
            if (indexes.isNotEmpty()) {
                parameter("indexes", indexes.joinToString("|"))
            }
        }
        .bodyOrThrow()
}

/** Get piece states for the torrent at [hash]. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getPieceStates(hash: String): List<PieceState> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/pieceStates") { parameter("hash", hash) }
        .bodyOrThrow()
}

/** Get piece hashes for the torrent at [hash]. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getPieceHashes(hash: String): List<String> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/pieceHashes") { parameter("hash", hash) }
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getWebseeds(hash: String): List<Webseed> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/webseeds") { parameter("hash", hash) }
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTorrentDownloadLimit(
    hashes: List<String> = QBittorrentClient.allList
): Map<String, Long> {
    return http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/downloadLimit",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) },
        )
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentDownloadLimit(
    hashes: List<String> = QBittorrentClient.allList
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/downloadLimit",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentShareLimits(
    hashes: List<String> = QBittorrentClient.allList,
    ratioLimit: Float,
    seedingTimeLimit: Duration,
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setShareLimits",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("ratioLimit", ratioLimit.toString())
                    append("seedingTimeLimit", seedingTimeLimit.inWholeSeconds.toString())
                },
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTorrentUploadLimit(
    hashes: List<String> = QBittorrentClient.allList
): Map<String, Long> {
    return http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/uploadLimit",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) },
        )
        .bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentUploadLimit(
    hashes: List<String> = QBittorrentClient.allList,
    limit: Long,
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setUploadLimit",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("limit", limit.toString())
                },
        )
        .orThrow()
}
