package qbittorrent

import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import qbittorrent.internal.bodyOrThrow
import qbittorrent.internal.orThrow
import qbittorrent.models.BuildInfo

/** Get the qBittorrent application preferences. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getPreferences(): JsonObject =
    http.get("${config.baseUrl}/api/v2/app/preferences").bodyOrThrow()

/** Set one or more qBittorrent application preferences. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setPreferences(prefs: JsonObject) {
    http
        .post("${config.baseUrl}/api/v2/app/setPreferences") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("json", prefs) })
        }
        .orThrow()
}

/** Get the application version. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getVersion(): String =
    http.get("${config.baseUrl}/api/v2/app/version").bodyOrThrow()

/** Get the Web API version. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getApiVersion(): String =
    http.get("${config.baseUrl}/api/v2/app/webapiVersion").bodyOrThrow()

/** Get the build info */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getBuildInfo(): BuildInfo =
    http.get("${config.baseUrl}/api/v2/app/buildInfo").bodyOrThrow()

/** Shutdown qBittorrent */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.shutdown() {
    http.get("${config.baseUrl}/api/v2/app/shutdown").orThrow()
}

/** Get the default torrent save path, ex. /user/home/downloads */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getDefaultSavePath(): String =
    http.get("${config.baseUrl}/api/v2/app/defaultSavePath").bodyOrThrow()

/** The response is 1 if alternative speed limits are enabled, 0 otherwise. */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getSpeedLimitsMode(): Int {
    return http.get("${config.baseUrl}/api/v2/transfer/speedLimitsMode").bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.toggleSpeedLimitsMode() {
    http.post("${config.baseUrl}/api/v2/transfer/toggleSpeedLimitsMode").orThrow()
}

/**
 * The response is the value of current global download speed limit in bytes/second; this value will
 * be zero if no limit is applied.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getGlobalDownloadLimit(): Int {
    return http.get("${config.baseUrl}/api/v2/transfer/downloadLimit").bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setGlobalDownloadLimit(limit: Int) {
    http
        .get("${config.baseUrl}/api/v2/transfer/setDownloadLimit") { parameter("limit", limit) }
        .orThrow()
}

/**
 * The response is the value of current global upload speed limit in bytes/second; this value will
 * be zero if no limit is applied.
 */
@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getGlobalUploadLimit(): Int {
    return http.get("${config.baseUrl}/api/v2/transfer/uploadLimit").bodyOrThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setGlobalUploadLimit(limit: Int) {
    http
        .get("${config.baseUrl}/api/v2/transfer/setUploadLimit") { parameter("limit", limit) }
        .orThrow()
}
