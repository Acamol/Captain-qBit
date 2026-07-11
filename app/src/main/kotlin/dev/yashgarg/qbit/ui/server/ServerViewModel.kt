package dev.yashgarg.qbit.ui.server

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.ui.common.StatusViewModel
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
    @ApplicationContext context: Context,
) : StatusViewModel(context) {
    private val _uiState = MutableStateFlow(ServerScreenState())
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
                            torrent.matchesSearch(state.searchQuery) &&
                                torrent.matchesFilter(state.selectedFilter) &&
                                torrent.matchesCategory(state.selectedCategory) &&
                                torrent.matchesTracker(state.selectedTracker) &&
                                torrent.matchesTags(state.selectedTags, state.filterUntagged)
                        }
                        .sortedWith(state.sortOption, state.sortDirection)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        launchStatus(
            successMessage = getString(CommonR.string.status_add_torrent_url_success),
            failureMessage = getString(CommonR.string.status_add_torrent_url_failure),
        ) {
            repository.addTorrentUrl(url, category, savePath, paused, autoTmm)
        }
    }

    fun addTorrentFile(
        bytes: ByteArray,
        category: String? = null,
        savePath: String? = null,
        paused: Boolean? = null,
        autoTmm: Boolean? = null,
    ) {
        launchStatus(
            successMessage = getString(CommonR.string.status_add_torrent_file_success),
            failureMessage = getString(CommonR.string.status_add_torrent_file_failure),
        ) {
            repository.addTorrentFile(bytes, category, savePath, paused, autoTmm)
        }
    }

    fun bulkSetCategory(hashes: List<String>, category: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_bulk_category_set, hashes.size),
            failureMessage = getString(CommonR.string.status_set_category_failure),
        ) {
            repository.setTorrentCategory(hashes, category)
        }
    }

    fun bulkAddTags(hashes: List<String>, tags: List<String>) {
        launchStatus(
            successMessage = getString(CommonR.string.status_bulk_tags_updated, hashes.size),
            failureMessage = getString(CommonR.string.status_update_tags_failure),
        ) {
            repository.addTorrentTags(hashes, tags)
        }
    }

    fun bulkRemoveTags(hashes: List<String>, tags: List<String>) {
        launchStatus(
            successMessage = getString(CommonR.string.status_bulk_tags_updated, hashes.size),
            failureMessage = getString(CommonR.string.status_update_tags_failure),
        ) {
            repository.removeTorrentTags(hashes, tags)
        }
    }

    fun createTag(name: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_tag_created, name),
            failureMessage = getString(CommonR.string.status_create_tag_failure),
        ) {
            repository.createTags(listOf(name))
        }
    }

    fun deleteTags(tags: List<String>) {
        launchStatus(
            successMessage = getString(CommonR.string.status_tags_deleted, tags.size),
            failureMessage = getString(CommonR.string.status_delete_tags_failure),
            onSuccess = {
                _uiState.update { it.copy(selectedTags = it.selectedTags - tags.toSet()) }
                saveFilterPrefs()
            },
        ) {
            repository.deleteTags(tags)
        }
    }

    fun createCategory(name: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_category_created, name),
            failureMessage = getString(CommonR.string.status_create_category_failure),
        ) {
            repository.createCategory(name)
        }
    }

    fun deleteCategories(names: List<String>) {
        launchStatus(
            successMessage = getString(CommonR.string.status_categories_deleted, names.size),
            failureMessage = getString(CommonR.string.status_delete_categories_failure),
        ) {
            repository.deleteCategories(names)
        }
    }

    fun editCategorySavePath(name: String, savePath: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_category_edited, name),
            failureMessage = getString(CommonR.string.status_edit_category_failure),
        ) {
            repository.editCategory(name, savePath)
        }
    }

    fun removeTorrents(hashes: List<String>, deleteFiles: Boolean = false) {
        launchStatus(
            successMessage = getString(CommonR.string.status_torrents_removed, hashes.size),
            failureMessage = getString(CommonR.string.status_remove_torrents_failure),
        ) {
            repository.removeTorrents(hashes, deleteFiles)
        }
    }

    fun toggleTorrentsState(pause: Boolean, hashes: List<String>) {
        launchStatus(
            successMessage =
                if (pause) getString(CommonR.string.status_torrents_paused, hashes.size)
                else getString(CommonR.string.status_torrents_resumed, hashes.size),
            failureMessage =
                if (pause) getString(CommonR.string.status_pause_torrents_failure, hashes.size)
                else getString(CommonR.string.status_resume_torrents_failure, hashes.size),
        ) {
            repository.toggleTorrentsState(hashes, pause)
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
                    emitStatus(
                        result.error.friendlyMessage(
                            getString(CommonR.string.status_toggle_speed_limits_failure)
                        )
                    )
            }
        }
    }

    private fun getSpeedLimitMode(showToast: Boolean = false) {
        viewModelScope.launch {
            when (val result = repository.getSpeedLimitMode()) {
                is Ok -> {
                    _uiState.update { it.copy(speedLimitMode = result.value) }
                    if (showToast) {
                        emitStatus(
                            getString(
                                if (result.value == 0) CommonR.string.status_speed_limits_disabled
                                else CommonR.string.status_speed_limits_enabled
                            )
                        )
                    }
                }
                is Err ->
                    emitStatus(
                        result.error.friendlyMessage(
                            getString(CommonR.string.status_get_speed_limit_mode_failure)
                        )
                    )
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
                            .mapNotNull { url -> Uri.parse(url).host?.takeIf { it.isNotBlank() } }
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
            is Err ->
                emitStatus(
                    result.error.message ?: getString(CommonR.string.status_sync_data_failure)
                )
        }
    }
}
