package dev.yashgarg.qbit.ui.server

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.dialogs.AddTorrentScreen
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.utils.TorrentHashUtil
import dev.yashgarg.qbit.utils.friendlyMessage
import dev.yashgarg.qbit.utils.toHumanReadable
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
 * The main torrent-list screen (native Compose port of `ServerFragment`). A [ModalNavigationDrawer]
 * (filter sidebar) wraps a [Scaffold] with a [BottomAppBar] (menu / search / sort, or bulk-action
 * icons while selecting) and an add-torrent FAB. Bulk pickers and management dialogs are driven by
 * [ServerDialogHost]; add/import uses the [AddTorrentScreen] full-screen Compose dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(appNavigator: AppNavigator, viewModel: ServerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val torrents by viewModel.sortedTorrents.collectAsStateWithLifecycle()
    val categoryColors by viewModel.categoryColors.collectAsStateWithLifecycle()

    var serverDialog by remember { mutableStateOf<ServerDialog?>(null) }
    val linkValidator = remember { LinkValidator() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
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
                Toast.makeText(context, "Torrent already exists", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (isLink) openTorrentDialog(prefillUrl = uri)
            else openTorrentDialog(prefillFileUri = uri)
        }
    }

    // Cold-start VIEW intent (magnet/.torrent from another app); the .cqb backup case is consumed
    // by
    // MainActivity. Handle once, then clear so a recomposition/return doesn't re-add it.
    LaunchedEffect(Unit) {
        activity.intent?.let { launch ->
            if (launch.action == Intent.ACTION_VIEW && launch.data != null) {
                handleAddIntent(launch.data.toString())
                launch.data = null
            }
        }
    }

    // Running-app VIEW intents.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener =
            androidx.core.util.Consumer<Intent> { intent ->
                intent.data?.let { handleAddIntent(it.toString()) }
            }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
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
                onToggleSpeedLimits = { viewModel.toggleSpeedLimits() },
                onSettings = { appNavigator.navigate(NavCommand.OpenSettings) },
            )
        },
    ) {
        val hasSelection = selected.isNotEmpty()
        Scaffold(
            bottomBar = {
                BottomAppBar(
                    // Ride above the keyboard so the bar stays reachable while searching.
                    modifier = Modifier.imePadding(),
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Filters")
                        }
                        if (hasSelection) {
                            IconButton(
                                onClick = { viewModel.toggleTorrentsState(true, selected.toList()) }
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause")
                            }
                            IconButton(
                                onClick = {
                                    viewModel.toggleTorrentsState(false, selected.toList())
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                            }
                            IconButton(
                                onClick = {
                                    serverDialog = ServerDialog.BulkCategory(selected.toList())
                                }
                            ) {
                                Icon(Icons.Filled.Category, contentDescription = "Category")
                            }
                            IconButton(
                                onClick = {
                                    serverDialog = ServerDialog.BulkTags(selected.toList())
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Tags")
                            }
                            IconButton(onClick = { deleteTargets = selected.toList() }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        } else {
                            IconButton(onClick = { searchOpen = !searchOpen }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            val sortActive =
                                state.sortOption != SortOption.NAME ||
                                    state.sortDirection != SortDirection.ASC
                            IconButton(onClick = { serverDialog = ServerDialog.SortPicker }) {
                                Icon(
                                    Icons.Filled.Sort,
                                    contentDescription = stringResource(CommonR.string.sort),
                                    modifier =
                                        Modifier.graphicsLayer {
                                            alpha = if (sortActive) 1f else 0.5f
                                        },
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { openTorrentDialog() }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(CommonR.string.add_server),
                            )
                        }
                    },
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val serverState = state.data?.serverState
                if (!state.hasError && !state.dataLoading && serverState != null) {
                    Text(
                        "↓ ${serverState.dlInfoSpeed.toHumanReadable()}/s  ↑ ${serverState.upInfoSpeed.toHumanReadable()}/s   ${serverState.freeSpace.toHumanReadable()} free",
                        style = MaterialTheme.typography.labelSmall,
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                if (searchOpen) {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    searchOpen = false
                                    viewModel.setSearchQuery("")
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
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
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.Image(
                                    androidx.compose.ui.res.painterResource(R.drawable.sync_error),
                                    contentDescription = null,
                                    modifier = Modifier.padding(bottom = 8.dp).size(70.dp),
                                )
                                Text(
                                    state.error?.friendlyMessage(fallback) ?: fallback,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
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
                                onRefresh = {
                                    refreshing = true
                                    scope.launch {
                                        viewModel.refresh()
                                        // The sync is continuous and its first emission is near
                                        // instant, so wait for the next data (or error) but also
                                        // hold the spinner a short minimum so the pull registers.
                                        // Timeout so it can never hang.
                                        coroutineScope {
                                            launch { delay(600) }
                                            launch {
                                                withTimeoutOrNull(15_000) {
                                                    merge(
                                                            viewModel.intent,
                                                            viewModel.uiState
                                                                .filter { it.hasError }
                                                                .map {},
                                                        )
                                                        .first()
                                                }
                                            }
                                        }
                                        refreshing = false
                                    }
                                },
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
                Text(if (targets.size > 1) "Remove ${targets.size} torrents" else "Remove torrent")
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text("Also delete the files on disk")
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
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) { Text("Cancel") }
            },
        )
    }

    ServerDialogHost(
        dialog = serverDialog,
        onDialogChange = { serverDialog = it },
        viewModel = viewModel,
        appNavigator = appNavigator,
    )

    if (showAddTorrent) {
        val prefs = viewModel.addTorrentPrefs.value
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
    val chips = buildList {
        if (state.selectedFilter != StateFilter.ALL)
            add(state.selectedFilter.label to { viewModel.setFilter(StateFilter.ALL) })
        state.selectedCategory?.let { c -> add(c to { viewModel.setCategory(null) }) }
        state.selectedTracker?.let { t -> add(t to { viewModel.setTracker(null) }) }
        if (state.filterUntagged) add("Untagged" to { viewModel.setFilterUntagged(false) })
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
