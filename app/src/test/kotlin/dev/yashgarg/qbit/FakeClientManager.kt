package dev.yashgarg.qbit

import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import qbittorrent.QBittorrentClient
import qbittorrent.models.ConnectionStatus
import qbittorrent.models.ServerState
import qbittorrent.models.Torrent

/**
 * A [ClientManager] backed by an in-JVM Ktor [MockEngine] — the integration tests run hermetically
 * with no real qBittorrent server (and no `base_url`/`password` env vars). A mutable set of torrent
 * hashes is shared across the (freshly built per call) clients so `torrents/add` and
 * `torrents/delete` are reflected by the next `sync/maindata` response.
 */
class FakeClientManager : ClientManager {
    private val torrents = mutableSetOf<String>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverState =
        ServerState(
            allTimeDownload = 0L,
            allTimeUpload = 0L,
            averageTimeInQueue = 0,
            connectionStatus = ConnectionStatus.CONNECTED,
            dhtNodes = 0,
            dlInfoData = 0L,
            dlInfoSpeed = 0L,
            dlRateLimit = 0L,
            freeSpace = 0L,
            globalShareRatio = "0",
            queuedIoJobs = 0,
            queueing = false,
            readCacheHits = "0",
            readCacheOverload = "0",
            refreshInterval = 1500,
            totalBuffersSize = 0,
            totalPeerConnections = 0,
            totalQueuedSize = 0,
            sessionWaste = 0L,
            upInfoData = 0L,
            upInfoSpeed = 0L,
            upRateLimit = 0,
            useAltSpeedLimits = false,
            writeCacheOverload = "0",
        )

    private fun torrentJson(hash: String): String =
        json.encodeToString(
            Torrent(
                addedOn = 0L,
                amountLeft = 0L,
                autoTmm = false,
                availability = 0f,
                category = "",
                completed = 0L,
                completedOn = 0L,
                contentPath = "",
                dlLimit = -1L,
                dlspeed = 0L,
                downloaded = 0f,
                downloadedSession = 0f,
                eta = 0L,
                firstLastPiecePriority = false,
                forceStart = false,
                hash = hash,
                lastActivity = 0L,
                magnetUri = "",
                maxRatio = -1f,
                maxSeedingTime = -1L,
                name = "Test $hash",
                seedsInSwarm = 0,
                leechersInSwarm = 0,
                connectedLeechers = 0,
                connectedSeeds = 0,
                priority = 0,
                progress = 1f,
                ratio = 1f,
                ratioLimit = -1f,
                savePath = "",
                seedingTimeLimit = -1L,
                seenCompleted = 0L,
                sequentialDownload = false,
                size = 1000L,
                state = Torrent.State.UPLOADING,
                superSeeding = false,
                tags = emptyList(),
                timeActive = 0L,
                seedingTime = 0L,
                totalSize = 1000L,
                tracker = "",
                uploadLimit = -1L,
                uploaded = 0L,
                uploadedSession = 0L,
                uploadSpeed = 0L,
            )
        )

    private fun mainDataJson(): String {
        val torrentsObj = torrents.joinToString(",") { "\"$it\":${torrentJson(it)}" }
        return """{"rid":1,"full_update":true,"torrents":{$torrentsObj},""" +
            """"server_state":${json.encodeToString(serverState)}}"""
    }

    private suspend fun requestBody(body: OutgoingContent): String =
        when (body) {
            is TextContent -> body.text
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            else -> ""
        }

    private fun mockEngine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/auth/login") ->
                respond("Ok.", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "SID=mock"))
            path.endsWith("/app/version") -> respond("v4.6.0")
            path.endsWith("/app/webapiVersion") -> respond("2.9.3")
            path.endsWith("/torrents/add") -> {
                torrents.add(Constants.magnetHash)
                respond("Ok.")
            }
            path.endsWith("/torrents/delete") -> {
                val body = requestBody(request.body)
                torrents.removeAll { body.contains(it) }
                respond("Ok.")
            }
            path.endsWith("/sync/maindata") ->
                respond(
                    mainDataJson(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            else -> respond("Ok.")
        }
    }

    private val _configStatus = MutableSharedFlow<ConfigStatus>()
    override val configStatus: SharedFlow<ConfigStatus>
        get() = _configStatus.asSharedFlow()

    // username/password intentionally left as the client defaults: the MockEngine accepts any auth,
    // so there are no real credentials here to configure or hardcode.
    override suspend fun checkAndGetClient() =
        QBittorrentClient(
            baseUrl = "http://localhost",
            syncInterval = ClientManager.syncInterval,
            httpClient = HttpClient(mockEngine()),
            dispatcher = Dispatchers.Default,
        )

    override suspend fun setActiveServer(id: Int) = Unit
}
