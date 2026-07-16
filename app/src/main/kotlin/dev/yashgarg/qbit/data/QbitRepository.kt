package dev.yashgarg.qbit.data

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import dev.yashgarg.qbit.data.manager.ClientManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

    suspend fun recheckTorrents(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().recheckTorrents(hashes) }
    }

    suspend fun reannounceTorrents(hashes: List<String>): Result<Unit, Throwable> {
        return runCatching { client().reannounceTorrents(hashes) }
    }

    suspend fun renameTorrent(hash: String, name: String): Result<Unit, Throwable> {
        return runCatching { client().setTorrentName(hash, name) }
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

    suspend fun getTorrentFiles(hash: String): Result<List<TorrentFile>, Throwable> {
        return runCatching { client().getTorrentFiles(hash) }
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
