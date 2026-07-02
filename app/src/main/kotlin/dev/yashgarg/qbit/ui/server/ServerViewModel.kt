package dev.yashgarg.qbit.ui.server

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.utils.ExceptionHandler
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qbittorrent.models.Torrent

@HiltViewModel
class ServerViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    private val prefsStore: DataStore<ServerPreferences>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerState())
    val uiState = _uiState.asStateFlow()

    val addTorrentPrefs: StateFlow<ServerPreferences> =
        prefsStore.data.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServerPreferences()
        )

    fun saveAddTorrentPrefs(autoTmm: Boolean, paused: Boolean) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(addTorrentAutoTmm = autoTmm, addTorrentPaused = paused)
            }
        }
    }

    val sortedTorrents: StateFlow<List<Torrent>?> =
        _uiState
            .map { state ->
                if (state.dataLoading || state.hasError || state.data == null) null
                else
                    state.data.torrents.values
                        .filter { torrent ->
                            val matchesSearch =
                                state.searchQuery.isBlank() ||
                                    torrent.name.contains(state.searchQuery, ignoreCase = true)
                            val matchesFilter = torrent.matchesFilter(state.selectedFilter)
                            val matchesCategory =
                                state.selectedCategory == null ||
                                    torrent.category == state.selectedCategory
                            val matchesTracker =
                                state.selectedTracker == null ||
                                    torrent.tracker.contains(
                                        state.selectedTracker,
                                        ignoreCase = true,
                                    )
                            val matchesTags =
                                state.selectedTags.isEmpty() ||
                                    state.selectedTags.any { tag -> torrent.tags.contains(tag) }
                            matchesSearch &&
                                matchesFilter &&
                                matchesCategory &&
                                matchesTracker &&
                                matchesTags
                        }
                        .sortedWith(state.sortOption, state.sortDirection)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _status = MutableSharedFlow<String>()
    val status = _status.asSharedFlow()

    private val _intent = MutableSharedFlow<Unit>()
    val intent = _intent.asSharedFlow()

    private var syncJob: Job? = null

    init {
        syncJob =
            viewModelScope.launch {
                val prefs = prefsStore.data.first()
                val option =
                    try {
                        SortOption.valueOf(prefs.sortOptionName)
                    } catch (e: Exception) {
                        SortOption.NAME
                    }
                val direction =
                    if (prefs.sortDirectionAsc) SortDirection.ASC else SortDirection.DESC
                _uiState.update { it.copy(sortOption = option, sortDirection = direction) }
                syncData()
            }
    }

    private fun saveSortPrefs(option: SortOption, direction: SortDirection) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(
                    sortOptionName = option.name,
                    sortDirectionAsc = direction == SortDirection.ASC
                )
            }
        }
    }

    fun refresh() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch { syncData() }
    }

    fun addTorrentUrl(
        url: String,
        category: String? = null,
        savePath: String? = null,
        paused: Boolean? = null,
        autoTmm: Boolean? = null,
    ) {
        viewModelScope.launch {
            when (val result = repository.addTorrentUrl(url, category, savePath, paused, autoTmm)) {
                is Ok -> _status.emit("Successfully added torrent")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to add torrent"))
            }
        }
    }

    fun addTorrentFile(
        bytes: ByteArray,
        category: String? = null,
        savePath: String? = null,
        paused: Boolean? = null,
        autoTmm: Boolean? = null,
    ) {
        viewModelScope.launch {
            when (
                val result = repository.addTorrentFile(bytes, category, savePath, paused, autoTmm)
            ) {
                is Ok -> _status.emit("Successfully added file")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to add file"))
            }
        }
    }

    fun removeTorrents(hashes: List<String>, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            when (val result = repository.removeTorrents(hashes, deleteFiles)) {
                is Ok -> _status.emit("Successfully deleted ${hashes.size} file(s)")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to remove"))
            }
        }
    }

    fun toggleTorrentsState(pause: Boolean, hashes: List<String>) {
        viewModelScope.launch {
            when (val result = repository.toggleTorrentsState(hashes, pause)) {
                is Ok ->
                    _status.emit("${if (pause) "Paused" else "Resumed"} ${hashes.size} torrent(s)")
                is Err ->
                    _status.emit(
                        result.error.friendlyMessage(
                            "Failed to ${if (pause) "pause" else "resume"} ${hashes.size} torrent(s)"
                        )
                    )
            }
        }
    }

    fun setSort(option: SortOption) {
        val newState =
            _uiState.updateAndGet { state ->
                val newDirection =
                    if (state.sortOption == option) {
                        if (state.sortDirection == SortDirection.ASC) SortDirection.DESC
                        else SortDirection.ASC
                    } else {
                        SortDirection.ASC
                    }
                state.copy(sortOption = option, sortDirection = newDirection)
            }
        saveSortPrefs(newState.sortOption, newState.sortDirection)
    }

    fun toggleSortDirection() {
        val newState =
            _uiState.updateAndGet { state ->
                state.copy(
                    sortDirection =
                        if (state.sortDirection == SortDirection.ASC) SortDirection.DESC
                        else SortDirection.ASC
                )
            }
        saveSortPrefs(newState.sortOption, newState.sortDirection)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query) }
    }

    fun setCategory(category: String?) {
        _uiState.update { state -> state.copy(selectedCategory = category) }
    }

    fun setFilter(filter: StateFilter) {
        _uiState.update { state -> state.copy(selectedFilter = filter) }
    }

    fun setTracker(tracker: String?) {
        _uiState.update { state -> state.copy(selectedTracker = tracker) }
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val tags = state.selectedTags.toMutableSet()
            if (tags.contains(tag)) tags.remove(tag) else tags.add(tag)
            state.copy(selectedTags = tags)
        }
    }

    fun clearFilters() {
        _uiState.update { state ->
            state.copy(
                selectedCategory = null,
                selectedFilter = StateFilter.ALL,
                selectedTracker = null,
                selectedTags = emptySet(),
            )
        }
    }

    fun toggleSpeedLimits() {
        viewModelScope.launch {
            when (val result = repository.toggleSpeedLimitsMode()) {
                is Ok -> getSpeedLimitMode(true)
                is Err ->
                    _status.emit(result.error.friendlyMessage("Failed to toggle speed limits"))
            }
        }
    }

    private fun getSpeedLimitMode(showToast: Boolean = false) {
        viewModelScope.launch {
            when (val result = repository.getSpeedLimitMode()) {
                is Ok -> {
                    _uiState.update { it.copy(speedLimitMode = result.value) }
                    if (showToast) {
                        _status.emit(
                            "Alternative speed limits are ${if (result.value == 0) "disabled" else "enabled"}"
                        )
                    }
                }
                is Err ->
                    _status.emit(result.error.friendlyMessage("Failed to get speed limit mode"))
            }
        }
    }

    private suspend fun syncData() {
        getSpeedLimitMode()
        val result = runCatching {
            repository
                .observeMainData()
                .catch { e ->
                    val error = ExceptionHandler.mapException(e)
                    _uiState.update { state ->
                        state.copy(hasError = true, error = error, data = null)
                    }
                }
                .collectLatest { mainData ->
                    val categories = mainData.categories.keys.sorted()
                    val trackers =
                        mainData.torrents.values
                            .mapNotNull { t -> t.tracker.takeIf { it.isNotBlank() } }
                            .distinct()
                            .sorted()
                    val tags = mainData.tags.sorted()
                    _uiState.update { state ->
                        state.copy(
                            dataLoading = false,
                            data = mainData,
                            hasError = false,
                            error = null,
                            availableCategories = categories,
                            availableTrackers = trackers,
                            availableTags = tags,
                        )
                    }
                    _intent.emit(Unit)
                }
        }

        when (result) {
            is Ok -> Unit
            is Err -> _status.emit(result.error.message ?: "Failed to sync data")
        }
    }
}
