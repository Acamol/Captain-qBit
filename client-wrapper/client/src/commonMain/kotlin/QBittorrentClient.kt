package qbittorrent

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import qbittorrent.internal.AtomicReference
import qbittorrent.internal.AuthHandler
import qbittorrent.internal.ErrorTransformer
import qbittorrent.internal.FileReader
import qbittorrent.internal.MainDataSync
import qbittorrent.internal.RawCookiesStorage
import qbittorrent.internal.TorrentPeersSync
import qbittorrent.internal.orThrow
import qbittorrent.models.AddTorrentBody
import qbittorrent.models.MainData
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentPeers

private const val PARAM_URLS = "urls"
private const val PARAM_TORRENTS = "torrents"
private const val PARAM_SAVE_PATH = "savepath"
private const val PARAM_COOKIE = "cookie"
private const val PARAM_CATEGORY = "category"
private const val PARAM_TAGS = "tags"
private const val PARAM_SKIP_CHECKING = "skip_checking"
private const val PARAM_PAUSED = "paused"
private const val PARAM_STOPPED = "stopped"
private const val PARAM_ROOT_FOLDER = "root_folder"
private const val PARAM_RENAME = "rename"
private const val PARAM_UP_LIMIT = "upLimit"
private const val PARAM_DL_LIMIT = "dlLimit"
private const val PARAM_RATIO_LIMIT = "ratioLimit"
private const val PARAM_SEEDING_TIME_LIMIT = "seedingTimeLimit"
private const val PARAM_AUTO_TTM = "autoTTM"
private const val PARAM_SEQUENTIAL_DOWNLOAD = "sequentialDownload"
private const val PARAM_FIRST_LAST_PIECE = "firstLastPiecePrio"

internal val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    useAlternativeNames = false
}

/**
 * qBittorrent Web API wrapper.
 *
 * https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)
 *
 * The bulk of the API surface (torrent actions, torrent info, trackers, categories/tags, peers, app
 * preferences, logs) lives in extension functions in sibling files - this class itself only holds
 * construction, session management, and the MainData/Torrent/TorrentPeers sync machinery.
 *
 * @param baseUrl The base URL of qBittorrent, including an explicit http:// or https:// scheme, ex.
 *   https://localhost:9000
 * @param username The qBittorrent username, default: admin
 * @param password The qBittorrent password, default: adminadmin
 * @param syncInterval The sync endpoint polling rate when subscribed to a [Flow], defaults to 5
 *   seconds.
 * @param httpClient Custom HTTPClient, useful when a default client engine is not used
 * @param dispatcher Coroutine dispatcher for flow API processing, defaults to
 *   [Dispatchers.Default].
 */
