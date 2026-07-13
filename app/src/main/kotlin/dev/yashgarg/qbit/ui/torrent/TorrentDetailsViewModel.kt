package dev.yashgarg.qbit.ui.torrent

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.common.StatusViewModel
import dev.yashgarg.qbit.utils.TransformUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qbittorrent.models.LogEntry
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentPeer

@HiltViewModel
class TorrentDetailsViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    state: SavedStateHandle,
    @ApplicationContext context: Context,
) : StatusViewModel(context) {
    private val _uiState = MutableStateFlow(TorrentDetailsState())
    val uiState = _uiState.asStateFlow()

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
        launchStatus(
            successMessage =
                if (pause) getString(CommonR.string.status_torrent_paused)
                else getString(CommonR.string.status_torrent_resumed),
            failureMessage =
                if (pause) getString(CommonR.string.status_pause_torrent_failure)
                else getString(CommonR.string.status_resume_torrent_failure),
        ) {
            repository.toggleTorrentsState(hashes, pause)
        }
    }

    fun removeTorrent(hash: String, deleteFiles: Boolean = false) {
        launchStatus(
            successMessage = getString(CommonR.string.status_torrent_removed),
            failureMessage = getString(CommonR.string.status_remove_torrent_failure),
            onSuccess = { _removed.emit(Unit) },
        ) {
            repository.removeTorrents(listOf(hash), deleteFiles)
        }
    }

    fun forceRecheck(hash: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rechecking_torrent),
            failureMessage = getString(CommonR.string.status_recheck_torrent_failure),
        ) {
            repository.recheckTorrents(listOf(hash))
        }
    }

    fun forceReannounce(hash: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_reannouncing_torrent),
            failureMessage = getString(CommonR.string.status_reannounce_torrent_failure),
        ) {
            repository.reannounceTorrents(listOf(hash))
        }
    }

    fun renameTorrent(torrentName: String, torrentHash: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_torrent_renamed),
            failureMessage = getString(CommonR.string.status_rename_torrent_failure),
        ) {
            repository.renameTorrent(torrentHash, torrentName)
        }
    }

    fun setCategory(category: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_category_set_to, category),
            failureMessage = getString(CommonR.string.status_set_category_failure),
        ) {
            repository.setTorrentCategory(listOf(requireNotNull(hash)), category)
        }
    }

    /** Create a new category on the server and assign it to this torrent. */
    fun createCategory(name: String, savePath: String = "") {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            when (val created = repository.createCategory(name, savePath)) {
                is Ok ->
                    when (val set = repository.setTorrentCategory(listOf(hash), name)) {
                        is Ok -> emitStatus(getString(CommonR.string.status_category_set_to, name))
                        is Err ->
                            emitStatus(
                                set.error.friendlyMessage(
                                    getString(CommonR.string.status_category_created_not_assigned)
                                )
                            )
                    }
                is Err ->
                    emitStatus(
                        created.error.friendlyMessage(
                            getString(CommonR.string.status_create_category_failure)
                        )
                    )
            }
        }
    }

    fun setSavePath(path: String) {
        val hash = requireNotNull(hash)
        launchStatus(
            successMessage = getString(CommonR.string.status_save_path_updated),
            failureMessage = getString(CommonR.string.status_set_save_path_failure),
        ) {
            repository.setAutoTorrentManagement(hash, false)
            repository.setTorrentLocation(hash, path)
        }
    }

    fun setAutoManagement(enabled: Boolean) {
        launchStatus(
            successMessage =
                if (enabled) getString(CommonR.string.status_auto_management_enabled)
                else getString(CommonR.string.status_auto_management_disabled),
            failureMessage = getString(CommonR.string.status_update_auto_management_failure),
        ) {
            repository.setAutoTorrentManagement(requireNotNull(hash), enabled)
        }
    }

    fun setTags(toAdd: List<String>, toRemove: List<String>) {
        viewModelScope.launch {
            val hash = requireNotNull(hash)
            if (toAdd.isNotEmpty()) repository.addTorrentTags(listOf(hash), toAdd)
            if (toRemove.isNotEmpty()) repository.removeTorrentTags(listOf(hash), toRemove)
            emitStatus(getString(CommonR.string.status_tags_updated))
        }
    }

    fun banPeer(peer: TorrentPeer) {
        val peerAddr = "${peer.ip}:${peer.port}"
        launchStatus(
            successMessage = getString(CommonR.string.status_peer_banned, peerAddr),
            failureMessage = getString(CommonR.string.status_ban_peer_failure),
        ) {
            repository.banPeers(listOf(peerAddr))
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

    /** Priorities: 0 = do not download, 1 = normal, 6 = high, 7 = maximal. */
    fun setFilePriority(ids: List<Int>, priority: Int) {
        val hash = requireNotNull(hash)
        viewModelScope.launch {
            when (val result = repository.setFilePriority(hash, ids, priority)) {
                is Ok -> {
                    // Update the tree immediately - qBittorrent applies priorities
                    // asynchronously, so an instant re-fetch can still return the old values.
                    _uiState.update { state ->
                        state.copy(
                            contentTree = withPriorities(state.contentTree, ids.toSet(), priority)
                        )
                    }
                    // Then reconcile with the server once it has settled.
                    delay(1000)
                    getContent()
                }
                is Err ->
                    emitStatus(
                        result.error.friendlyMessage(
                            getString(CommonR.string.status_set_priority_failure)
                        )
                    )
            }
        }
    }

    private fun withPriorities(
        nodes: List<ContentTreeItem>,
        ids: Set<Int>,
        priority: Int,
    ): List<ContentTreeItem> =
        nodes.map { node ->
            node.copy(
                item =
                    node.item?.let { file ->
                        if (file.index in ids) file.copy(priority = priority) else file
                    },
                children = node.children?.let { withPriorities(it, ids, priority) },
            )
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
