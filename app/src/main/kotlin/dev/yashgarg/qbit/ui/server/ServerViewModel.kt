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
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.ui.common.StatusViewModel
import dev.yashgarg.qbit.utils.TorrentFileParser
import dev.yashgarg.qbit.utils.TransformUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile

@HiltViewModel
class ServerViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    private val prefsStore: DataStore<ServerPreferences>,
    private val configDao: ConfigDao,
    private val clientManager: ClientManager,
    @ApplicationContext context: Context,
) : StatusViewModel(context) {
    private val _uiState = MutableStateFlow(ServerScreenState())
    val uiState = _uiState.asStateFlow()

    /** All saved servers, for the drawer switcher. */
    val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeServerId: StateFlow<Int> =
        prefsStore.data
            .map { it.activeServerId }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    /** User-picked colors per category (app-local; qBittorrent has no category color). */
    val categoryColors: StateFlow<Map<String, Int>> =
        prefsStore.data
            .map { it.categoryColors }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Sets (or clears, when [color] is null) the app-local color for a category. */
    fun setCategoryColor(category: String, color: Int?) {
        viewModelScope.launch {
            prefsStore.updateData { prefs ->
                prefs.copy(
                    categoryColors =
                        if (color == null) prefs.categoryColors - category
                        else prefs.categoryColors + (category to color)
                )
            }
        }
    }

    fun switchServer(id: Int) {
        if (id == activeServerId.value) return
        viewModelScope.launch { clientManager.setActiveServer(id) }
    }

    fun deleteServer(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configDao.getConfigs().first().size <= 1) {
                emitStatus(getString(CommonR.string.status_cannot_delete_last_server))
                return@launch
            }
            configDao.deleteConfig(id)
            if (id == prefsStore.data.first().activeServerId) {
                val remaining = configDao.getConfigs().first().firstOrNull()
                clientManager.setActiveServer(remaining?.configId ?: -1)
            }
        }
    }

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

        // Reflect the active server's name in the UI.
        viewModelScope.launch {
            combine(
                    configDao.getConfigs(),
                    prefsStore.data.map { it.activeServerId }.distinctUntilChanged(),
                ) { configs, activeId ->
                    configs.find { it.configId == activeId } ?: configs.firstOrNull()
                }
                .collect { active -> _uiState.update { it.copy(serverName = active?.serverName) } }
        }

        // On a real server switch, drop the current sync + filters and repopulate for the new
        // server (per-server prefs aren't stored yet, so each server starts from a clean slate).
        viewModelScope.launch {
            prefsStore.data
                .map { it.activeServerId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    syncJob?.cancel()
                    _uiState.update { state ->
                        state.copy(
                            dataLoading = true,
                            data = null,
                            selectedFilter = StateFilter.ALL,
                            selectedCategory = null,
                            selectedTracker = null,
                            selectedTags = emptySet(),
                            filterUntagged = false,
                            searchQuery = "",
                        )
                    }
                    saveFilterPrefs()
                    syncJob = viewModelScope.launch { syncData() }
                }
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

    // ---- Add-time file selection -------------------------------------------------------------
    // The add API can't take file selections up front. The add screen's Files tab "prepares" a
    // source (parsing a .torrent locally, or adding a magnet stopped and polling for metadata),
    // and confirmAdd() then applies options + "do not download" priorities and starts the
    // torrent unless the user chose paused.

    private var pendingSource: PendingSource? = null
    /** Set when a magnet was already added (stopped) to fetch its metadata. */
    private var addedStoppedHash: String? = null
    private var metadataJob: Job? = null

    private val _fileSelection = MutableStateFlow<FileSelectionUiState?>(null)
    /** Non-null while a prepared source's tree (or its loading state) should be showing. */
    val fileSelection = _fileSelection.asStateFlow()

    /** True when confirmAdd() will consume a prepared source instead of doing a plain add. */
    val hasPendingSelection: Boolean
        get() = pendingSource != null

    /**
     * Parses a `.torrent` locally and exposes its tree — nothing is sent to the server until
     * [confirmAdd]. Returns false when the file can't be parsed (v2-only or invalid).
     */
    fun prepareTorrentFileSelection(bytes: ByteArray): Boolean {
        val parsed = TorrentFileParser.parse(bytes) ?: return false
        cancelFileSelection()
        val synthetic =
            parsed.files.mapIndexed { i, file ->
                TorrentFile(
                    index = i,
                    name = file.path,
                    size = file.size,
                    progress = 0f,
                    priority = 1,
                    pieceRange = emptyList(),
                    availability = 0f,
                )
            }
        pendingSource = PendingSource.TorrentBytes(bytes, parsed)
        _fileSelection.value =
            FileSelectionUiState(parsed.name, TransformUtil.transformFilesToTree(synthetic, 0))
        return true
    }

    /**
     * Adds the magnet stopped right away and polls for its file list (metadata must arrive from
     * peers before a tree exists). Safe to call repeatedly with the same url. Returns false when
     * the link carries no v1 infohash.
     */
    fun prepareMagnetSelection(url: String): Boolean {
        (pendingSource as? PendingSource.Magnet)?.let { if (it.url == url) return true }
        val hash = TorrentFileParser.magnetHash(url) ?: return false
        cancelFileSelection()
        pendingSource = PendingSource.Magnet(url, hash)
        _fileSelection.value = FileSelectionUiState(magnetDisplayName(url), null)

        metadataJob =
            viewModelScope.launch {
                // Must be added RUNNING: libtorrent won't fetch metadata for a stopped magnet.
                when (val add = repository.addTorrentUrl(url, paused = false)) {
                    is Ok -> {
                        addedStoppedHash = hash
                        var attempts = 0
                        while (isActive) {
                            val files = repository.getTorrentFiles(hash)
                            if (files is Ok && files.value.isNotEmpty()) {
                                // Got the file list: stop it again so nothing downloads while the
                                // user picks. Metadata stays cached; confirm/apply will resume it.
                                repository.toggleTorrentsState(listOf(hash), pause = true)
                                _fileSelection.update { current ->
                                    current?.copy(
                                        tree = TransformUtil.transformFilesToTree(files.value, 0)
                                    )
                                }
                                break
                            }
                            if (++attempts >= METADATA_MAX_ATTEMPTS) {
                                repository.toggleTorrentsState(listOf(hash), pause = true)
                                emitStatus(getString(CommonR.string.metadata_timeout))
                                break
                            }
                            delay(METADATA_POLL_MS)
                        }
                    }
                    is Err -> {
                        emitStatus(
                            add.error.friendlyMessage(
                                getString(CommonR.string.status_add_torrent_url_failure)
                            )
                        )
                        clearFileSelection()
                    }
                }
            }
        return true
    }

    /**
     * Commits a prepared source with the add screen's options. [deselected] holds the file indices
     * NOT to download.
     */
    fun confirmAdd(
        deselected: Set<Int>,
        category: String?,
        savePath: String?,
        paused: Boolean,
        autoTmm: Boolean,
    ) {
        val source = pendingSource ?: return
        metadataJob?.cancel()
        clearFileSelection()

        viewModelScope.launch {
            when (source) {
                is PendingSource.TorrentBytes -> {
                    val add =
                        repository.addTorrentFile(
                            source.bytes,
                            category,
                            savePath,
                            paused = true,
                            autoTmm = autoTmm.takeIf { it },
                        )
                    if (add is Err) {
                        emitStatus(
                            add.error.friendlyMessage(
                                getString(CommonR.string.status_add_torrent_file_failure)
                            )
                        )
                        return@launch
                    }
                    val hash = source.parsed.hash
                    val server = awaitTorrentFiles(hash)
                    if (server == null) {
                        emitStatus(getString(CommonR.string.status_file_selection_failed))
                        return@launch
                    }
                    applySelectionAndStart(
                        hash,
                        mapDeselectedToServer(source.parsed, deselected, server),
                        paused,
                    )
                }
                is PendingSource.Magnet -> {
                    // Already added (stopped) during prepare; apply the options after the fact.
                    val hash = source.hash
                    if (category != null) repository.setTorrentCategory(listOf(hash), category)
                    if (autoTmm) {
                        repository.setAutoTorrentManagement(hash, true)
                    } else if (savePath != null) {
                        repository.setTorrentLocation(hash, savePath)
                    }
                    // Indices came from the server's own file list, so they map directly.
                    applySelectionAndStart(hash, deselected.toList(), paused)
                }
            }
        }
    }

    /** Cancels the flow; a magnet torrent that was already added (stopped) is removed again. */
    fun cancelFileSelection() {
        metadataJob?.cancel()
        metadataJob = null
        val addedHash = addedStoppedHash
        clearFileSelection()
        if (addedHash != null) {
            viewModelScope.launch { repository.removeTorrents(listOf(addedHash)) }
        }
    }

    private fun clearFileSelection() {
        pendingSource = null
        addedStoppedHash = null
        _fileSelection.value = null
    }

    private suspend fun awaitTorrentFiles(hash: String): List<TorrentFile>? {
        repeat(FILE_POLL_ATTEMPTS) {
            val files = repository.getTorrentFiles(hash)
            if (files is Ok && files.value.isNotEmpty()) return files.value
            delay(FILE_POLL_MS)
        }
        return null
    }

    // Match by path (the server's file names mirror the .torrent's), falling back to position.
    private fun mapDeselectedToServer(
        parsed: TorrentFileParser.ParsedTorrent,
        deselected: Set<Int>,
        server: List<TorrentFile>,
    ): List<Int> =
        deselected.mapNotNull { i ->
            val path = parsed.files.getOrNull(i)?.path ?: return@mapNotNull null
            server.firstOrNull { it.name == path || it.name.endsWith("/$path") }?.index
                ?: server.getOrNull(i)?.index
        }

    private suspend fun applySelectionAndStart(
        hash: String,
        deselectedIds: List<Int>,
        paused: Boolean,
    ) {
        if (deselectedIds.isNotEmpty()) {
            // Right after an add the torrent may briefly be checking, which rejects priority
            // changes - retry a few times before giving up.
            var applied = false
            repeat(PRIORITY_RETRY_ATTEMPTS) {
                if (!applied && repository.setFilePriority(hash, deselectedIds, 0) is Ok) {
                    applied = true
                }
                if (!applied) delay(PRIORITY_RETRY_MS)
            }
            if (!applied) emitStatus(getString(CommonR.string.status_file_selection_failed))
        }
        if (!paused) repository.toggleTorrentsState(listOf(hash), pause = false)
        emitStatus(getString(CommonR.string.status_add_torrent_file_success))
    }

    private fun magnetDisplayName(url: String): String {
        val encoded = Regex("[?&]dn=([^&]+)").find(url)?.groupValues?.get(1) ?: return ""
        return try {
            java.net.URLDecoder.decode(encoded.replace("+", "%20"), "UTF-8")
        } catch (e: Exception) {
            ""
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
                    _uiState.update { state -> state.copy(hasError = true, error = e, data = null) }
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
                    result.error.friendlyMessage(getString(CommonR.string.status_sync_data_failure))
                )
        }
    }
}

/** Tree + name shown by the add-time file selection dialog; [tree] is null while metadata loads. */
data class FileSelectionUiState(
    val torrentName: String,
    val tree: List<ContentTreeItem>?,
)

private sealed interface PendingSource {
    data class TorrentBytes(
        val bytes: ByteArray,
        val parsed: TorrentFileParser.ParsedTorrent,
    ) : PendingSource

    data class Magnet(val url: String, val hash: String) : PendingSource
}

private const val METADATA_POLL_MS = 1500L
private const val METADATA_MAX_ATTEMPTS = 80
private const val FILE_POLL_ATTEMPTS = 20
private const val FILE_POLL_MS = 500L
private const val PRIORITY_RETRY_ATTEMPTS = 4
private const val PRIORITY_RETRY_MS = 750L
