package dev.yashgarg.qbit.ui.server

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.CertificateProbe
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.PendingTorrentIntent
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.data.models.OwnAdds
import dev.yashgarg.qbit.data.models.OwnAdds.registered
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerViewPrefs
import dev.yashgarg.qbit.data.models.activeServer
import dev.yashgarg.qbit.ui.common.StatusViewModel
import dev.yashgarg.qbit.utils.TorrentDownloader
import dev.yashgarg.qbit.utils.TorrentFileParser
import dev.yashgarg.qbit.utils.TransformUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile

@HiltViewModel
class ServerViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    private val prefsStore: DataStore<AppPreferences>,
    private val configDao: ConfigDao,
    private val clientManager: ClientManager,
    private val pendingTorrentIntent: PendingTorrentIntent,
    application: Application,
) : StatusViewModel(application) {

    // Close filters on tab re-entry, but preserve an open sheet across configuration changes.
    var closeFiltersOnEntry = true
    private val _uiState = MutableStateFlow(ServerScreenState())
    val uiState = _uiState.asStateFlow()

    /** A torrent-file/magnet URI handed off by [MainActivity][dev.yashgarg.qbit.MainActivity]. */
    val pendingTorrentUri: StateFlow<String?> = pendingTorrentIntent.uri

    fun consumePendingTorrentUri() = pendingTorrentIntent.consume()

    /** All saved servers, for the server switcher. */
    val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The id of the server the app is connected to, or -1 when there is none. */
    val activeServerId: StateFlow<Int> =
        combine(configDao.getConfigs(), prefsStore.data.map { it.activeServerId }) { configs, id ->
                configs.activeServer(id)?.configId ?: -1
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    /** User-picked colors per category (app-local; qBittorrent has no category color). */
    val categoryColors: StateFlow<Map<String, Int>> =
        prefsStore.data
            .map { it.categoryColors }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val compactTorrentList: StateFlow<Boolean> =
        prefsStore.data
            .map { it.compactTorrentList }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences().compactTorrentList)

    fun setCompactTorrentList(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(compactTorrentList = enabled) } }
    }

    /** Sets (or clears, when [color] is null) the app-local color for a category. */
    fun setCategoryColor(category: String, color: Int?) {
        viewModelScope.launch {
            prefsStore.updateData { prefs ->
                prefs.copy(
                    categoryColors =
                        if (color == null) prefs.categoryColors - category
                        else prefs.categoryColors + (category to color),
                )
            }
        }
    }

    fun switchServer(id: Int) {
        if (id == activeServerId.value) return
        viewModelScope.launch { clientManager.setActiveServer(id) }
    }

    fun deleteServer(id: Int) {
        viewModelScope.launch {
            if (!clientManager.deleteServer(id)) {
                emitStatus(getString(CommonR.string.status_cannot_delete_last_server))
            }
        }
    }

    /** Fetches the certificate a server presents, for the user to review before trusting it. */
    internal suspend fun probeCertificate(
        target: CertificateTarget,
    ): Result<CertificateReview, Throwable> =
        CertificateProbe.fetchPresentedCertificate(target.host, target.port ?: 443).map {
            CertificateReview(target, it)
        }

    internal suspend fun pinCertificate(review: CertificateReview) {
        withContext(Dispatchers.IO) {
            val active =
                configDao.getConfigs().first().activeServer(prefsStore.data.first().activeServerId)
            review.pinIfCurrent(active, configDao)
        }
    }

    // Eagerly so the DataStore-backed flow starts collecting as soon as the ViewModel is created;
    // the add-torrent dialog reads .value directly (no long-lived collector), and WhileSubscribed
    // would leave it stuck on the default until something subscribed.
    val addTorrentPrefs: StateFlow<AppPreferences> =
        prefsStore.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

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
                        .filter(state::includes)
                        .sortedWith(state.sortOption, state.sortDirection)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _intent = MutableSharedFlow<Unit>()
    val intent = _intent.asSharedFlow()

    private val refreshRequests = MutableStateFlow(0L)
    private var displayedRepository: QbitRepository? = null

    init {
        viewModelScope.launch {
            clientManager.configStatus.collectLatest { status ->
                _uiState.update {
                    it.copy(
                        workspaceRevision = it.workspaceRevision + 1,
                        dataLoading = true,
                        data = null,
                        error = null,
                        hasError = false,
                        availableTags = emptyList(),
                        availableCategories = emptyList(),
                        availableTrackers = emptyList(),
                        trackerCounts = emptyMap(),
                        queueingEnabled = false,
                    )
                }
                if (status == ConfigStatus.EXISTS) {
                    syncWhileForeground { refreshRequests.collectLatest { syncData() } }
                }
            }
        }

        // Reflect the active server's name in the UI.
        viewModelScope.launch {
            combine(
                    configDao.getConfigs(),
                    prefsStore.data.map { it.activeServerId }.distinctUntilChanged(),
                ) { configs, activeId ->
                    configs.activeServer(activeId)
                }
                .collect { active -> _uiState.update { it.copy(serverName = active?.serverName) } }
        }

        // Restoring the view is separate from following client replacement: an interval or
        // credential edit needs a new collector, but must not reset the user's search or filters.
        viewModelScope.launch {
            prefsStore.data
                .distinctUntilChangedBy { it.activeServerId }
                .collect { prefs ->
                    _uiState.update { it.copy(searchQuery = "") }
                    applyViewPrefs(prefs, prefs.activeServerId)
                }
        }
    }

    /**
     * Restore [serverId]'s saved filters + sort into the UI state. Servers without a per-server
     * entry yet start from the defaults.
     */
    private fun applyViewPrefs(prefs: AppPreferences, serverId: Int) {
        val v = prefs.serverViewPrefs[serverId] ?: ServerViewPrefs()
        val option =
            try {
                SortOption.valueOf(v.sortOptionName)
            } catch (e: Exception) {
                SortOption.NAME
            }
        val filter =
            try {
                StateFilter.valueOf(v.filterStateName)
            } catch (e: Exception) {
                StateFilter.ALL
            }
        _uiState.update { state ->
            state.copy(
                sortOption = option,
                sortDirection = if (v.sortDirectionAsc) SortDirection.ASC else SortDirection.DESC,
                selectedFilter = filter,
                selectedCategory = v.filterCategory,
                selectedTracker = v.filterTracker,
                selectedTags = v.filterTags,
                filterUntagged = v.filterUntagged,
            )
        }
    }

    /** Persist the current filters + sort against the active server id. */
    private fun persistViewPrefs() {
        val s = _uiState.value
        viewModelScope.launch {
            prefsStore.updateData { prefs ->
                val entry =
                    ServerViewPrefs(
                        sortOptionName = s.sortOption.name,
                        sortDirectionAsc = s.sortDirection == SortDirection.ASC,
                        filterStateName = s.selectedFilter.name,
                        filterCategory = s.selectedCategory,
                        filterTracker = s.selectedTracker,
                        filterTags = s.selectedTags,
                        filterUntagged = s.filterUntagged,
                    )
                prefs.copy(
                    serverViewPrefs = prefs.serverViewPrefs + (prefs.activeServerId to entry),
                )
            }
        }
    }

    fun refresh() {
        refreshRequests.update { it + 1 }
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
            val magnetHash = TorrentFileParser.magnetHash(url)
            if (magnetHash == null && skipOwnAddsActive()) {
                // A plain .torrent link has no hash to read off the URL, so fetch it here to learn
                // the hash, then upload those bytes (a single fetch, so single-use links survive).
                // If the phone can't fetch or parse it, fall through to a normal server-side add;
                // that add simply isn't suppressed.
                val bytes = withContext(Dispatchers.IO) { TorrentDownloader.fetch(url) }
                val parsed = bytes?.let(TorrentFileParser::parse)
                if (bytes != null && parsed != null) {
                    val ownKey = rememberOwnAdd(parsed.hash)
                    return@launchStatus repository
                        .addTorrentFile(bytes, category, savePath, paused, autoTmm)
                        .onErr { forgetOwnAdd(ownKey) }
                }
            }
            val ownKey = rememberOwnAdd(magnetHash)
            repository.addTorrentUrl(url, category, savePath, paused, autoTmm).onErr {
                forgetOwnAdd(ownKey)
            }
        }
    }

    /**
     * Records a torrent this app is about to add so the "torrent added" alert skips it. Done before
     * the add request, since the background worker could otherwise poll in between. A no-op unless
     * that alert is on, so nothing accumulates for users who don't use it.
     */
    private suspend fun rememberOwnAdd(hash: String?): String? {
        if (hash == null) return null
        var recorded: String? = null
        val now = System.currentTimeMillis()
        prefsStore.updateData {
            if (!it.notifyOnAdded || !it.notifySkipOwnAdds) return@updateData it
            val key = OwnAdds.key(it.activeServerId, hash)
            recorded = key
            it.copy(notifOwnAdds = it.notifOwnAdds.registered(key, now))
        }
        return recorded
    }

    private suspend fun skipOwnAddsActive(): Boolean =
        prefsStore.data.first().let { it.notifyOnAdded && it.notifySkipOwnAdds }

    /** Undoes [rememberOwnAdd] when the add request failed, so it can't suppress a later alert. */
    private suspend fun forgetOwnAdd(key: String?) {
        if (key == null) return
        prefsStore.updateData { it.copy(notifOwnAdds = it.notifOwnAdds - key) }
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
            TorrentFileParser.parse(bytes)?.let { rememberOwnAdd(it.hash) }
            repository.addTorrentFile(bytes, category, savePath, paused, autoTmm)
        }
    }

    // ---- Add-time file selection -------------------------------------------------------------
    // The add API can't take file selections up front. The add screen's Files tab "prepares" a
    // source (parsing a .torrent locally, or adding a magnet and polling for metadata),
    // and confirmAdd() then applies options + "do not download" priorities and starts the
    // torrent unless the user chose paused.

    private var pendingSource: PendingSource? = null
    private var metadataJob: Job? = null
    // A confirm/cancel must wait for the in-flight add, not cancel it or assume it succeeded.
    private val torrentAddMutex = Mutex()
    private val _confirmingAdd = MutableStateFlow(false)
    val confirmingAdd = _confirmingAdd.asStateFlow()
    private val _addConfirmed = MutableStateFlow(false)
    val addConfirmed = _addConfirmed.asStateFlow()

    fun consumeAddConfirmation() {
        _addConfirmed.value = false
    }

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
        if (_confirmingAdd.value) return false
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
     * Adds the magnet running for metadata, then stops it before showing its file list. Safe to
     * call repeatedly with the same URL. Returns false for links without a v1 infohash.
     */
    fun prepareMagnetSelection(url: String): Boolean {
        if (_confirmingAdd.value) return false
        (pendingSource as? PendingSource.Magnet)?.let { if (it.url == url) return true }
        val hash = TorrentFileParser.magnetHash(url) ?: return false
        cancelFileSelection()
        val source = PendingSource.Magnet(url, hash)
        pendingSource = source
        _fileSelection.value = FileSelectionUiState(magnetDisplayName(url), null)

        metadataJob = viewModelScope.launch {
            val added = torrentAddMutex.withLock {
                if (pendingSource !== source) return@launch
                addPendingTorrent(source, AddTorrentOptions(), paused = false).also {
                    source.previewResult = it
                }
            }
            val session = added.get()
            if (session == null) {
                added.onErr { showMetadataError(source, addFailure(it, source)) }
                return@launch
            }
            val files = awaitTorrentFiles(session, METADATA_MAX_ATTEMPTS, METADATA_POLL_MS)
            torrentAddMutex.withLock {
                if (pendingSource !== source || _confirmingAdd.value) return@withLock
                session
                    .stop()
                    .onOk {
                        if (files == null) {
                            showMetadataError(source, getString(CommonR.string.metadata_timeout))
                        } else {
                            _fileSelection.update {
                                it?.copy(tree = TransformUtil.transformFilesToTree(files, 0))
                            }
                        }
                    }
                    .onErr { showMetadataError(source, addFailure(it, source)) }
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
        if (_confirmingAdd.value) return
        val waitingForPreview = source is PendingSource.Magnet && source.previewResult == null
        _confirmingAdd.value = true
        _fileSelection.update { it?.copy(confirmationError = null) }
        source.confirmed = true

        viewModelScope.launch {
            try {
                val options = AddTorrentOptions(category, savePath, autoTmm)
                val result = torrentAddMutex.withLock {
                    metadataJob?.cancel()
                    metadataJob = null
                    val previewError =
                        if (waitingForPreview && source is PendingSource.Magnet)
                            source.previewResult?.getError()
                        else null
                    if (previewError != null) Err(previewError)
                    else finishPendingAdd(source, deselected, options, paused)
                }
                result
                    .onOk {
                        clearFileSelection()
                        _addConfirmed.value = true
                        emitStatus(it)
                    }
                    .onErr {
                        _fileSelection.update { selection ->
                            selection?.copy(confirmationError = addFailure(it, source))
                        }
                    }
            } finally {
                _confirmingAdd.value = false
            }
        }
    }

    private suspend fun addPendingTorrent(
        source: PendingSource,
        options: AddTorrentOptions,
        paused: Boolean,
    ): Result<TorrentAddSession, Throwable> {
        val session =
            source.session
                ?: run {
                    val bound =
                        repository.forCurrentClient()
                            ?: return Err(IllegalStateException("No active qBittorrent client"))
                    TorrentAddSession(bound, source.hash).also { source.session = it }
                }
        return session
            .add { tag ->
                val ownKey = rememberOwnAdd(source.hash)
                val result =
                    when (source) {
                        is PendingSource.Magnet ->
                            session.repository.addTorrentUrl(
                                source.url,
                                options.category,
                                options.savePath,
                                paused,
                                options.autoTmm,
                                tag,
                            )
                        is PendingSource.TorrentBytes ->
                            session.repository.addTorrentFile(
                                source.bytes,
                                options.category,
                                options.savePath,
                                paused,
                                options.autoTmm,
                                tag,
                            )
                    }
                result.onOk { source.ownAddKey = ownKey }.onErr { forgetOwnAdd(ownKey) }
            }
            .map { session }
    }

    private suspend fun finishPendingAdd(
        source: PendingSource,
        deselected: Set<Int>,
        options: AddTorrentOptions,
        paused: Boolean,
    ): Result<String, Throwable> {
        val addedBeforeConfirm = source.session?.ownsTorrent == true
        return addPendingTorrent(source, options, paused = true).andThen { session ->
            session.stop().onErr {
                return Err(it)
            }
            if (addedBeforeConfirm) {
                applyAddOptions(session, options).onErr {
                    return Err(it)
                }
            }
            val ids =
                if (deselected.isEmpty()) emptyList()
                else
                    when (source) {
                        is PendingSource.Magnet -> deselected.toList()
                        is PendingSource.TorrentBytes -> {
                            val files =
                                awaitTorrentFiles(session, FILE_POLL_ATTEMPTS, FILE_POLL_MS)
                                    ?: return Err(FileSelectionNotApplied())
                            mapDeselectedToServer(source.parsed, deselected, files).takeIf {
                                it.distinct().size == deselected.size
                            } ?: return Err(FileSelectionNotApplied())
                        }
                    }
            session.applySelectionAndStart(ids, paused).onErr {
                return Err(it)
            }
            session.releaseMarker().onErr {
                return Ok(
                    getString(
                        CommonR.string.status_torrent_added_tag_cleanup_failed,
                        session.marker,
                    ),
                )
            }
            Ok(getString(CommonR.string.status_add_torrent_file_success))
        }
    }

    private suspend fun applyAddOptions(
        session: TorrentAddSession,
        options: AddTorrentOptions,
    ): Result<Unit, Throwable> {
        val client = session.repository
        val hash = session.hash
        options.category?.let {
            client.setTorrentCategory(listOf(hash), it).onErr { error ->
                return Err(error)
            }
        }
        if (options.autoTmm == true) return client.setAutoTorrentManagement(hash, true)
        options.savePath?.let {
            client.setAutoTorrentManagement(hash, false).onErr { error ->
                return Err(error)
            }
            return client.setTorrentLocation(hash, it)
        }
        return Ok(Unit)
    }

    /** Cleans up a preview only if the session proved it created the torrent. */
    fun cancelFileSelection() {
        if (_confirmingAdd.value) return
        val source = pendingSource
        val job = metadataJob
        metadataJob = null
        clearFileSelection()
        if (source == null) return
        viewModelScope.launch {
            torrentAddMutex.withLock {
                job?.cancel()
                source.session?.let {
                    val result =
                        if (source.confirmed) it.releaseMarker()
                        else it.discard { forgetOwnAdd(source.ownAddKey) }
                    result.onErr { error -> emitStatus(addFailure(error, source)) }
                }
            }
        }
    }

    private fun clearFileSelection() {
        pendingSource = null
        _fileSelection.value = null
    }

    private fun showMetadataError(source: PendingSource, message: String) {
        if (pendingSource === source) _fileSelection.update { it?.copy(error = message) }
    }

    private fun addFailure(error: Throwable, source: PendingSource): String =
        when (error) {
            is TorrentAlreadyPresent -> getString(CommonR.string.error_torrent_already_exists)
            is FileSelectionNotApplied -> getString(CommonR.string.status_file_selection_failed)
            else ->
                error.friendlyMessage(
                    getString(
                        if (source is PendingSource.Magnet)
                            CommonR.string.status_add_torrent_url_failure
                        else CommonR.string.status_add_torrent_file_failure,
                    ),
                )
        }

    private suspend fun awaitTorrentFiles(
        session: TorrentAddSession,
        attempts: Int,
        interval: Long,
    ): List<TorrentFile>? {
        repeat(attempts) {
            val files = session.repository.getTorrentFiles(session.hash).get()
            if (!files.isNullOrEmpty()) return files
            delay(interval)
        }
        return null
    }

    // Prefer exact names, but allow a server content layout to add or remove a root folder.
    private fun mapDeselectedToServer(
        parsed: TorrentFileParser.ParsedTorrent,
        deselected: Set<Int>,
        server: List<TorrentFile>,
    ): List<Int> = deselected.mapNotNull { i ->
        val path = parsed.files.getOrNull(i)?.path ?: return@mapNotNull null
        val withoutRoot = path.removePrefix("${parsed.name}/")
        val file =
            server.singleOrNull { it.name == path }
                ?: server.singleOrNull { it.name == withoutRoot || it.name.endsWith("/$path") }
        file?.index
    }

    private fun magnetDisplayName(url: String): String {
        val encoded = Regex("[?&]dn=([^&]+)").find(url)?.groupValues?.get(1) ?: return ""
        return try {
            java.net.URLDecoder.decode(encoded.replace("+", "%20"), "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    fun bulkSetCategory(requested: List<String>, category: String) {
        val repository = displayedRepository ?: return
        val hashes = uiState.value.visibleTargets(requested)
        if (hashes.isEmpty()) return
        launchStatus(
            successMessage =
                getQuantityString(
                    CommonR.plurals.status_bulk_category_set,
                    hashes.size,
                    hashes.size,
                ),
            failureMessage = getString(CommonR.string.status_set_category_failure),
        ) {
            repository.setTorrentCategory(hashes, category)
        }
    }

    fun bulkAddTags(requested: List<String>, tags: List<String>) {
        val repository = displayedRepository ?: return
        val hashes = uiState.value.visibleTargets(requested)
        if (hashes.isEmpty() || tags.isEmpty()) return
        launchStatus(
            successMessage =
                getQuantityString(
                    CommonR.plurals.status_bulk_tags_updated,
                    hashes.size,
                    hashes.size,
                ),
            failureMessage = getString(CommonR.string.status_update_tags_failure),
        ) {
            repository.addTorrentTags(hashes, tags)
        }
    }

    fun bulkRemoveTags(requested: List<String>, tags: List<String>) {
        val repository = displayedRepository ?: return
        val hashes = uiState.value.visibleTargets(requested)
        if (hashes.isEmpty() || tags.isEmpty()) return
        launchStatus(
            successMessage =
                getQuantityString(
                    CommonR.plurals.status_bulk_tags_updated,
                    hashes.size,
                    hashes.size,
                ),
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
            successMessage =
                getQuantityString(CommonR.plurals.status_tags_deleted, tags.size, tags.size),
            failureMessage = getString(CommonR.string.status_delete_tags_failure),
            onSuccess = {
                _uiState.update { it.copy(selectedTags = it.selectedTags - tags.toSet()) }
                persistViewPrefs()
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
            successMessage =
                getQuantityString(
                    CommonR.plurals.status_categories_deleted,
                    names.size,
                    names.size,
                ),
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

    fun removeTorrents(requested: List<String>, deleteFiles: Boolean = false) {
        val repository = displayedRepository ?: return
        val hashes = uiState.value.visibleTargets(requested)
        if (hashes.isEmpty()) return
        launchStatus(
            successMessage =
                getQuantityString(
                    CommonR.plurals.status_torrents_removed,
                    hashes.size,
                    hashes.size,
                ),
            failureMessage = getString(CommonR.string.status_remove_torrents_failure),
        ) {
            repository.removeTorrents(hashes, deleteFiles)
        }
    }

    fun toggleTorrentsState(pause: Boolean, requested: List<String>) {
        val repository = displayedRepository ?: return
        val hashes = uiState.value.visibleTargets(requested)
        if (hashes.isEmpty()) return
        launchStatus(
            successMessage =
                if (pause)
                    getQuantityString(
                        CommonR.plurals.status_torrents_paused,
                        hashes.size,
                        hashes.size,
                    )
                else
                    getQuantityString(
                        CommonR.plurals.status_torrents_resumed,
                        hashes.size,
                        hashes.size,
                    ),
            failureMessage =
                if (pause)
                    getQuantityString(
                        CommonR.plurals.status_pause_torrents_failure,
                        hashes.size,
                        hashes.size,
                    )
                else
                    getQuantityString(
                        CommonR.plurals.status_resume_torrents_failure,
                        hashes.size,
                        hashes.size,
                    ),
        ) {
            repository.toggleTorrentsState(hashes, pause)
        }
    }

    fun setSort(option: SortOption, direction: SortDirection) {
        _uiState.update { it.copy(sortOption = option, sortDirection = direction) }
        persistViewPrefs()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query) }
    }

    fun setCategory(category: String?) {
        _uiState.update { state -> state.copy(selectedCategory = category) }
        persistViewPrefs()
    }

    fun setFilter(filter: StateFilter) {
        _uiState.update { state -> state.copy(selectedFilter = filter) }
        persistViewPrefs()
    }

    fun setTracker(tracker: String?) {
        _uiState.update { state -> state.copy(selectedTracker = tracker) }
        persistViewPrefs()
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val tags = state.selectedTags.toMutableSet()
            if (tags.contains(tag)) tags.remove(tag) else tags.add(tag)
            state.copy(selectedTags = tags, filterUntagged = false)
        }
        persistViewPrefs()
    }

    fun setFilterUntagged(untagged: Boolean) {
        _uiState.update { it.copy(filterUntagged = untagged, selectedTags = emptySet()) }
        persistViewPrefs()
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
        persistViewPrefs()
    }

    private suspend fun getQueueingState(repository: QbitRepository) {
        repository.isQueueingEnabled().onOk { enabled ->
            // Don't leave the list stuck on the Queued filter if the server has queueing off
            // (e.g. a filter persisted from a prior session, or queueing toggled off
            // elsewhere).
            val clearFilter = !enabled && _uiState.value.selectedFilter == StateFilter.QUEUED
            _uiState.update {
                it.copy(
                    queueingEnabled = enabled,
                    selectedFilter = if (clearFilter) StateFilter.ALL else it.selectedFilter,
                )
            }
            if (clearFilter) persistViewPrefs()
        }
    }

    private suspend fun syncData() {
        val session = repository.forCurrentClient()
        if (session == null) {
            _uiState.update { it.copy(dataLoading = false, hasError = true) }
            return
        }
        try {
            syncData(session)
        } finally {
            displayedRepository = null
        }
    }

    private suspend fun syncData(repository: QbitRepository) {
        coroutineScope {
            launch { getQueueingState(repository) }
            // Data: keep the last-known list on screen; retry quietly on error. The offline state
            // is
            // driven by the durable error signal below, not by this flow throwing.
            launch {
                repository
                    .observeMainData()
                    .retry {
                        delay(SYNC_RETRY_MS)
                        true
                    }
                    .collectLatest { mainData ->
                        val categories = mainData.categories.keys.sorted()
                        val trackerCounts =
                            withContext(Dispatchers.Default) {
                                trackerFilterCounts(mainData.torrents.values.map { it.tracker })
                            }
                        val tags =
                            (mainData.tags + mainData.torrents.values.flatMap { it.tags })
                                .filter { it.isNotBlank() }
                                .distinct()
                                .sorted()
                        displayedRepository = repository
                        _uiState.update { state ->
                            state.copy(
                                dataLoading = false,
                                data = mainData,
                                hasError = false,
                                error = null,
                                availableCategories = categories,
                                availableTrackers = trackerCounts.keys.toList(),
                                trackerCounts = trackerCounts,
                                availableTags = tags,
                            )
                        }
                        _intent.emit(Unit)
                    }
            }
            // Health: a reliable offline signal. If we already have data, keep showing it and just
            // toast on the transition; only show the full error screen when nothing has loaded yet.
            launch {
                var lastError: Throwable? = null
                repository.observeMainDataError().collect { error ->
                    _uiState.update { state ->
                        when {
                            error == null -> state.copy(hasError = false, error = null)
                            state.data == null ->
                                state.copy(hasError = true, error = error, dataLoading = false)
                            else -> state
                        }
                    }
                    if (error != null && lastError == null && _uiState.value.data != null) {
                        emitStatus(
                            error.friendlyMessage(
                                getString(CommonR.string.status_sync_data_failure),
                            ),
                        )
                    }
                    lastError = error
                }
            }
        }
    }
}

/** Tree + name shown by the add-time file selection dialog; [tree] is null while metadata loads. */
data class FileSelectionUiState(
    val torrentName: String,
    val tree: List<ContentTreeItem>?,
    val error: String? = null,
    val confirmationError: String? = null,
)

private sealed class PendingSource(val hash: String) {
    var ownAddKey: String? = null
    var session: TorrentAddSession? = null
    var confirmed = false

    class TorrentBytes(
        val bytes: ByteArray,
        val parsed: TorrentFileParser.ParsedTorrent,
    ) : PendingSource(parsed.hash)

    class Magnet(val url: String, hash: String) : PendingSource(hash) {
        var previewResult: Result<TorrentAddSession, Throwable>? = null
    }
}

private data class AddTorrentOptions(
    val category: String? = null,
    val savePath: String? = null,
    val autoTmm: Boolean? = null,
)

private const val SYNC_RETRY_MS = 5000L
private const val METADATA_POLL_MS = 1500L
private const val METADATA_MAX_ATTEMPTS = 80
private const val FILE_POLL_ATTEMPTS = 20
private const val FILE_POLL_MS = 500L
