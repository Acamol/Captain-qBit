package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.RssFeedTreeView
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
import kotlinx.coroutines.launch
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(appNavigator: AppNavigator, viewModel: RssViewModel = hiltViewModel()) {
    val tabTitles =
        listOf(stringResource(CommonR.string.tab_feeds), stringResource(CommonR.string.tab_rules))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var addMenuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RssDialog?>(null) }
    var matchingArticles by remember { mutableStateOf<Map<String, List<String>>?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    // This screen's ViewModel is scoped to its own nav back-stack entry and stays alive (with
    // whatever it last loaded) while the rule editor or a feed's articles screen is on top - so
    // saving/removing a rule elsewhere wouldn't otherwise be reflected here until a manual
    // refresh. Re-fetching on every resume (not just the first) picks that up automatically.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.rss_title)) },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(CommonR.string.content_description_back),
                        )
                    }
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(CommonR.string.refresh_action),
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refresh() },
                        enabled = !state.refreshing,
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = stringResource(CommonR.string.refresh_interval_action),
                        icon = Icons.Filled.Schedule,
                        onClick = { dialog = RssDialog.RefreshInterval },
                        position = TooltipAnchorPosition.Below,
                    )
                    if (pagerState.currentPage == 0) {
                        TooltipIconButton(
                            label =
                                stringResource(
                                    if (state.sortDescending) CommonR.string.sort_ascending_action
                                    else CommonR.string.sort_descending_action
                                ),
                            icon = Icons.AutoMirrored.Filled.Sort,
                            onClick = { viewModel.toggleSort() },
                            position = TooltipAnchorPosition.Below,
                            modifier =
                                Modifier.graphicsLayer(
                                    scaleY = if (state.sortDescending) -1f else 1f
                                ),
                        )
                        TooltipIconButton(
                            label = stringResource(CommonR.string.mark_all_as_read),
                            icon = Icons.Filled.DoneAll,
                            onClick = { viewModel.markAllAsRead() },
                            position = TooltipAnchorPosition.Below,
                        )
                        TooltipIconButton(
                            label = stringResource(CommonR.string.add),
                            icon = Icons.Filled.Add,
                            onClick = { addMenuOpen = true },
                            position = TooltipAnchorPosition.Below,
                        )
                        DropdownMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(CommonR.string.add_feed_title)) },
                                leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                                onClick = {
                                    addMenuOpen = false
                                    dialog = RssDialog.AddFeed
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(CommonR.string.add_folder_menu_item))
                                },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = {
                                    addMenuOpen = false
                                    dialog = RssDialog.AddFolder
                                },
                            )
                        }
                    } else {
                        TooltipIconButton(
                            label = stringResource(CommonR.string.new_rule_action),
                            icon = Icons.Filled.Add,
                            onClick = { appNavigator.navigate(NavCommand.OpenRssRuleEditor()) },
                            position = TooltipAnchorPosition.Below,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            when {
                !state.rssProcessingEnabled ->
                    RssWarningBanner(
                        message = stringResource(CommonR.string.rss_fetching_disabled_message),
                        onEnable = { viewModel.setRssProcessingEnabled(true) },
                    )
                pagerState.currentPage == 1 && !state.rssAutoDownloadingEnabled ->
                    RssWarningBanner(
                        message =
                            stringResource(CommonR.string.rss_auto_downloading_disabled_message),
                        onEnable = { viewModel.setRssAutoDownloadingEnabled(true) },
                    )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 ->
                        FeedsTab(
                            state = state,
                            onRefresh = viewModel::refresh,
                            onFeedClick = { feed ->
                                appNavigator.navigate(NavCommand.OpenRssArticles(feed.path))
                            },
                            onFeedAction = { dialog = RssDialog.FeedActions(it) },
                            onFolderAction = { dialog = RssDialog.FolderActions(it) },
                            onMove = { item, destPath -> viewModel.moveItem(item.path, destPath) },
                        )
                    else ->
                        RulesTab(
                            state = state,
                            onRefresh = viewModel::refresh,
                            onRuleClick = {
                                appNavigator.navigate(NavCommand.OpenRssRuleEditor(it))
                            },
                            onToggleEnabled = { name, rule ->
                                viewModel.setRule(name, rule.copy(enabled = !rule.enabled))
                            },
                            onViewMatches = { name ->
                                viewModel.loadMatchingArticles(name) { matchingArticles = it }
                            },
                        )
                }
            }
        }
    }

    matchingArticles?.let { matches ->
        MatchingArticlesResultDialog(
            matches = matches,
            feeds = state.items.flattenFeeds(),
            onDismiss = { matchingArticles = null },
        )
    }

    when (val d = dialog) {
        is RssDialog.AddFeed ->
            AddFeedDialog(
                folders = state.items.flattenFolderPaths(),
                onConfirm = { url, path ->
                    viewModel.addFeed(url, path.ifBlank { null })
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.AddFolder ->
            AddFolderDialog(
                folders = state.items.flattenFolderPaths(),
                onConfirm = { name, parent ->
                    val path =
                        if (parent.isBlank()) name.trim() else "${parent.trim()}\\${name.trim()}"
                    viewModel.addFolder(path)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.FeedActions ->
            FeedActionsDialog(
                feed = d.feed,
                onRefresh = {
                    viewModel.refreshItem(d.feed.path)
                    dialog = null
                },
                onMarkAllRead = {
                    viewModel.markAsRead(d.feed.path)
                    dialog = null
                },
                onMoveToFolder = { dialog = RssDialog.MoveItem(d.feed) },
                onRemove = {
                    viewModel.removeItem(d.feed.path)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.FolderActions ->
            FolderActionsDialog(
                folder = d.folder,
                onMoveToFolder = { dialog = RssDialog.MoveItem(d.folder) },
                onRemove = {
                    viewModel.removeItem(d.folder.path)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.MoveItem -> {
            // Moving a folder into itself (or one of its own descendants) would orphan it, so
            // exclude that whole subtree from the destination picker. Feeds have no descendants,
            // so no exclusion is needed for them.
            val excluded = (d.item as? RssFolder)?.path
            val folderOptions =
                state.items.flattenFolderPaths().filter { path ->
                    excluded == null || (path != excluded && !path.startsWith("$excluded\\"))
                }
            MoveItemDialog(
                itemName = d.item.name,
                folders = folderOptions,
                onMove = { destPath ->
                    viewModel.moveItem(d.item.path, destPath)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        RssDialog.RefreshInterval ->
            RefreshIntervalDialog(
                currentMinutes = state.refreshIntervalMinutes,
                onConfirm = {
                    viewModel.setRefreshInterval(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

private sealed interface RssDialog {
    data object AddFeed : RssDialog

    data object AddFolder : RssDialog

    data class FeedActions(val feed: RssFeed) : RssDialog

    data class FolderActions(val folder: RssFolder) : RssDialog

    data class MoveItem(val item: RssItem) : RssDialog

    data object RefreshInterval : RssDialog
}

@Composable
private fun RssWarningBanner(message: String, onEnable: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEnable) { Text(stringResource(CommonR.string.enable_action)) }
        }
    }
}

@Composable
private fun FeedsTab(
    state: RssState,
    onRefresh: () -> Unit,
    onFeedClick: (RssFeed) -> Unit,
    onFeedAction: (RssFeed) -> Unit,
    onFolderAction: (RssFolder) -> Unit,
    onMove: (item: RssItem, destPath: String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null ->
                Text(
                    state.error,
                    Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            state.items.isEmpty() ->
                Text(
                    stringResource(CommonR.string.no_rss_feeds_placeholder),
                    Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            else -> {
                val sortedItems =
                    remember(state.items, state.sortDescending) {
                        state.items.sortedTree(state.sortDescending)
                    }
                RssFeedTreeView(
                    nodes = sortedItems,
                    onFeedClick = onFeedClick,
                    onFeedLongClick = onFeedAction,
                    onFolderLongClick = onFolderAction,
                    onMove = onMove,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesTab(
    state: RssState,
    onRefresh: () -> Unit,
    onRuleClick: (String) -> Unit,
    onToggleEnabled: (String, RssRule) -> Unit,
    onViewMatches: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.rules.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    stringResource(CommonR.string.no_rss_rules_placeholder),
                    Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@PullToRefreshBox
        }
        val sortedRules =
            remember(state.rules) { state.rules.entries.sortedBy { it.key.lowercase() } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(sortedRules, key = { it.key }) { (name, rule) ->
                Column {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onRuleClick(name) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        TooltipIconButton(
                            label = stringResource(CommonR.string.view_matching_articles_action),
                            icon = Icons.Filled.Visibility,
                            onClick = { onViewMatches(name) },
                            position = TooltipAnchorPosition.Below,
                        )
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggleEnabled(name, rule) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AddFeedDialog(
    folders: List<String>,
    onConfirm: (url: String, path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.add_feed_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(CommonR.string.feed_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                FolderPickerField(
                    label = stringResource(CommonR.string.folder_label),
                    selected = path,
                    folders = folders,
                    onSelect = { path = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url, path) }, enabled = url.isNotBlank()) {
                Text(stringResource(CommonR.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/**
 * A read-only dropdown of existing folder paths (plus "Root") - avoids hand-typing a "\"-joined
 * path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerField(
    label: String,
    selected: String,
    folders: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rootLabel = stringResource(CommonR.string.root_label)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.ifBlank { rootLabel },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf("") + folders).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifBlank { rootLabel }) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionOption(icon: ImageVector, label: String, action: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(16.dp))
        Text(label)
    }
}

@Composable
private fun FeedActionsDialog(
    feed: RssFeed,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(feed.name) },
        text = {
            Column {
                ActionOption(
                    Icons.Filled.Refresh,
                    stringResource(CommonR.string.refresh_action),
                    onRefresh,
                )
                ActionOption(
                    Icons.Filled.DoneAll,
                    stringResource(CommonR.string.mark_all_as_read),
                    onMarkAllRead,
                )
                ActionOption(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    stringResource(CommonR.string.move_to_folder_action),
                    onMoveToFolder,
                )
                ActionOption(
                    Icons.Filled.Delete,
                    stringResource(CommonR.string.remove_feed_action),
                    onRemove,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

@Composable
private fun FolderActionsDialog(
    folder: RssFolder,
    onMoveToFolder: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = {
            Column {
                ActionOption(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    stringResource(CommonR.string.move_to_folder_action),
                    onMoveToFolder,
                )
                ActionOption(
                    Icons.Filled.Delete,
                    stringResource(CommonR.string.remove_folder_action),
                    onRemove,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** A single global setting (not per-feed - qBittorrent has no per-feed refresh interval). */
@Composable
internal fun RefreshIntervalDialog(
    currentMinutes: Int,
    onConfirm: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(currentMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.rss_refresh_interval_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(stringResource(CommonR.string.minutes_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let(onConfirm) },
                enabled = (value.toIntOrNull() ?: 0) > 0,
            ) {
                Text(stringResource(CommonR.string.save_cfg))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** A single global setting (not per-feed - qBittorrent has no per-feed article cap). */
@Composable
internal fun MaxArticlesPerFeedDialog(
    currentCount: Int,
    onConfirm: (count: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(currentCount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.rss_max_articles_per_feed_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(stringResource(CommonR.string.articles_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let(onConfirm) },
                enabled = (value.toIntOrNull() ?: 0) > 0,
            ) {
                Text(stringResource(CommonR.string.save_cfg))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/**
 * Destination picker for "move to folder": root, any existing folder, or a freshly named one.
 * [folders] is already filtered by the caller to exclude the moved item's own subtree.
 */
@Composable
private fun MoveItemDialog(
    itemName: String,
    folders: List<String>,
    onMove: (destPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    fun destPathFor(folder: String) = if (folder.isBlank()) itemName else "$folder\\$itemName"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.move_item_to_title, itemName)) },
        text = {
            if (creatingFolder) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(CommonR.string.new_folder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(CommonR.string.root_label),
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onMove(destPathFor("")) }
                                .padding(vertical = 10.dp),
                    )
                    folders.forEach { folder ->
                        Text(
                            folder,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { onMove(destPathFor(folder)) }
                                    .padding(vertical = 10.dp),
                        )
                    }
                    TextButton(onClick = { creatingFolder = true }) {
                        Text(stringResource(CommonR.string.new_folder_ellipsis))
                    }
                }
            }
        },
        confirmButton = {
            if (creatingFolder) {
                TextButton(
                    onClick = { onMove(destPathFor(newFolderName.trim())) },
                    enabled = newFolderName.isNotBlank(),
                ) {
                    Text(stringResource(CommonR.string.move_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

@Composable
private fun AddFolderDialog(
    folders: List<String>,
    onConfirm: (name: String, parent: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.new_folder_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(CommonR.string.folder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                FolderPickerField(
                    label = stringResource(CommonR.string.parent_folder_label),
                    selected = parent,
                    folders = folders,
                    onSelect = { parent = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, parent) }, enabled = name.isNotBlank()) {
                Text(stringResource(CommonR.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}
