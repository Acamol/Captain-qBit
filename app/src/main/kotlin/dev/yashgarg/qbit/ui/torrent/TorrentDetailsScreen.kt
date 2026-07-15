package dev.yashgarg.qbit.ui.torrent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.torrent.tabs.FilesTab
import dev.yashgarg.qbit.ui.torrent.tabs.InfoTab
import dev.yashgarg.qbit.ui.torrent.tabs.PeersListView
import dev.yashgarg.qbit.ui.torrent.tabs.TrackersTab
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import kotlinx.coroutines.launch

private val TAB_TITLES = listOf("General", "Files", "Trackers", "Peers")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailsScreen(
    appNavigator: AppNavigator,
    viewModel: TorrentDetailsViewModel = hiltViewModel(),
) {
    val copy = rememberCopyToClipboard()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val torrent = state.torrent
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.removed.collect { appNavigator.navigate(NavCommand.Back) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        torrent?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Long-press the title to copy the torrent name.
                        modifier =
                            Modifier.pointerInput(torrent?.name) {
                                detectTapGestures(
                                    onLongPress = {
                                        torrent?.let { copy("name", it.name, "Copied name") }
                                    }
                                )
                            },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (torrent != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            fun act(block: () -> Unit) {
                                menuOpen = false
                                block()
                            }
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                leadingIcon = { Icon(Icons.Filled.Pause, null) },
                                onClick = { act { viewModel.toggleTorrent(true, torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                onClick = { act { viewModel.toggleTorrent(false, torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { act { dialog = DetailDialog.Delete } },
                            )
                            // qBittorrent derives a magnet URI for every torrent; only guard the
                            // rare case where one isn't available yet (e.g. metadata not fetched).
                            if (torrent.magnetUri.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Copy magnet link") },
                                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                                    onClick = {
                                        act {
                                            copy(
                                                "magnet",
                                                torrent.magnetUri,
                                                "Copied to clipboard",
                                            )
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Force recheck") },
                                leadingIcon = { Icon(Icons.Filled.FindInPage, null) },
                                onClick = { act { viewModel.forceRecheck(torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Force reannounce") },
                                leadingIcon = { Icon(Icons.Filled.Campaign, null) },
                                onClick = { act { viewModel.forceReannounce(torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                onClick = { act { dialog = DetailDialog.Rename } },
                            )
                            DropdownMenuItem(
                                text = { Text("Set category") },
                                leadingIcon = { Icon(Icons.Filled.Category, null) },
                                onClick = { act { dialog = DetailDialog.Category } },
                            )
                            DropdownMenuItem(
                                text = { Text("Set tags") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                                onClick = { act { dialog = DetailDialog.Tags } },
                            )
                            DropdownMenuItem(
                                text = { Text("Automatic management") },
                                leadingIcon = { Icon(Icons.Filled.Autorenew, null) },
                                trailingIcon = {
                                    Checkbox(checked = torrent.autoTmm, onCheckedChange = null)
                                },
                                onClick = {
                                    act { viewModel.setAutoManagement(!torrent.autoTmm) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Set save path…") },
                                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                                onClick = { act { dialog = DetailDialog.SavePath } },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.errorReason?.let { reason ->
                Text(
                    reason,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            TabRow(selectedTabIndex = pagerState.currentPage) {
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
                    0 -> InfoTab(state, Modifier.fillMaxSize())
                    1 -> FilesTab(state, viewModel::setFilePriority, Modifier.fillMaxSize())
                    2 -> TrackersTab(state, Modifier.fillMaxSize())
                    else -> PeersListView(state, Modifier.fillMaxSize(), viewModel::banPeer)
                }
            }
        }
    }

    when (dialog) {
        DetailDialog.Delete ->
            if (torrent != null) {
                DeleteDialog(
                    onConfirm = { deleteFiles ->
                        viewModel.removeTorrent(torrent.hash, deleteFiles)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.Rename ->
            if (torrent != null) {
                TextInputDialog(
                    title = "Rename torrent",
                    initial = torrent.name,
                    onConfirm = {
                        viewModel.renameTorrent(it, torrent.hash)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.SavePath ->
            if (torrent != null) {
                TextInputDialog(
                    title = "Save path",
                    initial = state.torrentProperties?.savePath ?: torrent.savePath,
                    onConfirm = {
                        viewModel.setSavePath(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.Category ->
            if (torrent != null) {
                CategoryDialog(
                    categories = state.availableCategories,
                    current = torrent.category,
                    onSelect = {
                        viewModel.setCategory(it)
                        dialog = null
                    },
                    onNew = { dialog = DetailDialog.CreateCategory },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.CreateCategory ->
            TextInputDialog(
                title = "New category",
                initial = "",
                onConfirm = {
                    if (it.isNotBlank()) viewModel.createCategory(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        DetailDialog.Tags ->
            if (torrent != null) {
                TagsDialog(
                    tags = state.availableTags,
                    current = torrent.tags.toSet(),
                    onApply = { toAdd, toRemove ->
                        viewModel.setTags(toAdd, toRemove)
                        dialog = null
                    },
                    onNew = { dialog = DetailDialog.CreateTag },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.CreateTag ->
            TextInputDialog(
                title = "New tag",
                initial = "",
                onConfirm = {
                    if (it.isNotBlank()) viewModel.setTags(listOf(it), emptyList())
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

private enum class DetailDialog {
    Delete,
    Rename,
    SavePath,
    Category,
    CreateCategory,
    Tags,
    CreateTag,
}

@Composable
private fun DeleteDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var deleteFiles by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete torrent?") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { deleteFiles = !deleteFiles },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                Spacer(Modifier.size(8.dp))
                Text("Also delete files on disk")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(deleteFiles) }) { Text("Delete") } },
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

@Composable
private fun CategoryDialog(
    categories: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("") + categories
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = option == current,
                                    onClick = { onSelect(option) },
                                )
                                .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Spacer(Modifier.size(12.dp))
                        Text(option.ifBlank { "None" })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagsDialog(
    tags: List<String>,
    current: Set<String>,
    onApply: (List<String>, List<String>) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (tags.isEmpty()) {
        // No tags yet — jump straight to creating one.
        LaunchedEffect(Unit) { onNew() }
        return
    }
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(tags, current) {
        tags.forEach { checked[it] = current.contains(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    val isChecked = checked[tag] == true
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { checked[tag] = !isChecked }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = { checked[tag] = it })
                        Spacer(Modifier.size(8.dp))
                        Text(tag)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val toAdd = tags.filter { checked[it] == true && !current.contains(it) }
                    val toRemove = tags.filter { checked[it] != true && current.contains(it) }
                    onApply(toAdd, toRemove)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onNew) { Text("New tag…") } },
    )
}
