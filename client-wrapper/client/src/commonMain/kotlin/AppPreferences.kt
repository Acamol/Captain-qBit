package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
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
    // qBittorrent reads the settings from a form field named `json`, not a JSON request body.
    http
        .submitForm(
            "${config.baseUrl}/api/v2/app/setPreferences",
            formParameters = Parameters.build { append("json", prefs.toString()) },
        )
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
    // POST form, not GET: qBittorrent 5.x returns 405 for a GET on this setter.
    http
        .submitForm(
            "${config.baseUrl}/api/v2/transfer/setDownloadLimit",
            formParameters = Parameters.build { append("limit", limit.toString()) },
        )
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
    // POST form, not GET: qBittorrent 5.x returns 405 for a GET on this setter.
    http
        .submitForm(
            "${config.baseUrl}/api/v2/transfer/setUploadLimit",
            formParameters = Parameters.build { append("limit", limit.toString()) },
        )
        .orThrow()
}
