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

    // Eagerly so the DataStore-backed flow starts collecting as soon as the ViewModel is created;
    // the add-torrent dialog reads .value directly (no long-lived collector), and WhileSubscribed
    // would leave it stuck on the default until something subscribed.
    val addTorrentPrefs: StateFlow<ServerPreferences> =
        prefsStore.data.stateIn(viewModelScope, SharingStarted.Eagerly, ServerPreferences())

    fun saveAddTorrentPrefs(autoTmm: Boolean, paused: Boolean) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(addTorrentAutoTmm = autoTmm, addTorrentPaused = paused)
            }
        }
    }

    fun saveDefaultCategory(category: String?) {
        viewModelScope.launch {
            prefsStore.updateData { it.copy(addTorrentCategory = category.orEmpty()) }
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
                                    torrent.category == state.selectedCategory ||
                                    torrent.category.startsWith("${state.selectedCategory}/")
                            val matchesTracker =
                                state.selectedTracker == null ||
                                    torrent.tracker.contains(
                                        state.selectedTracker,
                                        ignoreCase = true,
                                    )
                            val matchesTags =
                                when {
                                    state.filterUntagged -> torrent.tags.isEmpty()
                                    state.selectedTags.isEmpty() -> true
                                    else ->
                                        state.selectedTags.any { tag -> torrent.tags.contains(tag) }
                                }
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
                val filter =
                    try {
                        StateFilter.valueOf(prefs.filterStateName)
                    } catch (e: Exception) {
                        StateFilter.ALL
                    }
                _uiState.update {
                    it.copy(
                        sortOption = option,
                        sortDirection = direction,
                        selectedFilter = filter,
                        selectedCategory = prefs.filterCategory,
                        selectedTracker = prefs.filterTracker,
                        selectedTags = prefs.filterTags,
                        filterUntagged = prefs.filterUntagged,
                    )
                }
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

    private fun saveFilterPrefs() {
        val state = _uiState.value
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(
                    filterStateName = state.selectedFilter.name,
                    filterCategory = state.selectedCategory,
                    filterTracker = state.selectedTracker,
                    filterTags = state.selectedTags,
                    filterUntagged = state.filterUntagged,
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

    fun bulkSetCategory(hashes: List<String>, category: String) {
        viewModelScope.launch {
            when (val result = repository.setTorrentCategory(hashes, category)) {
                is Ok -> _status.emit("Category set on ${hashes.size} torrent(s)")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to set category"))
            }
        }
    }

    fun bulkAddTags(hashes: List<String>, tags: List<String>) {
        viewModelScope.launch {
            when (val result = repository.addTorrentTags(hashes, tags)) {
                is Ok -> _status.emit("Tags updated on ${hashes.size} torrent(s)")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to update tags"))
            }
        }
    }

    fun bulkRemoveTags(hashes: List<String>, tags: List<String>) {
        viewModelScope.launch {
            when (val result = repository.removeTorrentTags(hashes, tags)) {
                is Ok -> _status.emit("Tags updated on ${hashes.size} torrent(s)")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to update tags"))
            }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            when (val result = repository.createTags(listOf(name))) {
                is Ok -> _status.emit("Tag \"$name\" created")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to create tag"))
            }
        }
    }

    fun deleteTags(tags: List<String>) {
        viewModelScope.launch {
            when (val result = repository.deleteTags(tags)) {
                is Ok -> {
                    _uiState.update { it.copy(selectedTags = it.selectedTags - tags.toSet()) }
                    saveFilterPrefs()
                    _status.emit("Deleted ${tags.size} tag(s)")
                }
                is Err -> _status.emit(result.error.friendlyMessage("Failed to delete tags"))
            }
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (val result = repository.createCategory(name)) {
                is Ok -> _status.emit("Category \"$name\" created")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to create category"))
            }
        }
    }

    fun deleteCategories(names: List<String>) {
        viewModelScope.launch {
            when (val result = repository.deleteCategories(names)) {
                is Ok -> _status.emit("Deleted ${names.size} category(ies)")
                is Err -> _status.emit(result.error.friendlyMessage("Failed to delete categories"))
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
        saveFilterPrefs()
    }

    fun setFilter(filter: StateFilter) {
        _uiState.update { state -> state.copy(selectedFilter = filter) }
        saveFilterPrefs()
    }

    fun setTracker(tracker: String?) {
        _uiState.update { state -> state.copy(selectedTracker = tracker) }
        saveFilterPrefs()
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val tags = state.selectedTags.toMutableSet()
            if (tags.contains(tag)) tags.remove(tag) else tags.add(tag)
            state.copy(selectedTags = tags, filterUntagged = false)
        }
        saveFilterPrefs()
    }

    fun setFilterUntagged(untagged: Boolean) {
        _uiState.update { it.copy(filterUntagged = untagged, selectedTags = emptySet()) }
        saveFilterPrefs()
    }

    fun clearFilters() {
        _uiState.update { state ->
            state.copy(
                selectedCategory = null,
                selectedFilter = StateFilter.ALL,
                selectedTracker = null,
                selectedTags = emptySet(),
                filterUntagged = false,
            )
        }
        saveFilterPrefs()
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
                    val tags =
                        (mainData.tags + mainData.torrents.values.flatMap { it.tags })
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
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
