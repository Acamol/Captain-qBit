package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.ActionSheet
import dev.yashgarg.qbit.ui.compose.ActionSheetItem
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.RssFeedTreeView
import dev.yashgarg.qbit.ui.compose.SingleChoiceSheet
import dev.yashgarg.qbit.ui.compose.TextInputDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.ui.server.TooltipIconButton
import kotlinx.coroutines.launch
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem

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
    var moreMenuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RssDialog?>(null) }
    var matchingArticles by remember { mutableStateOf<Map<String, List<String>>?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(pagerState.currentPage) {
        addMenuOpen = false
        moreMenuOpen = false
    }

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
                actions = {
                    TooltipIconButton(
                        label = stringResource(CommonR.string.refresh_action),
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refresh() },
                        enabled =
                            state.itemsLoad != RssLoad.Refreshing &&
                                state.rulesLoad != RssLoad.Refreshing,
                        position = TooltipAnchorPosition.Below,
                    )
                    Box {
                        TooltipIconButton(
                            label = stringResource(CommonR.string.content_description_more),
                            icon = Icons.Filled.MoreVert,
                            onClick = { moreMenuOpen = true },
                            position = TooltipAnchorPosition.Below,
                        )
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false },
                        ) {
                            if (pagerState.currentPage == 0) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (state.sortDescending)
                                                    CommonR.string.sort_ascending_action
                                                else CommonR.string.sort_descending_action,
                                            ),
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.toggleSort()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(CommonR.string.mark_all_as_read))
                                    },
                                    leadingIcon = { Icon(Icons.Filled.DoneAll, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.markAllAsRead()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(CommonR.string.refresh_interval_action))
                                },
                                leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                                onClick = {
                                    moreMenuOpen = false
                                    dialog = RssDialog.RefreshInterval
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                val addLabel =
                    stringResource(
                        if (pagerState.currentPage == 0) CommonR.string.add_feed_title
                        else CommonR.string.new_rule_action,
                    )
                ExtendedFloatingActionButton(
                    text = { Text(addLabel) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = addLabel) },
                    onClick = {
                        if (pagerState.currentPage == 0) addMenuOpen = true
                        else appNavigator.navigate(NavCommand.OpenRssRuleEditor())
                    },
                )
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(CommonR.string.add_feed_title)) },
                        leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                        onClick = {
                            addMenuOpen = false
                            dialog = RssDialog.AddFeed
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(CommonR.string.add_folder_menu_item)) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        onClick = {
                            addMenuOpen = false
                            dialog = RssDialog.AddFolder
                        },
                    )
                }
            }
        },
        // No bottomBar here - see NoWindowInsets kdoc.
        contentWindowInsets = NoWindowInsets,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
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
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 ->
                        FeedsTab(
                            state = state,
                            onRefresh = viewModel::refreshItems,
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
                            onRefresh = viewModel::refreshRules,
                            onRuleClick = {
                                appNavigator.navigate(NavCommand.OpenRssRuleEditor(it))
                            },
                            onToggleEnabled = viewModel::toggleRule,
                            onViewMatches = { name ->
                                viewModel.loadMatchingArticles(name) { matchingArticles = it }
                            },
                        )
                }
            }
        }
    }

    matchingArticles?.let { matches ->
        MatchingArticlesResultsScreen(
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
            FeedActionsSheet(
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
            FolderActionsSheet(
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
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
        isRefreshing = state.itemsLoad == RssLoad.Refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.itemsLoad == RssLoad.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.itemsLoad is RssLoad.Failed ->
                RssLoadError(state.itemsLoad.message, onRefresh, Modifier.align(Alignment.Center))
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
    onToggleEnabled: (String, Boolean) -> Unit,
    onViewMatches: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.rulesLoad == RssLoad.Refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.rulesLoad == RssLoad.Loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@PullToRefreshBox
        }
        if (state.rulesLoad is RssLoad.Failed) {
            RssLoadError(state.rulesLoad.message, onRefresh, Modifier.align(Alignment.Center))
            return@PullToRefreshBox
        }
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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
        ) {
            items(sortedRules, key = { it.key }) { (name, rule) ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onRuleClick(name) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (rule.mustContain.isNotBlank()) {
                            Text(
                                rule.mustContain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TooltipIconButton(
                        label = stringResource(CommonR.string.view_matching_articles_action),
                        icon = Icons.Filled.Visibility,
                        onClick = { onViewMatches(name) },
                        position = TooltipAnchorPosition.Below,
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleEnabled(name, it) },
                        enabled = state.rulesLoad == RssLoad.Ready,
                        modifier = Modifier.semantics { contentDescription = name },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
internal fun RssLoadError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(CommonR.string.retry_action)) }
    }
}

@Composable
private fun AddFeedDialog(
    folders: List<String>,
    onConfirm: (url: String, path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf("") }
    val urlEmptyError = stringResource(CommonR.string.invalid_feed_url)
    TextInputDialog(
        title = stringResource(CommonR.string.add_feed_title),
        label = stringResource(CommonR.string.feed_url_label),
        confirmLabel = stringResource(CommonR.string.add),
        validate = { url -> if (url.isBlank()) urlEmptyError else null },
        onConfirm = { url -> onConfirm(url, path) },
        onDismiss = onDismiss,
        extraContent = {
            Spacer(Modifier.size(8.dp))
            FolderPickerField(
                label = stringResource(CommonR.string.folder_label),
                selected = path,
                folders = folders,
                onSelect = { path = it },
            )
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
    ActionSheetItem(label, icon, action)
}

@Composable
private fun FeedActionsSheet(
    feed: RssFeed,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionSheet(
        onDismissRequest = onDismiss,
        title = feed.name,
    ) {
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
}

@Composable
private fun FolderActionsSheet(
    folder: RssFolder,
    onMoveToFolder: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionSheet(
        onDismissRequest = onDismiss,
        title = folder.name,
    ) {
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

    fun destPathFor(folder: String) = if (folder.isBlank()) itemName else "$folder\\$itemName"

    val title = stringResource(CommonR.string.move_item_to_title, itemName)
    if (creatingFolder) {
        val emptyError = stringResource(CommonR.string.invalid_name)
        TextInputDialog(
            title = title,
            label = stringResource(CommonR.string.new_folder_name_label),
            confirmLabel = stringResource(CommonR.string.move_action),
            validate = { if (it.isBlank()) emptyError else null },
            onConfirm = { onMove(destPathFor(it.trim())) },
            onDismiss = onDismiss,
        )
    } else {
        val destinations = listOf("") + folders
        SingleChoiceSheet(
            title = title,
            labels = listOf(stringResource(CommonR.string.root_label)) + folders,
            selectedIndex = -1,
            onSelect = { onMove(destPathFor(destinations[it])) },
            onDismiss = onDismiss,
            newLabel = stringResource(CommonR.string.new_folder_ellipsis),
            onNew = { creatingFolder = true },
        )
    }
}

@Composable
private fun AddFolderDialog(
    folders: List<String>,
    onConfirm: (name: String, parent: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var parent by remember { mutableStateOf("") }
    val nameEmptyError = stringResource(CommonR.string.invalid_name)
    TextInputDialog(
        title = stringResource(CommonR.string.new_folder_title),
        label = stringResource(CommonR.string.folder_name_label),
        confirmLabel = stringResource(CommonR.string.add),
        validate = { name -> if (name.isBlank()) nameEmptyError else null },
        onConfirm = { name -> onConfirm(name, parent) },
        onDismiss = onDismiss,
        extraContent = {
            Spacer(Modifier.size(8.dp))
            FolderPickerField(
                label = stringResource(CommonR.string.parent_folder_label),
                selected = parent,
                folders = folders,
                onSelect = { parent = it },
            )
        },
    )
}
