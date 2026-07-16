package dev.yashgarg.qbit.data

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import dev.yashgarg.qbit.data.manager.ClientManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import qbittorrent.*
import qbittorrent.models.LogEntry
import qbittorrent.models.MainData
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile
import qbittorrent.models.TorrentPeers
import qbittorrent.models.TorrentProperties
import qbittorrent.models.TorrentTracker

class QbitRepository @Inject constructor(private val clientManager: ClientManager) {
    // Fetch the CURRENT client each call (rather than caching one) so switching the active server
    // propagates: after a switch the client is rebuilt and the next call gets the new one. The
    // long-lived observe* flows are restarted at the ViewModel level on switch.
    private suspend fun client(): QBittorrentClient =
        clientManager.checkAndGetClient()
            ?: throw IllegalStateException("No active qBittorrent client")

    fun observeMainData(): Flow<MainData> {
        return flow { emitAll(client().observeMainData()) }
    }

    /** Latest MainData sync error (null while reachable); stays set until the next success. */
    fun observeMainDataError(): Flow<Throwable?> {
        return flow { emitAll(client().observeMainDataError()) }
    }

    fun observeTorrent(hash: String, waitIfMissing: Boolean): Flow<Torrent> {
        return flow { emitAll(client().observeTorrent(hash, waitIfMissing)) }
    }

    fun observeTorrentPeers(hash: String): Flow<TorrentPeers> {
        return flow { emitAll(client().observeTorrentPeers(hash)) }
    }

    suspend fun getApiVersion(): Result<String, Throwable> {
        return runCatching { client().getApiVersion() }
    }

    suspend fun getVersion(): Result<String, Throwable> {
        return runCatching { client().getVersion() }
    }

    suspend fun addTorrentUrl(
        url: String,
        category: String? = null,
        savePath: String? = null,
        paused: Boolean? = null,
        autoTmm: Boolean? = null,
    ): Result<Unit, Throwable> {
        return runCatching {
            client().addTorrent {
                urls.add(url)
                this.category = category
                this.savepath = savePath
                this.paused = paused
                this.autoTMM = autoTmm
            }
        }
    }

    suspend fun addTorrentFile(
        bytes: ByteArray,
        category: String? = null,
        savePath: String? = null,
        paused: Boolean? = null,
        autoTmm: Boolean? = null,
    ): Result<Unit, Throwable> {
        return runCatching {
            client().addTorrent {
                rawTorrents["torrent_file"] = bytes
                this.category = category
                this.savepath = savePath
                this.paused = paused
                this.autoTMM = autoTmm
            }
        }
    }

    suspend fun setTorrentCategory(
        hashes: List<String>,
        category: String,
    ): Result<Unit, Throwable> {
        return runCatching { client().setTorrentCategory(hashes, category) }
    }

    suspend fun createCategory(name: String, savePath: String = ""): Result<Unit, Throwable> {
        return runCatching { client().createCategory(name, savePath) }
    }

    suspend fun setTorrentLocation(hash: String, path: String): Result<Unit, Throwable> {
        return runCatching { client().setTorrentLocation(listOf(hash), path) }
    }

    suspend fun setAutoTorrentManagement(hash: String, enabled: Boolean): Result<Unit, Throwable> {
        return runCatching { client().setAutoTorrentManagement(listOf(hash), enabled) }
    }

    suspend fun addTorrentTags(hashes: List<String>, tags: List<String>): Result<Unit, Throwable> {
        return runCatching { client().addTorrentTags(hashes, tags) }
    }

    suspend fun removeTorrentTags(
        hashes: List<String>,
        tags: List<String>,
    ): Result<Unit, Throwable> {
        return runCatching { client().removeTorrentTags(hashes, tags) }
    }

    suspend fun createTags(tags: List<String>): Result<Unit, Throwable> {
        return runCatching { client().createTags(tags) }
    }

    suspend fun deleteTags(tags: List<String>): Result<Unit, Throwable> {
        return runCatching { client().deleteTags(tags) }
    }

    suspend fun deleteCategories(names: List<String>): Result<Unit, Throwable> {
        return runCatching { client().removeCategories(names) }
    }

    /**
     * Edits an existing category's save path - the officially supported operation; qBittorrent has
     * no endpoint to rename a category's identifier.
     */
    suspend fun editCategory(name: String, savePath: String): Result<Unit, Throwable> {
        return runCatching { client().editCategory(name, savePath) }
    }

    suspend fun removeTorrents(
        hashes: List<String>,
        deleteFiles: Boolean = false,
    ): Result<Unit, Throwable> {
        return runCatching { client().deleteTorrents(hashes, deleteFiles) }
    }

    suspend fun getLogs(): Result<List<LogEntry>, Throwable> {
        return runCatching { client().getLogs() }
    }

    suspend fun toggleTorrentsState(hashes: List<String>, pause: Boolean): Result<Unit, Throwable> {
        return runCatching {
            if (pause) client().pauseTorrents(hashes) else client().resumeTorrents(hashes)
        }
    }

    suspend fun getSpeedLimitMode(): Result<Int, Throwable> {
        return runCatching { client().getSpeedLimitsMode() }
    }

    suspend fun toggleSpeedLimitsMode(): Result<Unit, Throwable> {
        return runCatching { client().toggleSpeedLimitsMode() }
    }

    /** Per-torrent limits are in bytes/s; 0 means unlimited. */
    suspend fun setTorrentDownloadLimit(hash: String, limit: Long): Result<Unit, Throwable> {
        return runCatching { client().setTorrentDownloadLimit(listOf(hash), limit) }
    }

    suspend fun setTorrentUploadLimit(hash: String, limit: Long): Result<Unit, Throwable> {
        return runCatching { client().setTorrentUploadLimit(listOf(hash), limit) }
    }

