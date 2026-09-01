package dev.yashgarg.qbit.ui.server

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.dialogs.AddTorrentScreen
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.utils.TorrentHashUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import dev.yashgarg.qbit.utils.isUntrustedCertificateError
import dev.yashgarg.qbit.utils.matchesHost
import dev.yashgarg.qbit.utils.rememberFriendlyMessageResolver
import dev.yashgarg.qbit.utils.sha256Fingerprint
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.validation.LinkValidator
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The main torrent-list screen (native Compose port of `ServerFragment`). A [ModalNavigationDrawer]
 * (filter sidebar) wraps a [Scaffold] with a [TopAppBar] (menu / search / sort, or bulk-action
 * icons while selecting) and an add-torrent FAB. Bulk pickers and management dialogs are driven by
 * [ServerDialogHost]; add/import uses the [AddTorrentScreen] full-screen Compose dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(appNavigator: AppNavigator, viewModel: ServerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val torrentAlreadyExistsMessage = stringResource(CommonR.string.torrent_already_exists)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val torrents by viewModel.sortedTorrents.collectAsStateWithLifecycle()
    val categoryColors by viewModel.categoryColors.collectAsStateWithLifecycle()
    val activeServerId by viewModel.activeServerId.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    // Mirrors ClientManagerImpl.resolveActiveConfig()'s fallback, so "Review certificate" probes
    // the server that's actually showing this error even if activeServerId is stale/unset (-1).
    val resolvedServerId =
        remember(activeServerId, servers) {
            servers.find { it.configId == activeServerId }?.configId
                ?: servers.firstOrNull()?.configId
                ?: -1
        }

    var pendingCertReview by remember { mutableStateOf<X509Certificate?>(null) }
    var serverDialog by remember { mutableStateOf<ServerDialog?>(null) }
    val linkValidator = remember { LinkValidator() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Drawer state is saved, and the bottom-nav tabs deliberately save/restore each tab's state, so
    // leaving this tab with the filters open and coming back would restore it open - unexpected,
    // since returning to a tab should show the list, not a menu the user left behind.
    LaunchedEffect(Unit) { drawerState.close() }
    val selected = remember { mutableStateListOf<String>() }
    // Switching any filter (drawer, active-filter chips, or "Clear all") drops the current
    // selection, so bulk actions never apply to torrents scrolled out of the new filter.
    LaunchedEffect(
        state.selectedFilter,
        state.selectedCategory,
        state.selectedTracker,
        state.selectedTags,
        state.filterUntagged,
    ) {
        selected.clear()
    }
    val collapsedPaths = remember { mutableStateListOf<String>() }
    var searchOpen by remember { mutableStateOf(false) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // Shared by pull-to-refresh (list and error states) and the error-screen Retry button. The sync
    // is continuous and its first emission is near-instant, so wait for the next data/error but
    // hold
    // the spinner a short minimum (so the pull registers) with a timeout so it can never hang.
    val doRefresh: () -> Unit = {
        refreshing = true
        scope.launch {
            viewModel.refresh()
            coroutineScope {
                launch { delay(600) }
                launch {
                    withTimeoutOrNull(15_000) {
                        merge(viewModel.intent, viewModel.uiState.filter { it.hasError }.map {})
                            .first()
                    }
                }
            }
            refreshing = false
        }
    }
    // Hash of the single torrent row whose swipe actions are revealed (only one open at a time).
    var openHash by remember { mutableStateOf<String?>(null) }
    val searchFocus = remember { FocusRequester() }

    // Add-torrent screen state (full-screen Compose dialog). Prefills carry an incoming link/file.
    var showAddTorrent by rememberSaveable { mutableStateOf(false) }
    var addPrefillUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var addPrefillFileUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun openTorrentDialog(prefillUrl: String? = null, prefillFileUri: String? = null) {
        if (showAddTorrent) return
        addPrefillUrl = prefillUrl
        addPrefillFileUri = prefillFileUri
        showAddTorrent = true
    }

    fun handleAddIntent(uri: String?) {
        if (uri.isNullOrEmpty()) return
        val isLink = linkValidator.isValid(uri)
        val isFile = uri.startsWith("content://") || uri.startsWith("file://")
        if (!isLink && !isFile) return
        scope.launch {
            val incomingHash =
                if (isLink) TorrentHashUtil.infoHashFromMagnet(uri)
                else
                    withContext(Dispatchers.IO) {
                        runCatching {
                                context.contentResolver.openInputStream(uri.toUri())?.use {
                                    it.readBytes()
                                }
                            }
                            .getOrNull()
                            ?.let(TorrentHashUtil::infoHashFromTorrent)
                    }
            val existing = viewModel.uiState.value.data?.torrents?.keys.orEmpty()
            if (incomingHash != null && existing.contains(incomingHash)) {
                Toast.makeText(context, torrentAlreadyExistsMessage, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (isLink) openTorrentDialog(prefillUrl = uri)
            else openTorrentDialog(prefillFileUri = uri)
        }
    }

    // Torrent-file/magnet VIEW intents (cold start or already-running), handed off by MainActivity
    // via PendingTorrentIntent rather than read directly off the Activity's Intent — a StateFlow
    // replays its latest value to this collector however late it subscribes, so it can't race
    // MainActivity's onCreate/onNewIntent. The .cqb backup case is consumed by MainActivity itself.
    LaunchedEffect(Unit) {
        viewModel.pendingTorrentUri.collect { uri ->
            if (uri != null) {
                handleAddIntent(uri)
                viewModel.consumePendingTorrentUri()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.status.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus() else keyboard?.hide()
    }

    val drawerOpen = drawerState.isOpen
    BackHandler(enabled = drawerOpen || searchOpen || selected.isNotEmpty() || openHash != null) {
        when {
            drawerOpen -> scope.launch { drawerState.close() }
            searchOpen -> {
                searchOpen = false
                viewModel.setSearchQuery("")
            }
            selected.isNotEmpty() -> selected.clear()
            else -> openHash = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ServerDrawer(
                state = state,
                collapsedPaths = collapsedPaths,
                onServerPicker = { serverDialog = ServerDialog.ServerPicker },
                onStats = { serverDialog = ServerDialog.Statistics },
                onLogs = { appNavigator.navigate(NavCommand.OpenLogs) },
                onFilter = viewModel::setFilter,
                onCategory = viewModel::setCategory,
                onCategoryLongPress = { serverDialog = ServerDialog.CategoryLongPress(it) },
                onManageCategories = {
                    scope.launch { drawerState.close() }
                    serverDialog = ServerDialog.ManageCategories
                },
                onTracker = viewModel::setTracker,
                onFilterUntagged = viewModel::setFilterUntagged,
                onToggleTag = viewModel::toggleTag,
                onTagLongPress = { serverDialog = ServerDialog.TagLongPress(it) },
                onManageTags = {
                    scope.launch { drawerState.close() }
                    serverDialog = ServerDialog.ManageTags
                },
                onClearFilters = {
                    viewModel.clearFilters()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        val hasSelection = selected.isNotEmpty()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(CommonR.string.torrents_title)) },
                    navigationIcon = {
                        TooltipIconButton(
                            label = stringResource(CommonR.string.filters_label),
                            icon = Icons.Filled.Menu,
                            onClick = { scope.launch { drawerState.open() } },
                        )
                    },
                    actions = {
                        if (hasSelection) {
                            TooltipIconButton(
                                label = stringResource(CommonR.string.pause_action),
                                icon = Icons.Filled.Pause,
                                onClick = {
                                    viewModel.toggleTorrentsState(true, selected.toList())
                                },
                            )
                            TooltipIconButton(
                                label = stringResource(CommonR.string.resume),
                                icon = Icons.Filled.PlayArrow,
                                onClick = {
                                    viewModel.toggleTorrentsState(false, selected.toList())
                                },
                            )
                            TooltipIconButton(
                                label = stringResource(CommonR.string.category),
                                icon = Icons.Filled.Category,
                                onClick = {
                                    serverDialog = ServerDialog.BulkCategory(selected.toList())
                                },
                            )
                            TooltipIconButton(
                                label = stringResource(CommonR.string.tags_section_title),
                                icon = Icons.AutoMirrored.Filled.Label,
                                onClick = {
                                    serverDialog = ServerDialog.BulkTags(selected.toList())
                                },
                            )
                            TooltipIconButton(
                                label = stringResource(CommonR.string.delete),
                                icon = Icons.Filled.Delete,
                                onClick = { deleteTargets = selected.toList() },
                            )
                        } else {
                            TooltipIconButton(
                                label = stringResource(CommonR.string.content_description_search),
                                icon = Icons.Filled.Search,
                                onClick = { searchOpen = !searchOpen },
                            )
                            val sortActive =
                                state.sortOption != SortOption.NAME ||
                                    state.sortDirection != SortDirection.ASC
                            TooltipIconButton(
                                label = stringResource(CommonR.string.sort),
                                icon = Icons.AutoMirrored.Filled.Sort,
                                onClick = { serverDialog = ServerDialog.SortPicker },
                                modifier =
                                    Modifier.graphicsLayer { alpha = if (sortActive) 1f else 0.5f },
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { openTorrentDialog() },
                    modifier = Modifier.imePadding(),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription =
                            stringResource(CommonR.string.content_description_add_torrent),
                    )
                }
            },
            // This Scaffold has no bottomBar of its own - see NoWindowInsets kdoc; without this
            // the FAB would sit an extra, unwanted amount above the outer NavigationBar.
            contentWindowInsets = NoWindowInsets,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val serverState = state.data?.serverState
                if (!state.hasError && !state.dataLoading && serverState != null) {
                    // Mixing RTL text with several independent LTR numeric/unit values makes the
                    // Unicode bidi algorithm reorder things unpredictably depending on the actual
                    // numbers involved. Force LTR here so this line's segments always render in
                    // the fixed order the string template defines, regardless of locale.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            stringResource(
                                CommonR.string.status_speed_free_space,
                                serverState.dlInfoSpeed.toHumanReadable(),
                                serverState.upInfoSpeed.toHumanReadable(),
                                serverState.freeSpace.toHumanReadable(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                if (searchOpen) {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = {
                            Text(stringResource(CommonR.string.search_torrents_placeholder))
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    searchOpen = false
                                    viewModel.setSearchQuery("")
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription =
                                        stringResource(
                                            CommonR.string.content_description_close_search
                                        ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .focusRequester(searchFocus),
                    )
                }

                FilterChips(state, viewModel)

                Box(Modifier.fillMaxSize()) {
                    when {
                        state.hasError -> {
                            val fallback = stringResource(CommonR.string.error)
                            val friendlyMessageResolver = rememberFriendlyMessageResolver()
                            // Scrollable so pull-to-refresh works on the error screen too (a static
                            // Column wouldn't feed the pull gesture); the Retry button stays as an
                            // explicit affordance.
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = doRefresh,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    androidx.compose.foundation.Image(
                                        androidx.compose.ui.res.painterResource(
                                            R.drawable.sync_error
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.padding(bottom = 8.dp).size(70.dp),
                                    )
                                    Text(
                                        state.error?.friendlyMessage(
                                            friendlyMessageResolver,
                                            fallback,
                                        ) ?: fallback,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                    if (state.error?.isUntrustedCertificateError() == true) {
                                        FilledTonalButton(
                                            onClick = {
                                                val target =
                                                    servers.find { it.configId == resolvedServerId }
                                                        ?: return@FilledTonalButton
                                                scope.launch {
                                                    viewModel
                                                        .probeCertificate(
                                                            target.baseUrl,
                                                            target.port ?: 443,
                                                        )
                                                        .onOk { cert -> pendingCertReview = cert }
                                                }
                                            },
                                            modifier = Modifier.padding(top = 16.dp),
                                        ) {
                                            Text(
                                                stringResource(
                                                    CommonR.string.review_certificate_action
                                                )
                                            )
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { viewModel.refresh() },
                                            modifier = Modifier.padding(top = 16.dp),
                                        ) {
                                            Text(stringResource(CommonR.string.retry_action))
                                        }
                                    }
                                }
                            }
                        }
                        state.dataLoading || torrents == null ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        torrents.isNullOrEmpty() ->
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(R.drawable.cloud_done),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp).size(70.dp),
                                )
                                Text(
                                    stringResource(CommonR.string.no_queue),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        else ->
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = doRefresh,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                TorrentList(
                                    torrents = torrents.orEmpty(),
                                    categoryColors = categoryColors,
                                    selected = selected,
                                    openHash = openHash,
                                    onOpenChange = { hash, open ->
                                        openHash =
                                            if (open) hash
                                            else if (openHash == hash) null else openHash
                                    },
                                    onOpen = { appNavigator.navigate(NavCommand.OpenTorrent(it)) },
                                    onPauseResume = { t ->
                                        viewModel.toggleTorrentsState(!t.isPaused(), listOf(t.hash))
                                    },
                                    onDelete = { t -> deleteTargets = listOf(t.hash) },
                                )
                            }
                    }
                }
            }
        }
    }

    deleteTargets?.let { targets ->
        var deleteFiles by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = {
                Text(
                    if (targets.size > 1)
                        stringResource(CommonR.string.remove_torrents_count, targets.size)
                    else stringResource(CommonR.string.remove_torrent_singular)
                )
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text(stringResource(CommonR.string.also_delete_the_files_on_disk))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeTorrents(targets, deleteFiles)
                        selected.clear()
                        deleteTargets = null
                    }
                ) {
                    Text(stringResource(CommonR.string.remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    ServerDialogHost(
        dialog = serverDialog,
        onDialogChange = { serverDialog = it },
        viewModel = viewModel,
        appNavigator = appNavigator,
    )

    pendingCertReview?.let { cert ->
        val targetHost = servers.find { it.configId == resolvedServerId }?.baseUrl.orEmpty()
        val fingerprint = remember(cert) { cert.sha256Fingerprint() }
        val hostMismatch = remember(cert, targetHost) { !cert.matchesHost(targetHost) }
        AlertDialog(
            onDismissRequest = { pendingCertReview = null },
            title = { Text(stringResource(CommonR.string.untrusted_certificate_title)) },
            text = {
                Column {
                    Text(stringResource(CommonR.string.untrusted_certificate_message))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${stringResource(CommonR.string.certificate_subject_label)}: " +
                            cert.subjectX500Principal.name
                    )
                    Text(
                        "${stringResource(CommonR.string.certificate_issuer_label)}: " +
                            cert.issuerX500Principal.name
                    )
                    Text(
                        "${stringResource(CommonR.string.certificate_fingerprint_label)}: " +
                            fingerprint
                    )
                    if (hostMismatch) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                CommonR.string.certificate_hostname_mismatch_warning,
                                targetHost,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCertReview = null
                        scope.launch { viewModel.pinCertificate(resolvedServerId, cert.encoded) }
                    }
                ) {
                    Text(stringResource(CommonR.string.trust_and_retry_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCertReview = null }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    if (showAddTorrent) {
        val prefs by viewModel.addTorrentPrefs.collectAsStateWithLifecycle()
        AddTorrentScreen(
            viewModel = viewModel,
            availableCategories = state.availableCategories,
            defaultAutoTmm = prefs.addTorrentAutoTmm,
            defaultPaused = prefs.addTorrentPaused,
            defaultCategory = prefs.addTorrentCategory,
            prefillUrl = addPrefillUrl,
            prefillFileUri = addPrefillFileUri,
            onDismiss = {
                showAddTorrent = false
                addPrefillUrl = null
                addPrefillFileUri = null
            },
        )
    }
}

/**
 * Icon-only action-bar button that shows a plain tooltip label on long-press/hover, so the action
 * is discoverable without relying on the icon alone. [label] also serves as the accessibility
 * [contentDescription].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    enabled: Boolean = true,
) {
    val tooltipState = rememberTooltipState()
    val haptics = LocalHapticFeedback.current
    // Buzz when the long-press reveals the tooltip, matching the platform's press-and-hold feel.
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, modifier = modifier)
        }
    }
}

@Composable
private fun TorrentList(
    torrents: List<qbittorrent.models.Torrent>,
    categoryColors: Map<String, Int>,
    selected: SnapshotStateList<String>,
    openHash: String?,
    onOpenChange: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onPauseResume: (qbittorrent.models.Torrent) -> Unit,
    onDelete: (qbittorrent.models.Torrent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Extra bottom padding so the last row clears the floating add-torrent FAB, which - unlike
        // the old BottomAppBar-docked FAB - no longer reserves its own space below the content.
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(torrents, key = { it.hash }) { torrent ->
            SwipeableTorrentRow(
                torrent = torrent,
                categoryColors = categoryColors,
                selected = selected.contains(torrent.hash),
                selectionActive = selected.isNotEmpty(),
                revealed = openHash == torrent.hash,
                onRevealChange = { open -> onOpenChange(torrent.hash, open) },
                onClick = {
                    if (selected.isNotEmpty()) {
                        if (!selected.remove(torrent.hash)) selected.add(torrent.hash)
                    } else onOpen(torrent.hash)
                },
                onLongClick = {
                    if (!selected.remove(torrent.hash)) selected.add(torrent.hash)
                },
                onPauseResume = { onPauseResume(torrent) },
                onDelete = { onDelete(torrent) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(state: ServerScreenState, viewModel: ServerViewModel) {
    val untaggedLabel = stringResource(CommonR.string.untagged_label)
    val chips = buildList {
        if (state.selectedFilter != StateFilter.ALL)
            add(
                stringResource(state.selectedFilter.labelRes) to
                    {
                        viewModel.setFilter(StateFilter.ALL)
                    }
            )
        state.selectedCategory?.let { c -> add(c to { viewModel.setCategory(null) }) }
        state.selectedTracker?.let { t -> add(t to { viewModel.setTracker(null) }) }
        if (state.filterUntagged) add(untaggedLabel to { viewModel.setFilterUntagged(false) })
        state.selectedTags.forEach { tag -> add("#$tag" to { viewModel.toggleTag(tag) }) }
    }
    if (chips.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (label, onClear) ->
            InputChip(
                selected = true,
                onClick = onClear,
                label = { Text(label) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
            )
        }
    }
}
