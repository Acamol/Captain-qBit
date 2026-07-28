package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.compose.RssFeedTreeView
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import kotlinx.coroutines.launch
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder

private val TAB_TITLES = listOf("Feeds", "Rules")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(appNavigator: AppNavigator, viewModel: RssViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val scope = rememberCoroutineScope()

    var addMenuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RssDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSS") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    if (pagerState.currentPage == 0) {
                        IconButton(onClick = { addMenuOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                        DropdownMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add feed") },
                                leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                                onClick = {
                                    addMenuOpen = false
                                    dialog = RssDialog.AddFeed
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add folder") },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = {
                                    addMenuOpen = false
                                    dialog = RssDialog.AddFolder
                                },
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { appNavigator.navigate(NavCommand.OpenRssRuleEditor()) }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New rule")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
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
                            onFolderAction = { dialog = RssDialog.RemoveFolder(it) },
                        )
                    else ->
                        RulesTab(
                            state = state,
                            onRuleClick = {
                                appNavigator.navigate(NavCommand.OpenRssRuleEditor(it))
                            },
                            onToggleEnabled = { name, rule ->
                                viewModel.setRule(name, rule.copy(enabled = !rule.enabled))
                            },
                        )
                }
            }
        }
    }

    when (val d = dialog) {
        is RssDialog.AddFeed ->
            AddFeedDialog(
                onConfirm = { url, path ->
                    viewModel.addFeed(url, path.ifBlank { null })
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.AddFolder ->
            TextInputDialog(
                title = "New folder",
                initial = "",
                onConfirm = {
                    if (it.isNotBlank()) viewModel.addFolder(it)
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
                onRemove = {
                    viewModel.removeItem(d.feed.path)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is RssDialog.RemoveFolder ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Remove folder \"${d.folder.name}\"?") },
                text = { Text("This removes every feed inside it too.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeItem(d.folder.path)
                            dialog = null
                        }
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        null -> Unit
    }
}

private sealed interface RssDialog {
    data object AddFeed : RssDialog

    data object AddFolder : RssDialog

    data class FeedActions(val feed: RssFeed) : RssDialog

    data class RemoveFolder(val folder: RssFolder) : RssDialog
}

@Composable
private fun FeedsTab(
    state: RssState,
    onRefresh: () -> Unit,
    onFeedClick: (RssFeed) -> Unit,
    onFeedAction: (RssFeed) -> Unit,
    onFolderAction: (RssFolder) -> Unit,
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
                    "No RSS feeds yet — tap + to add one",
                    Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            else ->
                RssFeedTreeView(
                    nodes = state.items,
                    onFeedClick = onFeedClick,
                    onFeedLongClick = onFeedAction,
                    onFolderLongClick = onFolderAction,
                )
        }
    }
}

@Composable
private fun RulesTab(
    state: RssState,
    onRuleClick: (String) -> Unit,
    onToggleEnabled: (String, qbittorrent.models.RssRule) -> Unit,
) {
    if (state.rules.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "No auto-download rules yet — tap + to add one",
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.rules.entries.toList(), key = { it.key }) { (name, rule) ->
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable { onRuleClick(name) }.padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = rule.enabled, onCheckedChange = { onToggleEnabled(name, rule) })
            }
        }
    }
}

@Composable
private fun AddFeedDialog(onConfirm: (url: String, path: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add feed") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Folder (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url, path) }, enabled = url.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FeedActionsDialog(
    feed: RssFeed,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    @Composable
    fun Option(icon: ImageVector, label: String, action: () -> Unit) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(16.dp))
            Text(label)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(feed.name) },
        text = {
            Column {
                Option(Icons.Filled.Refresh, "Refresh", onRefresh)
                Option(Icons.Filled.MarkEmailRead, "Mark all as read", onMarkAllRead)
                Option(Icons.Filled.Delete, "Remove feed", onRemove)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
