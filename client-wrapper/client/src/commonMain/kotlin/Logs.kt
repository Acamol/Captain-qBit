package qbittorrent

import io.ktor.client.request.*
import kotlin.coroutines.cancellation.CancellationException
import qbittorrent.internal.bodyOrThrow
import qbittorrent.models.LogEntry
import qbittorrent.models.PeerLog

/** @param lastKnownId Exclude messages with "message id" <= last_known_id (default: -1) */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getPeerLogs(lastKnownId: Int = -1): List<PeerLog> =
    http
        .get("${config.baseUrl}/api/v2/log/peers") { parameter("last_known_id", lastKnownId) }
        .bodyOrThrow()

/**
 * @param normal Include normal messages (default: true)
 * @param info Include info messages (default: true)
 * @param warning Include warning messages (default: true)
 * @param critical Include critical messages (default: true)
 * @param lastKnownId Exclude messages with "message id" <= last_known_id (default: -1)
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getLogs(
    normal: Boolean = true,
    info: Boolean = true,
    warning: Boolean = true,
    critical: Boolean = true,
    lastKnownId: Int = -1,
): List<LogEntry> =
    http
        .get("${config.baseUrl}/api/v2/log/main") {
            parameter("normal", normal)
            parameter("info", info)
            parameter("warning", warning)
            parameter("critical", critical)
            parameter("last_known_id", lastKnownId)
        }
        .bodyOrThrow()
