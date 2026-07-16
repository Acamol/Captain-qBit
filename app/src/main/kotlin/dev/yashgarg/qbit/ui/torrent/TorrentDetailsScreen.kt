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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.isPaused
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
                            // Pause and resume are mutually exclusive — show only the one that
                            // applies to the torrent's current state.
                            if (torrent.isPaused()) {
                                DropdownMenuItem(
                                    text = { Text("Resume") },
                                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                    onClick = {
                                        act { viewModel.toggleTorrent(false, torrent.hash) }
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Pause") },
                                    leadingIcon = { Icon(Icons.Filled.Pause, null) },
                                    onClick = {
                                        act { viewModel.toggleTorrent(true, torrent.hash) }
                                    },
                                )
                            }
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
                            // Queue-priority moves only do anything when the server has torrent
                            // queueing enabled; qBittorrent reports priority as -1 otherwise.
                            if (torrent.priority >= 0) {
                                DropdownMenuItem(
                                    text = { Text("Queue priority") },
                                    leadingIcon = { Icon(Icons.Filled.LowPriority, null) },
                                    onClick = { act { dialog = DetailDialog.QueuePriority } },
                                )
                            }
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
                            DropdownMenuItem(
                                text = { Text("Download limit…") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = { act { dialog = DetailDialog.DownloadLimit } },
                            )
                            DropdownMenuItem(
                                text = { Text("Upload limit…") },
                                leadingIcon = { Icon(Icons.Filled.Upload, null) },
                                onClick = { act { dialog = DetailDialog.UploadLimit } },
                            )
                            DropdownMenuItem(
                                text = { Text("Share limits…") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { act { dialog = DetailDialog.ShareLimits } },
                            )
                            DropdownMenuItem(
                                text = { Text("Force start") },
                                leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                trailingIcon = {
                                    Checkbox(checked = torrent.forceStart, onCheckedChange = null)
                                },
                                onClick = {
                                    act { viewModel.setForceStart(!torrent.forceStart) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sequential download") },
                                leadingIcon = { Icon(Icons.Filled.FormatListNumbered, null) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = torrent.sequentialDownload,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = { act { viewModel.toggleSequentialDownload() } },
                            )
                            DropdownMenuItem(
                                text = { Text("Download first and last pieces") },
                                leadingIcon = { Icon(Icons.Filled.Flag, null) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = torrent.firstLastPiecePriority,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = { act { viewModel.toggleFirstLastPriority() } },
                            )
                            DropdownMenuItem(
                                text = { Text("Super seeding") },
                                leadingIcon = { Icon(Icons.Filled.CloudUpload, null) },
                                trailingIcon = {
                                    Checkbox(checked = torrent.superSeeding, onCheckedChange = null)
                                },
                                onClick = {
                                    act { viewModel.setSuperSeeding(!torrent.superSeeding) }
                                },
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
                    0 -> InfoTab(state, Modifier.fillMaxSize())
                    1 -> FilesTab(state, viewModel::setFilePriority, Modifier.fillMaxSize())
                    2 ->
                        TrackersTab(
                            state = state,
                            onAddTrackers = viewModel::addTracker,
                            onEditTracker = viewModel::editTracker,
                            onRemoveTracker = viewModel::removeTracker,
                            modifier = Modifier.fillMaxSize(),
                        )
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
        DetailDialog.QueuePriority ->
            if (torrent != null) {
                QueuePriorityDialog(
                    onSelect = { action ->
                        viewModel.setQueuePriority(action, torrent.hash)
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
        DetailDialog.DownloadLimit ->
            if (torrent != null) {
                SpeedLimitDialog(
                    title = "Download limit",
                    currentBytesPerSec = torrent.dlLimit,
                    onConfirm = {
                        viewModel.setDownloadLimit(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.UploadLimit ->
            if (torrent != null) {
                SpeedLimitDialog(
                    title = "Upload limit",
                    currentBytesPerSec = torrent.uploadLimit,
                    onConfirm = {
                        viewModel.setUploadLimit(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        DetailDialog.ShareLimits ->
            if (torrent != null) {
                ShareLimitDialog(
                    currentRatioLimit = torrent.ratioLimit,
                    currentSeedingTimeLimit = torrent.seedingTimeLimit,
                    onConfirm = { ratio, minutes ->
                        viewModel.setShareLimits(ratio, minutes)
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
    QueuePriority,
    SavePath,
    DownloadLimit,
    UploadLimit,
    ShareLimits,
    Category,
    CreateCategory,
    Tags,
    CreateTag,
}

/**
 * Enter a speed limit in KiB/s (qBittorrent's own unit in the desktop dialog). A blank/zero value
 * clears the limit. [currentBytesPerSec] is the torrent's current limit in bytes/s (0 or -1 =
 * unlimited); it's shown converted to KiB/s.
 */
@Composable
private fun SpeedLimitDialog(
    title: String,
    currentBytesPerSec: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = if (currentBytesPerSec > 0) (currentBytesPerSec / 1024).toString() else ""
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new -> value = new.filter { it.isDigit() } },
                singleLine = true,
                label = { Text("KiB/s") },
                supportingText = { Text("Leave empty for unlimited") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toLongOrNull()?.times(1024) ?: 0L) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun QueuePriorityDialog(onSelect: (QueueAction) -> Unit, onDismiss: () -> Unit) {
    @Composable
    fun Option(icon: ImageVector, label: String, action: QueueAction) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable { onSelect(action) }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(16.dp))
            Text(label)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue priority") },
        text = {
            Column {
                Option(Icons.Filled.VerticalAlignTop, "Move to top", QueueAction.TOP)
                Option(Icons.Filled.ArrowUpward, "Move up", QueueAction.UP)
                Option(Icons.Filled.ArrowDownward, "Move down", QueueAction.DOWN)
                Option(Icons.Filled.VerticalAlignBottom, "Move to bottom", QueueAction.BOTTOM)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class LimitMode {
    GLOBAL,
    UNLIMITED,
    CUSTOM,
}

/**
 * Share-limit editor. qBittorrent encodes each limit as -2 (use global), -1 (no limit), or a real
 * value, so each row is a Global/Unlimited/Custom choice with a value field shown only for Custom.
 * [currentSeedingTimeLimit] is in minutes.
 */
@Composable
private fun ShareLimitDialog(
    currentRatioLimit: Float,
    currentSeedingTimeLimit: Long,
    onConfirm: (ratioLimit: Float, seedingTimeMinutes: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    fun modeOf(v: Double): LimitMode =
        when {
            v <= -2.0 -> LimitMode.GLOBAL
            v < 0.0 -> LimitMode.UNLIMITED
            else -> LimitMode.CUSTOM
        }
    var ratioMode by remember { mutableStateOf(modeOf(currentRatioLimit.toDouble())) }
    var seedMode by remember { mutableStateOf(modeOf(currentSeedingTimeLimit.toDouble())) }
    var ratioValue by remember {
        mutableStateOf(if (currentRatioLimit >= 0) currentRatioLimit.toString() else "")
    }
    var seedValue by remember {
        mutableStateOf(if (currentSeedingTimeLimit >= 0) currentSeedingTimeLimit.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share limits") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ratio limit", style = MaterialTheme.typography.titleSmall)
                LimitMode.entries.forEach { mode ->
                    LimitModeOption(mode, ratioMode == mode) { ratioMode = mode }
                }
                if (ratioMode == LimitMode.CUSTOM) {
                    OutlinedTextField(
                        value = ratioValue,
                        onValueChange = { new ->
                            ratioValue = new.filter { it.isDigit() || it == '.' }
                        },
                        singleLine = true,
                        label = { Text("Ratio") },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text("Seeding time limit", style = MaterialTheme.typography.titleSmall)
                LimitMode.entries.forEach { mode ->
                    LimitModeOption(mode, seedMode == mode) { seedMode = mode }
                }
                if (seedMode == LimitMode.CUSTOM) {
                    OutlinedTextField(
                        value = seedValue,
                        onValueChange = { new -> seedValue = new.filter { it.isDigit() } },
                        singleLine = true,
                        label = { Text("Minutes") },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ratio =
                        when (ratioMode) {
                            LimitMode.GLOBAL -> -2f
                            LimitMode.UNLIMITED -> -1f
                            LimitMode.CUSTOM -> ratioValue.toFloatOrNull() ?: 0f
                        }
                    val minutes =
                        when (seedMode) {
                            LimitMode.GLOBAL -> -2L
                            LimitMode.UNLIMITED -> -1L
                            LimitMode.CUSTOM -> seedValue.toLongOrNull() ?: 0L
                        }
                    onConfirm(ratio, minutes)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LimitModeOption(mode: LimitMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            when (mode) {
                LimitMode.GLOBAL -> "Use global limit"
                LimitMode.UNLIMITED -> "Unlimited"
                LimitMode.CUSTOM -> "Custom"
            }
        )
    }
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