    /**
     * Share limits. For each value: -2 = use the global limit, -1 = no limit, else the limit
     * ([ratioLimit] as a ratio, [seedingTimeMinutes] in minutes).
     */
    suspend fun setTorrentShareLimits(
        hash: String,
        ratioLimit: Float,
        seedingTimeMinutes: Long,
    ): Result<Unit, Throwable> {
        return runCatching {
            client().setTorrentShareLimits(listOf(hash), ratioLimit, seedingTimeMinutes)
        }
    }

    /** Global limits are in bytes/s; 0 means unlimited. */
    suspend fun getGlobalDownloadLimit(): Result<Int, Throwable> {
        return runCatching { client().getGlobalDownloadLimit() }
    }

    suspend fun setGlobalDownloadLimit(limit: Int): Result<Unit, Throwable> {
        return runCatching { client().setGlobalDownloadLimit(limit) }
    }

    suspend fun getGlobalUploadLimit(): Result<Int, Throwable> {
        return runCatching { client().getGlobalUploadLimit() }
    }

    suspend fun setGlobalUploadLimit(limit: Int): Result<Unit, Throwable> {
        return runCatching { client().setGlobalUploadLimit(limit) }
    }

    /**
     * Alternate speed limits live in the server's app preferences (not the transfer endpoints).
     * Values are in bytes/s; 0 means unlimited. Returns download-to-upload.
     */
    suspend fun getAltSpeedLimits(): Result<Pair<Int, Int>, Throwable> {
        return runCatching {
            val prefs = client().getPreferences()
            val dl = prefs["alt_dl_limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val ul = prefs["alt_up_limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            dl to ul
        }
    }

    suspend fun setAltSpeedLimits(
        downloadBytesPerSec: Int,
        uploadBytesPerSec: Int,
    ): Result<Unit, Throwable> {
        return runCatching {
            client()
                .setPreferences(
                    buildJsonObject {
                        put("alt_dl_limit", downloadBytesPerSec)
                        put("alt_up_limit", uploadBytesPerSec)
                    }
                )
        }
    }

    suspend fun setForceStart(hash: String, value: Boolean): Result<Unit, Throwable> {
        return runCatching { client().setForceStart(listOf(hash), value) }
    }

    suspend fun setSuperSeeding(hash: String, value: Boolean): Result<Unit, Throwable> {
        return runCatching { client().setSuperSeeding(listOf(hash), value) }
    }

    suspend fun toggleSequentialDownload(hash: String): Result<Unit, Throwable> {
        return runCatching { client().toggleSequentialDownload(listOf(hash)) }
    }

    suspend fun toggleFirstLastPriority(hash: String): Result<Unit, Throwable> {
        return runCatching { client().toggleFirstLastPriority(listOf(hash)) }
    }

    suspend fun recheckTorrents(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().recheckTorrents(hashes) }
    }

    suspend fun reannounceTorrents(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().reannounceTorrents(hashes) }
    }

    suspend fun renameTorrent(hash: String, name: String): Result<Unit, Throwable> {
        return runCatching { client().setTorrentName(hash, name) }
    }

    /**
     * Queue-priority actions. These only have an effect when torrent queueing is enabled on the
     * server; qBittorrent otherwise reports every torrent's priority as -1 and ignores the call.
     */
    suspend fun increaseTorrentPriority(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().increasePriority(hashes) }
    }

    suspend fun decreaseTorrentPriority(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().decreasePriority(hashes) }
    }

    suspend fun maxTorrentPriority(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().maxPriority(hashes) }
    }

    suspend fun minTorrentPriority(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().minPriority(hashes) }
    }

    suspend fun banPeers(peers: List<String>): Result<Unit, Throwable> {
        return runCatching { client().banPeers(peers) }
    }

    suspend fun getTorrentProperties(hash: String): Result<TorrentProperties, Throwable> {
        return runCatching { client().getTorrentProperties(hash) }
    }

    suspend fun getTorrentTrackers(hash: String): Result<List<TorrentTracker>, Throwable> {
        return runCatching { client().getTrackers(hash) ?: emptyList() }
    }

    suspend fun addTorrentTrackers(hash: String, urls: List<String>): Result<Unit, Throwable> {
        return runCatching { client().addTrackers(hash, urls) }
    }

    suspend fun editTorrentTracker(
        hash: String,
        originalUrl: String,
        newUrl: String,
    ): Result<Unit, Throwable> {
        return runCatching { client().editTrackers(hash, originalUrl, newUrl) }
    }

    suspend fun removeTorrentTrackers(hash: String, urls: List<String>): Result<Unit, Throwable> {
        return runCatching { client().removeTrackers(hash, urls) }
    }

    suspend fun getTorrentFiles(hash: String): Result<List<TorrentFile>, Throwable> {
        return runCatching { client().getTorrentFiles(hash) }
    }

    /** [oldPath]/[newPath] are relative to the torrent root, "/"-separated. */
    suspend fun renameFile(
        hash: String,
        oldPath: String,
        newPath: String,
    ): Result<Unit, Throwable> {
        return runCatching { client().renameFile(hash, oldPath, newPath) }
    }

    suspend fun renameFolder(
        hash: String,
        oldPath: String,
        newPath: String,
    ): Result<Unit, Throwable> {
        return runCatching { client().renameFolder(hash, oldPath, newPath) }
    }

    /** Priorities: 0 = do not download, 1 = normal, 6 = high, 7 = maximal. */
    suspend fun setFilePriority(
        hash: String,
        ids: List<Int>,
        priority: Int,
    ): Result<Unit, Throwable> {
        return runCatching { client().setFilePriority(hash, ids, priority) }
    }
}
