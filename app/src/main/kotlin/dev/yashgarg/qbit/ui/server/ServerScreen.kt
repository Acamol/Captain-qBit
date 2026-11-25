package dev.yashgarg.qbit.ui.server

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.ActionSheet
import dev.yashgarg.qbit.ui.compose.ActionSheetItem
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.CheckboxRow
import dev.yashgarg.qbit.ui.dialogs.AddTorrentScreen
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.utils.TorrentHashUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import dev.yashgarg.qbit.utils.hasExpired
import dev.yashgarg.qbit.utils.isNotYetValid
import dev.yashgarg.qbit.utils.isUntrustedCertificateError
import dev.yashgarg.qbit.utils.rememberFriendlyMessageResolver
import dev.yashgarg.qbit.utils.sha256Fingerprint
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.validFromText
import dev.yashgarg.qbit.utils.validUntilText
import dev.yashgarg.qbit.validation.LinkValidator
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
 * The torrent list, with a dedicated filter sheet and an add-torrent FAB. Bulk pickers and
 * assignment dialogs are driven by [ServerDialogHost]; add/import uses the [AddTorrentScreen]
 * full-screen Compose dialog.
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
    val compactTorrentList by viewModel.compactTorrentList.collectAsStateWithLifecycle()
    // The server actually connected to, so "Review certificate" probes the one showing the error.
    val activeServerId by viewModel.activeServerId.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    val target = servers.find { it.configId == activeServerId }?.certificateTarget()
    var pendingCertReview by remember(target) { mutableStateOf<CertificateReview?>(null) }
    var probingCertificate by remember(target) { mutableStateOf(false) }
    val fallback = stringResource(CommonR.string.error)
    val friendlyMessageResolver = rememberFriendlyMessageResolver()
    LaunchedEffect(target, probingCertificate) {
        if (target != null && probingCertificate) {
            viewModel
                .probeCertificate(target)
                .onOk { pendingCertReview = it }
                .onErr {
                    Toast.makeText(
                            context,
                            it.friendlyMessage(friendlyMessageResolver, fallback),
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            probingCertificate = false
        }
    }
    var serverDialog by
        remember(activeServerId, state.workspaceRevision) { mutableStateOf<ServerDialog?>(null) }
    val linkValidator = remember { LinkValidator() }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (viewModel.closeFiltersOnEntry) {
            viewModel.closeFiltersOnEntry = false
            filtersOpen = false
        }
    }
    val activity = context as? Activity
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) viewModel.closeFiltersOnEntry = true
        }
    }
    val selected =
        remember(
            state.selectedFilter,
            state.selectedCategory,
            state.selectedTracker,
            state.selectedTags,
            state.filterUntagged,
            state.searchQuery,
            activeServerId,
            state.workspaceRevision,
        ) {
            mutableStateListOf<String>()
        }
    val visibleSelected = state.visibleTargets(selected)
    val hasSelection = visibleSelected.isNotEmpty()
    if (visibleSelected.size != selected.size) {
        LaunchedEffect(visibleSelected, selected) { selected.retainAll(visibleSelected.toSet()) }
    }
    val collapsedPaths = remember { mutableStateListOf<String>() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchVisible = searchOpen || state.searchQuery.isNotEmpty()
    var moreMenuOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var floatingActionsHeight by remember { mutableIntStateOf(0) }
    var deleteTargets by
        remember(activeServerId, state.workspaceRevision) { mutableStateOf<List<String>?>(null) }
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
    var openHash by
        remember(activeServerId, state.workspaceRevision) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var focusSearch by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(hasSelection) {
        selectionMenuOpen = false
        if (hasSelection) {
            moreMenuOpen = false
            openHash = null
            keyboard?.hide()
        }
    }

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

    LaunchedEffect(searchVisible) { if (!searchVisible) keyboard?.hide() }

    BackHandler(
        enabled = filtersOpen || searchVisible || selected.isNotEmpty() || openHash != null,
    ) {
        when {
            filtersOpen -> filtersOpen = false
            searchVisible -> {
                searchOpen = false
                viewModel.setSearchQuery("")
            }
            selected.isNotEmpty() -> selected.clear()
            else -> openHash = null
        }
    }

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (hasSelection)
                                    pluralStringResource(
                                        CommonR.plurals.selected_torrents_count,
                                        visibleSelected.size,
                                        visibleSelected.size,
                                    )
                                else stringResource(CommonR.string.torrents_title),
                                style =
                                    if (hasSelection) MaterialTheme.typography.titleLarge
                                    else MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasSelection) {
                                Text(
                                    state.serverName.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                ServerPickerDropdown(
                                    name = state.serverName,
                                    servers = servers,
                                    activeServerId = activeServerId,
                                    onSelect = viewModel::switchServer,
                                    appNavigator = appNavigator,
                                    textStyle = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (hasSelection) {
                            TooltipIconButton(
                                label = stringResource(CommonR.string.clear_selection_action),
                                icon = Icons.Filled.Close,
                                onClick = { selected.clear() },
                            )
                        } else {
                            TooltipIconButton(
                                label = stringResource(CommonR.string.filters_label),
                                icon = Icons.Filled.FilterList,
                                onClick = { filtersOpen = true },
                            )
                        }
                    },
                    actions = {
                        if (!hasSelection) {
                            TooltipIconButton(
                                label = stringResource(CommonR.string.content_description_search),
                                icon = Icons.Filled.Search,
                                onClick = {
                                    searchOpen = !searchVisible
                                    if (searchOpen) {
                                        listState.requestScrollToItem(0)
                                        focusSearch = true
                                    } else viewModel.setSearchQuery("")
                                },
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
                            Box {
                                TooltipIconButton(
                                    label = stringResource(CommonR.string.content_description_more),
                                    icon = Icons.Filled.MoreVert,
                                    onClick = { moreMenuOpen = true },
                                )
                                DropdownMenu(
                                    expanded = moreMenuOpen,
                                    onDismissRequest = { moreMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        modifier =
                                            Modifier.semantics {
                                                role = Role.Checkbox
                                                toggleableState =
                                                    ToggleableState(compactTorrentList)
                                            },
                                        text = {
                                            Text(
                                                stringResource(
                                                    CommonR.string.compact_torrent_list_label,
                                                ),
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DensitySmall,
                                                contentDescription = null,
                                            )
                                        },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = compactTorrentList,
                                                onCheckedChange = null,
                                            )
                                        },
                                        onClick = {
                                            viewModel.setCompactTorrentList(!compactTorrentList)
                                            moreMenuOpen = false
                                            openHash = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(CommonR.string.search_online_action),
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.TravelExplore,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            moreMenuOpen = false
                                            appNavigator.navigate(NavCommand.OpenSearch)
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!searchVisible || WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                    Box(Modifier.onSizeChanged { floatingActionsHeight = it.height }) {
                        if (hasSelection) {
                            SelectionActionsToolbar(
                                onResume = {
                                    viewModel.toggleTorrentsState(false, visibleSelected)
                                },
                                onPause = {
                                    viewModel.toggleTorrentsState(true, visibleSelected)
                                },
                                onMore = { selectionMenuOpen = true },
                                modifier =
                                    Modifier.padding(horizontal = 16.dp)
                                        .widthIn(max = 560.dp)
                                        .fillMaxWidth(),
                            )
                        } else {
                            ExtendedFloatingActionButton(
                                onClick = { openTorrentDialog() },
                                icon = {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription =
                                            stringResource(
                                                CommonR.string.content_description_add_torrent,
                                            ),
                                    )
                                },
                                text = {
                                    Text(
                                        stringResource(
                                            CommonR.string.content_description_add_torrent,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            },
            floatingActionButtonPosition =
                if (hasSelection) FabPosition.Center else FabPosition.End,
            // This Scaffold has no bottomBar of its own - see NoWindowInsets kdoc; without this
            // the FAB would sit an extra, unwanted amount above the outer NavigationBar.
            contentWindowInsets = NoWindowInsets,
        ) { padding ->
            val rowSpacing = if (compactTorrentList) 6.dp else 12.dp
            val bottomPadding = with(LocalDensity.current) { floatingActionsHeight.toDp() } + 32.dp
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = doRefresh,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                ) {
                    item(key = "list-controls") {
                        Column {
                            val serverState = state.data?.serverState
                            if (!state.hasError && !state.dataLoading && serverState != null) {
                                ServerTransferSummary(
                                    downloadSpeed = serverState.dlInfoSpeed,
                                    uploadSpeed = serverState.upInfoSpeed,
                                    freeSpace = serverState.freeSpace,
                                    onOpenServer = {
                                        appNavigator.navigate(NavCommand.OpenServerOverview)
                                    },
                                )
                            }

                            if (searchVisible) {
                                LaunchedEffect(focusSearch) {
                                    if (focusSearch) {
                                        searchFocus.requestFocus()
                                        focusSearch = false
                                    }
                                }
                                TextField(
                                    value = state.searchQuery,
                                    onValueChange = viewModel::setSearchQuery,
                                    placeholder = {
                                        Text(
                                            stringResource(
                                                CommonR.string.search_torrents_placeholder,
                                            ),
                                        )
                                    },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                searchOpen = false
                                                viewModel.setSearchQuery("")
                                            },
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription =
                                                    stringResource(
                                                        CommonR.string
                                                            .content_description_close_search,
                                                    ),
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions =
                                        KeyboardActions(onSearch = { keyboard?.hide() }),
                                    colors =
                                        TextFieldDefaults.colors(
                                            focusedContainerColor =
                                                MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor =
                                                MaterialTheme.colorScheme.surface,
                                        ),
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                            .focusRequester(searchFocus),
                                )
                            }

                            StatusFilters(
                                selected = state.selectedFilter,
                                queueingEnabled = state.queueingEnabled,
                                onSelect = viewModel::setFilter,
                            )
                            FilterChips(state, viewModel)
                            if (!state.hasError && !state.dataLoading && torrents != null) {
                                Text(
                                    pluralStringResource(
                                        CommonR.plurals.visible_torrents_count,
                                        torrents.orEmpty().size,
                                        torrents.orEmpty().size,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    when {
                        state.hasError ->
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    androidx.compose.foundation.Image(
                                        androidx.compose.ui.res.painterResource(
                                            R.drawable.sync_error,
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
                                    if (
                                        state.error?.isUntrustedCertificateError() == true &&
                                            target != null
                                    ) {
                                        FilledTonalButton(
                                            onClick = { probingCertificate = true },
                                            enabled = !probingCertificate,
                                            modifier = Modifier.padding(top = 16.dp),
                                        ) {
                                            if (probingCertificate) {
                                                CircularProgressIndicator(Modifier.size(20.dp))
                                            } else {
                                                Text(
                                                    stringResource(
                                                        CommonR.string.review_certificate_action,
                                                    ),
                                                )
                                            }
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
                        state.dataLoading || torrents == null ->
                            item {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        torrents.isNullOrEmpty() ->
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        androidx.compose.ui.res.painterResource(
                                            R.drawable.cloud_done,
                                        ),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp).size(70.dp),
                                    )
                                    // An empty list means either the server really has no torrents
                                    // or
                                    // the active filters hid them all - saying which one saves the
                                    // user wondering where their torrents went.
                                    val filtering =
                                        state.selectedFilter != StateFilter.ALL ||
                                            state.selectedCategory != null ||
                                            state.selectedTracker != null ||
                                            state.filterUntagged ||
                                            state.selectedTags.isNotEmpty() ||
                                            state.searchQuery.isNotEmpty()
                                    Text(
                                        stringResource(
                                            if (filtering) CommonR.string.no_torrents_match_filter
                                            else CommonR.string.no_torrents,
                                        ),
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        else ->
                            torrentRows(
                                torrents = torrents.orEmpty(),
                                compact = compactTorrentList,
                                categoryColors = categoryColors,
                                selected = selected,
                                openHash = openHash,
                                onOpenChange = { hash, open ->
                                    openHash =
                                        if (open) hash else if (openHash == hash) null else openHash
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

    if (selectionMenuOpen && hasSelection) {
        ActionSheet(
            title =
                pluralStringResource(
                    CommonR.plurals.selected_torrents_count,
                    visibleSelected.size,
                    visibleSelected.size,
                ),
            onDismissRequest = { selectionMenuOpen = false },
        ) {
            ActionSheetItem(stringResource(CommonR.string.category), Icons.Filled.Category) {
                selectionMenuOpen = false
                serverDialog = ServerDialog.BulkCategory(visibleSelected)
            }
            ActionSheetItem(
                stringResource(CommonR.string.tags_section_title),
                Icons.AutoMirrored.Filled.Label,
            ) {
                selectionMenuOpen = false
                serverDialog = ServerDialog.BulkTags(visibleSelected)
            }
            ActionSheetItem(stringResource(CommonR.string.delete), Icons.Filled.Delete) {
                selectionMenuOpen = false
                deleteTargets = visibleSelected
            }
        }
    }

    val visibleDeleteTargets = deleteTargets?.let(state::visibleTargets)?.takeIf { it.isNotEmpty() }
    if (deleteTargets != visibleDeleteTargets) {
        LaunchedEffect(deleteTargets, visibleDeleteTargets) { deleteTargets = visibleDeleteTargets }
    }
    visibleDeleteTargets?.let { targets ->
        var deleteFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = {
                Text(
                    if (targets.size > 1)
                        stringResource(CommonR.string.remove_torrents_count, targets.size)
                    else stringResource(CommonR.string.remove_torrent_singular),
                )
            },
            text = {
                CheckboxRow(
                    label = stringResource(CommonR.string.also_delete_the_files_on_disk),
                    checked = deleteFiles,
                    onCheckedChange = { deleteFiles = it },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeTorrents(targets, deleteFiles)
                        selected.clear()
                        deleteTargets = null
                    },
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

    if (filtersOpen) {
        TorrentFilterSheet(
            state = state,
            collapsedPaths = collapsedPaths,
            onDismissRequest = { filtersOpen = false },
            onFilter = viewModel::setFilter,
            onCategory = viewModel::setCategory,
            onTracker = viewModel::setTracker,
            onFilterUntagged = viewModel::setFilterUntagged,
            onToggleTag = viewModel::toggleTag,
            onClearFilters = {
                viewModel.clearFilters()
                filtersOpen = false
            },
        )
    }

    ServerDialogHost(
        dialog = serverDialog,
        onDialogChange = { serverDialog = it },
        viewModel = viewModel,
    )

    pendingCertReview
        ?.takeIf { it.target == target }
        ?.let { review ->
            val cert = review.certificate
            val fingerprint = remember(cert) { cert.sha256Fingerprint() }
            val expired = remember(cert) { cert.hasExpired() }
            val notYetValid = remember(cert) { cert.isNotYetValid() }
            AlertDialog(
                onDismissRequest = { pendingCertReview = null },
                title = { Text(stringResource(CommonR.string.untrusted_certificate_title)) },
                text = {
                    Column {
                        Text(stringResource(CommonR.string.untrusted_certificate_message))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "https://${review.target.host}:${review.target.port ?: 443}${review.target.path.orEmpty()}",
                        )
                        Text(
                            "${stringResource(CommonR.string.certificate_subject_label)}: " +
                                cert.subjectX500Principal.name,
                        )
                        Text(
                            "${stringResource(CommonR.string.certificate_issuer_label)}: " +
                                cert.issuerX500Principal.name,
                        )
                        Text(
                            "${stringResource(CommonR.string.certificate_fingerprint_label)}: " +
                                fingerprint,
                        )
                        Text(
                            "${stringResource(CommonR.string.certificate_validity_label)}: " +
                                cert.validUntilText(),
                        )
                        if (expired || notYetValid) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (expired)
                                    stringResource(
                                        CommonR.string.certificate_expired_warning,
                                        cert.validUntilText(),
                                    )
                                else
                                    stringResource(
                                        CommonR.string.certificate_not_yet_valid_warning,
                                        cert.validFromText(),
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
                            scope.launch { viewModel.pinCertificate(review) }
                        },
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

private fun LazyListScope.torrentRows(
    torrents: List<qbittorrent.models.Torrent>,
    compact: Boolean,
    categoryColors: Map<String, Int>,
    selected: SnapshotStateList<String>,
    openHash: String?,
    onOpenChange: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onPauseResume: (qbittorrent.models.Torrent) -> Unit,
    onDelete: (qbittorrent.models.Torrent) -> Unit,
) {
    items(torrents, key = { it.hash }) { torrent ->
        Box(Modifier.padding(horizontal = 16.dp)) {
            SwipeableTorrentRow(
                torrent = torrent,
                compact = compact,
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

@Composable
private fun ServerTransferSummary(
    downloadSpeed: Long,
    uploadSpeed: Long,
    freeSpace: Long,
    onOpenServer: () -> Unit,
) {
    Surface(
        onClick = onOpenServer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(CommonR.string.server_overview_action),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TransferMetric(
                    label = stringResource(CommonR.string.dl_speed),
                    value =
                        stringResource(
                            CommonR.string.transfer_rate,
                            downloadSpeed.toHumanReadable(),
                        ),
                    icon = Icons.Filled.ArrowDownward,
                    modifier = Modifier.widthIn(min = 96.dp).weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                )
                TransferMetric(
                    label = stringResource(CommonR.string.up_speed),
                    value =
                        stringResource(CommonR.string.transfer_rate, uploadSpeed.toHumanReadable()),
                    icon = Icons.Filled.ArrowUpward,
                    modifier = Modifier.widthIn(min = 96.dp).weight(1f),
                )
                TransferMetric(
                    label = stringResource(CommonR.string.free_space_label),
                    value = freeSpace.toHumanReadable(),
                    icon = Icons.Filled.Storage,
                    modifier = Modifier.widthIn(min = 96.dp).weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusFilters(
    selected: StateFilter,
    queueingEnabled: Boolean,
    onSelect: (StateFilter) -> Unit,
) {
    val filters =
        remember(queueingEnabled) {
            StateFilter.entries.filter { it != StateFilter.QUEUED || queueingEnabled }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(selected, filters) {
        val index = filters.indexOf(selected)
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == selected.name }
        if (index >= 0 && !visible) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                shape = RoundedCornerShape(50),
            )
        }
    }
}