class QBittorrentClient(
    baseUrl: String,
    username: String = "admin",
    password: String = "adminadmin",
    syncInterval: Duration = 5.seconds,
    httpClient: HttpClient = HttpClient(),
    dispatcher: CoroutineDispatcher = Default,
) {
    companion object {
        const val RATIO_LIMIT_NONE = -1
        const val RATIO_LIMIT_GLOBAL = -2
        const val SEEDING_LIMIT_NONE = -1
        const val SEEDING_LIMIT_GLOBAL = -2

        internal val allList = listOf("all")
    }

    internal data class Config(
        val baseUrl: String,
        val username: String,
        val password: String,
        val syncInterval: Duration,
    )

    init {
        require(
            baseUrl.startsWith("http://", ignoreCase = true) ||
                baseUrl.startsWith("https://", ignoreCase = true)
        ) {
            "baseUrl must include an explicit scheme (http:// or https://), got: \"$baseUrl\""
        }
    }

    internal val config = Config(baseUrl, username, password, syncInterval)

    internal val http: HttpClient =
        httpClient.config {
            install(ErrorTransformer)
            install(AuthHandler) { config = this@QBittorrentClient.config }
            install(ContentNegotiation) { json(json) }
            install(HttpCookies) { storage = RawCookiesStorage(AcceptAllCookiesStorage()) }
        }
    private val syncScope = CoroutineScope(SupervisorJob() + dispatcher + http.coroutineContext)
    private val mainDataSync = MainDataSync(http, config, syncScope)
    private val peerDataSyncMapAtomic = AtomicReference(emptyMap<String, TorrentPeersSync>())
    private var peerDataSyncMap: Map<String, TorrentPeersSync>
        get() = peerDataSyncMapAtomic.value
        set(value) {
            peerDataSyncMapAtomic.value = value
        }

    private fun getPeersSync(hash: String): TorrentPeersSync? {
        return peerDataSyncMap[hash.lowercase()]
    }

    private fun createPeersSync(hash: String): TorrentPeersSync {
        return TorrentPeersSync(hash.lowercase(), http, config, syncScope).also { peersSync ->
            peerDataSyncMap = peerDataSyncMap + (hash to peersSync)
        }
    }

    private fun removePeersSync(hash: String) {
        peerDataSyncMap[hash]?.close()
        peerDataSyncMap = peerDataSyncMap - hash
    }

    /**
     * Create a session with the username and password provided in the constructor.
     *
     * NOTE: Calling [login] is not required as authentication is managed internally.
     */
    @Throws(QBittorrentException::class, CancellationException::class)
    suspend fun login() {
        http.plugin(AuthHandler).run {
            if (!tryAuth(http)) {
                val response = lastAuthResponseState.filterNotNull().first()
                throw QBittorrentException(response, response.bodyAsText())
            }
        }
    }

    /** End the current session. */
    @Throws(QBittorrentException::class, CancellationException::class)
    suspend fun logout() {
        http.get("${config.baseUrl}/api/v2/auth/logout").orThrow()
    }

    /**
     * Returns true when [observeMainData] or [observeTorrent] have at least one subscriber, meaning
     * the syncing endpoint is being polled at [syncInterval].
     */
    val isSyncing: Boolean
        get() = mainDataSync.isSyncing()

    /**
     * Emits the next [MainData] every [syncInterval] while subscribed.
     *
     * NOTE: The underlying logic and network requests will be started only once, no matter how many
     * times you invoke [observeMainData].
     */
    fun observeMainData(): Flow<MainData> {
        return mainDataSync.observeData().transform { (mainData, error) ->
            error?.let { throw it }
            mainData?.let { emit(it) }
        }
    }

    /**
     * Emits the latest [Torrent] data for the [hash]. If the torrent is removed or not found, the
     * flow will complete unless [waitIfMissing] is true.
     *
     * @param hash The info hash of the torrent to observe.
     * @param waitIfMissing When true, wait for the [hash] if it does not exist
     */
    fun observeTorrent(hash: String, waitIfMissing: Boolean = false): Flow<Torrent> {
        return if (waitIfMissing) {
                observeMainData().takeWhile { mainData -> !mainData.torrentsRemoved.contains(hash) }
            } else {
                observeMainData().takeWhile { mainData -> mainData.torrents.contains(hash) }
            }
            .mapNotNull { mainData -> mainData.torrents[hash] }
            .distinctUntilChanged()
    }

    /**
     * Emits the latest [TorrentPeers] data for the [hash]. If the torrent is removed or not found,
     * the flow will complete.
     *
     * @param hash The info hash of the torrent to observe.
     */
    fun observeTorrentPeers(hash: String): Flow<TorrentPeers> {
        val peersSync = getPeersSync(hash) ?: createPeersSync(hash)
        return peersSync
            .observeData()
            .takeWhile { (_, error) -> (error?.response?.status != HttpStatusCode.NotFound) }
            .transform { (mainData, error) ->
                error?.let { throw it }
                mainData?.let { emit(it) }
            }
            .onCompletion {
                syncScope.launch {
                    if (getPeersSync(hash)?.isSyncing() == false) {
                        removePeersSync(hash)
                    }
                }
            }
    }

    /**
     * This method can add torrents from server local file or from URLs. http://, https://, magnet:
     * and bc://bt/ links are supported.
     *
     * To include torrents, add HTTP and magnet urls to [AddTorrentBody.urls], or file paths to
     * [AddTorrentBody.torrents]. Only one [AddTorrentBody.urls] or [AddTorrentBody.torrents] entry
     * is required for the request to succeed.
     *
     * @param configure A function to configure the request body with [AddTorrentBody].
     * @see AddTorrentBody for all available options.
     */
    @Throws(QBittorrentException::class, CancellationException::class)
    suspend fun addTorrent(configure: AddTorrentBody.() -> Unit) {
        val body = AddTorrentBody().apply(configure)

        http
            .submitFormWithBinaryData(
                "${config.baseUrl}/api/v2/torrents/add",
                formData {
                    fun appendUnlessNull(param: String, value: Any?) {
                        value?.toString()?.let { append(param, it) }
                    }
                    appendUnlessNull(
                        PARAM_URLS,
                        body.urls.joinToString("|").takeUnless(String::isBlank)
                    )
                    appendUnlessNull(PARAM_SAVE_PATH, body.savepath)
                    appendUnlessNull(PARAM_COOKIE, body.cookie)
                    appendUnlessNull(PARAM_CATEGORY, body.category)
                    appendUnlessNull(
                        PARAM_TAGS,
                        body.tags.joinToString(",").takeUnless(String::isBlank)
                    )
                    appendUnlessNull(PARAM_SKIP_CHECKING, body.skipChecking)
                    // qBittorrent 5.x renamed the add "paused" field to "stopped"; send both so
                    // the torrent starts paused on 4.x and 5.x alike (unknown fields are ignored).
                    appendUnlessNull(PARAM_PAUSED, body.paused)
                    appendUnlessNull(PARAM_STOPPED, body.paused)
                    appendUnlessNull(PARAM_ROOT_FOLDER, body.rootFolder)
                    appendUnlessNull(PARAM_RENAME, body.rename)
                    appendUnlessNull(PARAM_UP_LIMIT, body.upLimit)
                    appendUnlessNull(PARAM_DL_LIMIT, body.dlLimit)
                    appendUnlessNull(PARAM_RATIO_LIMIT, body.ratioLimit)
                    appendUnlessNull(
                        PARAM_SEEDING_TIME_LIMIT,
                        body.seedingTimeLimit?.inWholeSeconds
                    )
                    appendUnlessNull(PARAM_AUTO_TTM, body.autoTMM)
                    appendUnlessNull(PARAM_SEQUENTIAL_DOWNLOAD, body.sequentialDownload)
                    appendUnlessNull(PARAM_FIRST_LAST_PIECE, body.firstLastPiecePriority)
                    val torrentFiles =
                        body.torrents
                            .mapNotNull { filePath ->
                                FileReader.contentOrNull(filePath)?.let { filePath to it }
                            }
                            .toMap()
                            .plus(body.rawTorrents)
                    torrentFiles.forEach { (torrentPath, fileContent) ->
                        val filename = torrentPath.substringAfterLast('/').substringAfterLast('\\')
                        append(
                            PARAM_TORRENTS,
                            fileContent,
                            Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=${filename.escapeIfNeeded()}"
                                )
                            }
                        )
                    }
                }
            )
            .orThrow()
    }
}

internal suspend fun login(http: HttpClient, config: QBittorrentClient.Config): HttpResponse {
    return http.submitForm(
        "${config.baseUrl}/api/v2/auth/login",
        formParameters =
            Parameters.build {
                append("username", config.username)
                append("password", config.password)
            }
    ) {
        header("Referer", config.baseUrl)
    }
}
