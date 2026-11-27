package qbittorrent.internal

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.reflect.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import qbittorrent.QBittorrentClient
import qbittorrent.QBittorrentException
import qbittorrent.json
import qbittorrent.models.MainData
import qbittorrent.models.TorrentPeers

typealias DataStatePair<T> = Pair<T?, QBittorrentException?>

internal abstract class DataSync<T>(
    private val typeInfo: TypeInfo,
    private val http: HttpClient,
    private val config: QBittorrentClient.Config,
    syncScope: CoroutineScope,
) {

    abstract val endpointUrl: String
    abstract val nestedObjectKeys: List<String>

    open fun HttpRequestBuilder.configureRequest() = Unit

    private val serializer = serializer(requireNotNull(typeInfo.kotlinType))
    private val state = MutableStateFlow<DataStatePair<T>>(null to null)
    // Durable last-poll error: set on a failed fetch, cleared on the next success. Unlike the
    // transient error carried in [state] (reset almost immediately, so a conflating collector can
    // miss it), this stays set while the endpoint is unreachable, so observers can reliably show an
    // offline state.
    private val syncError = MutableStateFlow<Throwable?>(null)
    private val isSyncingState =
        state.subscriptionCount.map { it > 0 }.stateIn(syncScope, Eagerly, false)
    private val atomicSyncRid = AtomicReference(0L)
    private var syncRid: Long
        get() = atomicSyncRid.value
        set(value) {
            atomicSyncRid.value = value
        }

    private val syncLoopJob = syncScope.launch {
        while (true) {
            // Wait for the first subscribers
            isSyncingState.first { it }
            syncData()
        }
    }

    fun isSyncing(): Boolean {
        return isSyncingState.value
    }

    fun observeData(): Flow<DataStatePair<T>> {
        return state
    }

    /** Latest sync error, or null while the endpoint is reachable. Cleared on the next success. */
    fun observeError(): StateFlow<Throwable?> = syncError

    fun close() {
        syncLoopJob.cancel()
    }

    private suspend fun syncData() {
        try {
            // Keep the data we already have (also after a failed poll: the next patch is requested
            // against the last rid we actually received, and the server sends a full update if it
            // can no longer serve a delta for it). Only fetch a fresh snapshot when there is none.
            val (initialMainData, _) =
                state.updateAndGet { (mainData, _) -> (mainData ?: fetchInitial()) to null }
            syncError.value = null

            delay(config.syncInterval)

            val mainDataJson = json.encodeToJsonElement(serializer, initialMainData).mutateJson()
            // Patch MainData while there is at least one subscriber
            while (isSyncingState.value) {
                // Fetch the next MainData patch and merge into existing model, remove any error.
                // The server only sends a delta against the rid it last sent, so echo that back
                // rather than counting locally: with an API key every client shares one server
                // session, and a guessed rid could match a response sent to another client,
                // yielding a delta that silently skips changes (e.g. a newly created category).
                val patch = fetchData(syncRid).bodyOrThrow<JsonObject>()
                state.value = mainDataJson.applyPatch(patch) to null
                patch.responseId()?.let { syncRid = it }
                syncError.value = null

                delay(config.syncInterval)
            }
        } catch (e: CancellationException) {
            // Closing the client (switching servers) or losing the last subscriber cancels this
            // scope mid-poll. That is lifecycle, not a sync failure - reporting it flashed a
            // "failed to sync data" error every time the user switched server.
            throw e
        } catch (e: Exception) {
            // Failed to fetch patch, keep current MainData and add the error
            println("DataSync: sync failed for $endpointUrl: ${e.message}")
            val qbEx = if (e is QBittorrentException) e else QBittorrentException(e)
            syncError.value = qbEx
            state.update { (mainData, _) -> mainData to qbEx }
            yield()
            // Active state subscribers have seen the error, clear it
            state.update { (mainData, _) -> mainData to null }
        }
    }

    private suspend fun fetchInitial(): T {
        val body = fetchData(0).bodyOrThrow<JsonObject>()
        syncRid = body.responseId() ?: 0
        @Suppress("UNCHECKED_CAST")
        return json.decodeFromJsonElement(serializer, body) as T
    }

    private fun JsonObject.responseId(): Long? = get("rid")?.jsonPrimitive?.longOrNull

    private suspend fun fetchData(rid: Long): HttpResponse {
        return http.get("${config.baseUrl}/${endpointUrl.trimStart('/')}") {
            parameter("rid", rid)
            configureRequest()
        }
    }

    private fun MutableMap<String, JsonElement>.applyPatch(newObject: JsonObject): T {
        // full_update marks a complete snapshot rather than a delta, which the server also sends
        // mid-stream when it no longer recognises our rid. Merging one would keep anything the
        // server has since dropped: [merge] only ever adds entries, and the *_removed lists that
        // normally delete them are absent from a snapshot.
        if (newObject.isFullUpdate()) {
            clear()
            putAll(newObject)
        } else {
            // Unlike ordinary array fields, the top-level tags array contains additions only.
            val tags =
                newObject["tags"]?.let { added ->
                    JsonArray((get("tags")?.jsonArray.orEmpty() + added.jsonArray).distinct())
                }
            merge(newObject, nestedObjectKeys)
            if (tags != null) put("tags", tags)
        }
        nestedObjectKeys.forEach { key -> dropRemoved(key) }
        dropRemovedStrings("tags")

        // Note: Create new model instance here so that one update event includes
        // identifiers of removed data
        @Suppress("UNCHECKED_CAST")
        val mainData: T = json.decodeFromJsonElement(serializer, JsonObject(this)) as T

        nestedObjectKeys.forEach { key -> resetRemoved(key) }
        resetRemoved("tags")
        return mainData
    }
}

/** Manages a single [MainData] instance and updates it periodically when it is being observed. */
internal class MainDataSync(
    http: HttpClient,
    config: QBittorrentClient.Config,
    syncScope: CoroutineScope,
) :
    DataSync<MainData>(
        typeInfo = typeInfo<MainData>(),
        http = http,
        config = config,
        syncScope = syncScope,
    ) {
    override val endpointUrl: String = "/api/v2/sync/maindata"
    override val nestedObjectKeys: List<String> = listOf("torrents", "categories")
}

/**
 * Manages a single [TorrentPeers] instance and updates it periodically when it is being observed.
 */
internal class TorrentPeersSync(
    private val hash: String,
    http: HttpClient,
    config: QBittorrentClient.Config,
    syncScope: CoroutineScope,
) :
    DataSync<TorrentPeers>(
        typeInfo = typeInfo<TorrentPeers>(),
        http = http,
        config = config,
        syncScope = syncScope,
    ) {
    override val endpointUrl: String = "/api/v2/sync/torrentPeers"
    override val nestedObjectKeys: List<String> = listOf("peers")

    override fun HttpRequestBuilder.configureRequest() {
        parameter("hash", hash)
    }
}
