package dev.yashgarg.qbit.ui.torrent

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.utils.TransformUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qbittorrent.models.LogEntry
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentPeer

@HiltViewModel
class TorrentDetailsViewModel
@Inject
constructor(private val repository: QbitRepository, state: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(TorrentDetailsState())
    val uiState = _uiState.asStateFlow()

    private val _status = MutableSharedFlow<String>()
    val status = _status.asSharedFlow()

    // Emitted only after a torrent is actually removed, so the UI can navigate away.
    private val _removed = MutableSharedFlow<Unit>()
    val removed = _removed.asSharedFlow()

    private val hash by lazy { state.get<String>("torrentHash") }

    init {
        Log.d("TorrentDetailsViewModel", "TorrentHash: $hash")
        viewModelScope.launch {
            launch { syncTorrentFlow() }
            launch { syncPeers() }
            launch { syncAvailableFilters() }
            launch { getContent() }
        }
    }

    fun toggleTorrent(pause: Boolean, hash: String) {
        val hashes = listOf(hash)
        viewModelScope.launch {
            when (val result = repository.toggleTorrentsState(hashes, pause)) {
                is Ok -> _status.emit(if (pause) "Torrent paused" else "Torrent resumed")
                is Err ->
                    _status.emit(
                        result.error.friendlyMessage(
                            "Failed to ${if (pause) "pause" else "resume"} torrent"
                        )
                    )
            }
        }
    }

    fun removeTorrent(hash: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            when (val result = repository.removeTorrents(listOf(hash), deleteFiles)) {
                is Ok -> {
                    _status.emit("Torrent removed")
                    _removed.emit(Unit)
                }
                is Err -> _status.emit(result.error.friendlyMessage("Failed to remove torrent"))
            }
        }
    }

    fun forceRecheck(hash: String) {
        viewModelScope.launch {
            when (val result = repository.recheckTorrents(listOf(hash))) {
                is Ok -> _status.emit("Rechecking torrent")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to recheck torrent"))
            }
        }
    }

    fun forceReannounce(hash: String) {
        viewModelScope.launch {
            when (val result = repository.reannounceTorrents(listOf(hash))) {
                is Ok -> _status.emit("Reannouncing torrent")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to reannounce torrent"))
            }
        }
    }

    fun renameTorrent(torrentName: String, torrentHash: String) {
        viewModelScope.launch {
            when (val result = repository.renameTorrent(torrentHash, torrentName)) {
                is Ok -> _status.emit("Torrent renamed")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to rename torrent"))
            }
        }
    }

    fun setCategory(category: String) {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            when (val result = repository.setTorrentCategory(hash, category)) {
                is Ok -> _status.emit("Category set to \"$category\"")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to set category"))
            }
        }
    }

    /** Create a new category on the server and assign it to this torrent. */
    fun createCategory(name: String, savePath: String = "") {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            when (val created = repository.createCategory(name, savePath)) {
                is Ok ->
                    when (val set = repository.setTorrentCategory(hash, name)) {
                        is Ok -> _status.emit("Category set to \"$name\"")
                        is Err ->
                            _status.emit(
                                set.error.friendlyMessage(
                                    "Category created, but couldn't assign it"
                                )
                            )
                    }
                is Err -> _status.emit(created.error.friendlyMessage("Failed to create category"))
            }
        }
    }

    fun setSavePath(path: String) {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            repository.setAutoTorrentManagement(hash, false)
            when (val result = repository.setTorrentLocation(hash, path)) {
                is Ok -> _status.emit("Save path updated")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to set save path"))
            }
        }
    }

    fun setAutoManagement(enabled: Boolean) {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            when (val result = repository.setAutoTorrentManagement(hash, enabled)) {
                is Ok -> _status.emit("Auto management ${if (enabled) "enabled" else "disabled"}")
                is Err ->
                    _status.emit(result.error.friendlyMessage("Failed to update auto management"))
            }
        }
    }

    fun setTags(toAdd: List<String>, toRemove: List<String>) {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            if (toAdd.isNotEmpty()) repository.addTorrentTags(hash, toAdd)
            if (toRemove.isNotEmpty()) repository.removeTorrentTags(hash, toRemove)
            _status.emit("Tags updated")
        }
    }

    fun banPeer(peer: TorrentPeer) {
        val peerAddr = "${peer.ip}:${peer.port}"

        viewModelScope.launch {
            when (val result = repository.banPeers(listOf(peerAddr))) {
                is Ok -> _status.emit("Successfully banned $peerAddr")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to ban peer"))
            }
        }
    }

    /**
     * When a torrent is in an error/missing-files state, qBittorrent only reports the *state* in
     * the sync feed, not why. The actual reason (usually an I/O error) lives in the main log, so
     * fetch it and surface the most recent entry that mentions this torrent. Falls back to a
     * generic hint when nothing matches.
     */
    private suspend fun errorReasonFor(torrent: Torrent): String? {
        if (torrent.state != Torrent.State.ERROR && torrent.state != Torrent.State.MISSING_FILES) {
            return null
        }

        val fromLog =
            when (val logs = repository.getLogs()) {
                is Ok ->
                    logs.value
                        .filter {
                            it.type == LogEntry.TYPE_WARNING || it.type == LogEntry.TYPE_CRITICAL
                        }
                        .lastOrNull { it.message.contains(torrent.name, ignoreCase = true) }
                        ?.message
                is Err -> null
            }

        return fromLog
            ?: if (torrent.state == Torrent.State.MISSING_FILES) {
                "Files are missing from the save path. Check that the download location exists and is accessible, then force a recheck."
            } else {
                "This torrent is in an error state — usually an I/O problem. Verify the save path exists and is writable."
            }
    }

    private suspend fun syncAvailableFilters() {
        repository
            .observeMainData()
            .catch { /* non-fatal, ignore */}
            .collectLatest { mainData ->
                val categories = mainData.categories.keys.sorted()
                val tags = mainData.tags.sorted()
                _uiState.update { it.copy(availableCategories = categories, availableTags = tags) }
            }
    }

    private suspend fun syncTorrentFlow() {
        val hash = requireNotNull(hash)
        val result = runCatching {
            repository.observeTorrent(hash, false).collectLatest { info ->
                val props = repository.getTorrentProperties(hash)
                val trackers = repository.getTorrentTrackers(hash)
                val errorReason = errorReasonFor(info)

                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        torrent = info,
                        error = null,
                        errorReason = errorReason,
                        trackers =
                            when (trackers) {
                                is Ok -> trackers.value
                                is Err -> emptyList()
                            },
                        torrentProperties =
                            when (props) {
                                is Ok -> props.value
                                is Err -> null
                            }
                    )
                }
            }
        }

        when (result) {
            is Ok -> Unit
            is Err -> {
                _uiState.update { state ->
                    state.copy(loading = false, error = Exception(result.error.friendlyMessage()))
                }
            }
        }
    }

    private suspend fun getContent() {
        val files = hash?.let { repository.getTorrentFiles(it) }
        if (files != null) {
            when (files) {
                is Ok -> {
                    val tree = TransformUtil.transformFilesToTree(files.value, 0)
                    _uiState.update { state -> state.copy(contentTree = tree) }
                }
                is Err -> {
                    _uiState.update { state -> state.copy(contentTree = emptyList()) }
                }
            }
        }

        _uiState.update { state -> state.copy(contentLoading = false) }
    }

    private suspend fun syncPeers() {
        val result = runCatching {
            repository
                .observeTorrentPeers(requireNotNull(hash))
                .catch {
                    _uiState.update { state ->
                        state.copy(peersLoading = false, error = Exception(it.friendlyMessage()))
                    }
                }
                .collectLatest { peers ->
                    _uiState.update { state ->
                        state.copy(
                            peers = peers,
                            peersLoading = false,
                        )
                    }
                }
        }

        when (result) {
            is Ok -> Unit
            is Err ->
                _uiState.update { state ->
                    state.copy(
                        peersLoading = false,
                        error = Exception(result.error.friendlyMessage())
                    )
                }
        }
    }
}
