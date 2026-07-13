package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import qbittorrent.internal.orThrow

/**
 * Pause one or more torrents
 *
 * @param hashes A single torrent hash, list of torrents, or 'all'.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.pauseTorrents(hashes: List<String> = QBittorrentClient.allList) {
    stopOrStartTorrents(hashes, primaryAction = "stop", legacyAction = "pause")
}

/**
 * Resume one or more torrents
 *
 * @param hashes A single torrent hash, list of torrents, or 'all'.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.resumeTorrents(hashes: List<String> = QBittorrentClient.allList) {
    stopOrStartTorrents(hashes, primaryAction = "start", legacyAction = "resume")
}

/**
 * qBittorrent 5.0 renamed the pause/resume endpoints to stop/start and dropped the old names in
 * later releases. Call the modern endpoint first and fall back to the legacy name on a 404, so this
 * works against both 4.x and 5.x servers.
 */
private suspend fun QBittorrentClient.stopOrStartTorrents(
    hashes: List<String>,
    primaryAction: String,
    legacyAction: String,
) {
    val params = Parameters.build { append("hashes", hashes.joinToString("|")) }
    val response =
        http.submitForm("${config.baseUrl}/api/v2/torrents/$primaryAction", formParameters = params)
    if (response.status == HttpStatusCode.NotFound) {
        http
            .submitForm("${config.baseUrl}/api/v2/torrents/$legacyAction", formParameters = params)
            .orThrow()
    } else {
        response.orThrow()
    }
}

/**
 * Delete one or more torrents.
 *
 * @param hashes A single torrent hash, list of torrents, or 'all'.
 * @param deleteFiles If true, delete all the torrents files.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.deleteTorrents(hashes: List<String>, deleteFiles: Boolean = false) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/delete",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("deleteFiles", deleteFiles.toString())
                }
        )
        .orThrow()
}

/** Recheck a torrent in qBittorrent. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.recheckTorrents(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/recheck",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

/** Reannounce a torrent. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.reannounceTorrents(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/reannounce",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

// The priority endpoints below mutate state, so qBittorrent only accepts them as POSTed form
// data - a GET with query params is rejected (405).

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.increasePriority(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/increasePrio",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.decreasePriority(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/decreasePrio",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.maxPriority(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/topPrio",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.minPriority(hashes: List<String> = QBittorrentClient.allList) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/bottomPrio",
            formParameters = Parameters.build { append("hashes", hashes.joinToString("|")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setFilePriority(hash: String, ids: List<Int>, priority: Int) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/filePrio",
            formParameters =
                Parameters.build {
                    append("hash", hash)
                    append("id", ids.joinToString("|"))
                    append("priority", priority.toString())
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentLocation(
    hashes: List<String> = QBittorrentClient.allList,
    location: String
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setLocation",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("location", location)
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentName(hash: String, name: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/rename",
            formParameters =
                Parameters.build {
                    append("hash", hash)
                    append("name", name)
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setAutoTorrentManagement(
    hashes: List<String> = QBittorrentClient.allList,
    enabled: Boolean
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setAutoManagement",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("enable", enabled.toString())
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.toggleSequentialDownload(
    hashes: List<String> = QBittorrentClient.allList
) {
    http
        .get("${config.baseUrl}/api/v2/torrents/toggleSequentialDownload") {
            parameter("hashes", hashes.joinToString("|"))
        }
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.toggleFirstLastPriority(
    hashes: List<String> = QBittorrentClient.allList
) {
    http
        .get("${config.baseUrl}/api/v2/torrents/toggleFirstLastPiecePrio") {
            parameter("hashes", hashes.joinToString("|"))
        }
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setForceStart(
    hashes: List<String> = QBittorrentClient.allList,
    value: Boolean
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setForceStart",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("value", value.toString())
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setSuperSeeding(
    hashes: List<String> = QBittorrentClient.allList,
    value: Boolean
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setSuperSeeding",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("value", value.toString())
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.renameFile(hash: String, id: Int, newName: String) {
    http
        .get("${config.baseUrl}/api/v2/torrents/renameFile") {
            parameter("hash", hash)
            parameter("id", id)
            parameter("name", newName)
        }
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.renameFolder(hash: String, id: Int, newName: String) {
    http
        .get("${config.baseUrl}/api/v2/torrents/renameFolder") {
            parameter("hash", hash)
            parameter("id", id)
            parameter("name", newName)
        }
        .orThrow()
}
