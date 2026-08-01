package dev.yashgarg.qbit.ui.torrent

import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.isPaused
import dev.yashgarg.qbit.ui.torrent.tabs.FilesTab
import dev.yashgarg.qbit.ui.torrent.tabs.InfoTab
import dev.yashgarg.qbit.ui.torrent.tabs.PeersListView
import dev.yashgarg.qbit.ui.torrent.tabs.TrackersTab
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailsScreen(
    appNavigator: AppNavigator,
    viewModel: TorrentDetailsViewModel = hiltViewModel(),
) {
    val tabTitles =
        listOf(
            stringResource(R.string.tab_general),
            stringResource(R.string.tab_files),
            stringResource(R.string.tab_trackers),
            stringResource(R.string.peers),
        )
    val copy = rememberCopyToClipboard()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val torrent = state.torrent
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }
    val copiedNameMessage = stringResource(R.string.status_copied_name)
    val copiedToClipboardMessage = stringResource(R.string.clipboard_copied)

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
                            Modifier.basicMarquee().pointerInput(torrent?.name) {
                                detectTapGestures(
                                    onLongPress = {
                                        torrent?.let { copy("name", it.name, copiedNameMessage) }
                                    }
                                )
                            },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    if (torrent != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription =
                                    stringResource(R.string.content_description_more),
                            )
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
                                    text = { Text(stringResource(R.string.resume)) },
                                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                    onClick = {
                                        act { viewModel.toggleTorrent(false, torrent.hash) }
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pause_action)) },
                                    leadingIcon = { Icon(Icons.Filled.Pause, null) },
                                    onClick = {
                                        act { viewModel.toggleTorrent(true, torrent.hash) }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { act { dialog = DetailDialog.Delete } },
                            )
                            // qBittorrent derives a magnet URI for every torrent; only guard the
                            // rare case where one isn't available yet (e.g. metadata not fetched).
                            if (torrent.magnetUri.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_magnet_link)) },
                                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                                    onClick = {
                                        act {
                                            copy(
                                                "magnet",
                                                torrent.magnetUri,
                                                copiedToClipboardMessage,
                                            )
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.force_recheck)) },
                                leadingIcon = { Icon(Icons.Filled.FindInPage, null) },
                                onClick = { act { viewModel.forceRecheck(torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.force_reannounce)) },
                                leadingIcon = { Icon(Icons.Filled.Campaign, null) },
                                onClick = { act { viewModel.forceReannounce(torrent.hash) } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                onClick = { act { dialog = DetailDialog.Rename } },
                            )
                            // Only when the server has torrent queueing enabled — qBittorrent
                            // rejects the priority moves (409) otherwise.
                            if (state.queueingEnabled) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_priority)) },
                                    leadingIcon = { Icon(Icons.Filled.LowPriority, null) },
                                    onClick = { act { dialog = DetailDialog.QueuePriority } },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_category)) },
                                leadingIcon = { Icon(Icons.Filled.Category, null) },
                                onClick = { act { dialog = DetailDialog.Category } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_tags)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                                onClick = { act { dialog = DetailDialog.Tags } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.automatic_management)) },
                                leadingIcon = { Icon(Icons.Filled.Autorenew, null) },
                                trailingIcon = {
                                    Checkbox(checked = torrent.autoTmm, onCheckedChange = null)
                                },
                                onClick = {
                                    act { viewModel.setAutoManagement(!torrent.autoTmm) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_set_save_path)) },
                                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                                onClick = { act { dialog = DetailDialog.SavePath } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_download_limit)) },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = { act { dialog = DetailDialog.DownloadLimit } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_upload_limit)) },
                                leadingIcon = { Icon(Icons.Filled.Upload, null) },
                                onClick = { act { dialog = DetailDialog.UploadLimit } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_limits)) },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { act { dialog = DetailDialog.ShareLimits } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.force_start)) },
                                leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                trailingIcon = {
                                    Checkbox(checked = torrent.forceStart, onCheckedChange = null)
                                },
                                onClick = {
                                    act { viewModel.setForceStart(!torrent.forceStart) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sequential_download)) },
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
                                text = {
                                    Text(stringResource(R.string.download_first_last_pieces))
                                },
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
                                text = { Text(stringResource(R.string.super_seeding)) },
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
                tabTitles.forEachIndexed { index, title ->
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
                    1 ->
                        FilesTab(
                            state,
                            viewModel::setFilePriority,
                            viewModel::renameContent,
                            Modifier.fillMaxSize(),
                        )
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
                    title = stringResource(R.string.rename_torrent_title),
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
                    title = stringResource(R.string.save_path_hint),
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
                    title = stringResource(R.string.download_limit_title),
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
                    title = stringResource(R.string.upload_limit_title),
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
                    currentInactiveSeedingTimeLimit = torrent.inactiveSeedingTimeLimit,
                    onConfirm = { ratio, minutes, inactiveMinutes ->
                        viewModel.setShareLimits(ratio, minutes, inactiveMinutes)
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
                title = stringResource(R.string.new_category_title),
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
                title = stringResource(R.string.new_tag_title),
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
    // Display in KiB/s (qBittorrent's unit): round to nearest, and never show a real >0 limit as
    // blank (which would read as "unlimited"). Blank strictly means 0 = unlimited.
    val initial =
        if (currentBytesPerSec > 0) ((currentBytesPerSec + 512) / 1024).coerceAtLeast(1).toString()
        else ""
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new -> value = new.filter { it.isDigit() } },
                singleLine = true,
                label = { Text(stringResource(R.string.unit_kib_per_sec)) },
                supportingText = { Text(stringResource(R.string.leave_empty_for_unlimited)) },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // No-op if the field wasn't edited, so re-opening and confirming never rewrites
                    // (and rounds) a limit the user didn't touch.
                    if (value != initial) onConfirm(value.toLongOrNull()?.times(1024) ?: 0L)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var deleteFiles by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete_torrent_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { deleteFiles = !deleteFiles },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.also_delete_files_on_disk))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
        title = { Text(stringResource(R.string.queue_priority)) },
        text = {
            Column {
                Option(
                    Icons.Filled.VerticalAlignTop,
                    stringResource(R.string.move_to_top),
                    QueueAction.TOP,
                )
                Option(Icons.Filled.ArrowUpward, stringResource(R.string.move_up), QueueAction.UP)
                Option(
                    Icons.Filled.ArrowDownward,
                    stringResource(R.string.move_down),
                    QueueAction.DOWN,
                )
                Option(
                    Icons.Filled.VerticalAlignBottom,
                    stringResource(R.string.move_to_bottom),
                    QueueAction.BOTTOM,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
    currentInactiveSeedingTimeLimit: Long,
    onConfirm:
        (ratioLimit: Float, seedingTimeMinutes: Long, inactiveSeedingTimeMinutes: Long) -> Unit,
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
    var inactiveMode by remember {
        mutableStateOf(modeOf(currentInactiveSeedingTimeLimit.toDouble()))
    }
    var ratioValue by remember {
        mutableStateOf(if (currentRatioLimit >= 0) currentRatioLimit.toString() else "")
    }
    var seedValue by remember {
        mutableStateOf(if (currentSeedingTimeLimit >= 0) currentSeedingTimeLimit.toString() else "")
    }
    var inactiveValue by remember {
        mutableStateOf(
            if (currentInactiveSeedingTimeLimit >= 0) currentInactiveSeedingTimeLimit.toString()
            else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_limits_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.ratio_limit_label),
                    style = MaterialTheme.typography.titleSmall,
                )
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
                        label = { Text(stringResource(R.string.ratio)) },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.seeding_time_limit_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                LimitMode.entries.forEach { mode ->
                    LimitModeOption(mode, seedMode == mode) { seedMode = mode }
                }
                if (seedMode == LimitMode.CUSTOM) {
                    OutlinedTextField(
                        value = seedValue,
                        onValueChange = { new -> seedValue = new.filter { it.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.minutes_label)) },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.inactive_seeding_time_limit_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                LimitMode.entries.forEach { mode ->
                    LimitModeOption(mode, inactiveMode == mode) { inactiveMode = mode }
                }
                if (inactiveMode == LimitMode.CUSTOM) {
                    OutlinedTextField(
                        value = inactiveValue,
                        onValueChange = { new -> inactiveValue = new.filter { it.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.minutes_label)) },
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
                    fun timeOf(mode: LimitMode, value: String): Long =
                        when (mode) {
                            LimitMode.GLOBAL -> -2L
                            LimitMode.UNLIMITED -> -1L
                            LimitMode.CUSTOM -> value.toLongOrNull() ?: 0L
                        }
                    val ratio =
                        when (ratioMode) {
                            LimitMode.GLOBAL -> -2f
                            LimitMode.UNLIMITED -> -1f
                            LimitMode.CUSTOM -> ratioValue.toFloatOrNull() ?: 0f
                        }
                    onConfirm(
                        ratio,
                        timeOf(seedMode, seedValue),
                        timeOf(inactiveMode, inactiveValue),
                    )
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
            stringResource(
                when (mode) {
                    LimitMode.GLOBAL -> R.string.use_global_limit
                    LimitMode.UNLIMITED -> R.string.unlimited
                    LimitMode.CUSTOM -> R.string.custom_label
                }
            )
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
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
    val noneLabel = stringResource(R.string.none_option)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_category)) },
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
                        Text(option.ifBlank { noneLabel })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNew) { Text(stringResource(R.string.new_ellipsis)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
        title = { Text(stringResource(R.string.set_tags)) },
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
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onNew) { Text(stringResource(R.string.new_tag_ellipsis)) }
        },
    )
}
