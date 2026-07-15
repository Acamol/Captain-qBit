package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_HIGH
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_MAXIMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_NORMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_SKIP
import dev.yashgarg.qbit.ui.compose.TorrentContentTreeView
import dev.yashgarg.qbit.ui.compose.isSkipped
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.rememberCopyToClipboard

@Composable
fun FilesTab(
    state: TorrentDetailsState,
    onSetPriority: (List<Int>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Long-press target: first a small action menu, then a priority or path dialog.
    var menuItem by remember { mutableStateOf<ContentTreeItem?>(null) }
    var priorityItem by remember { mutableStateOf<ContentTreeItem?>(null) }
    var pathItem by remember { mutableStateOf<ContentTreeItem?>(null) }

    FilesListView(
        state = state,
        modifier = modifier,
        onNodeLongClick = { menuItem = it },
        // Checkbox / file tap = download on/off; finer priorities live in the long-press menu.
        onToggleDownload = { item ->
            onSetPriority(
                fileIndices(item),
                if (item.isSkipped()) FILE_PRIORITY_NORMAL else FILE_PRIORITY_SKIP,
            )
        },
    )

    menuItem?.let { item ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text(item.name) },
            text = {
                Column {
                    MenuRow(stringResource(CommonR.string.file_menu_priority)) {
                        menuItem = null
                        priorityItem = item
                    }
                    MenuRow(stringResource(CommonR.string.file_menu_path)) {
                        menuItem = null
                        pathItem = item
                    }
                }
            },
            confirmButton = {},
        )
    }

    priorityItem?.let { item ->
        val options =
            listOf(
                FILE_PRIORITY_SKIP to stringResource(CommonR.string.file_priority_skip),
                FILE_PRIORITY_NORMAL to stringResource(CommonR.string.file_priority_normal),
                FILE_PRIORITY_HIGH to stringResource(CommonR.string.file_priority_high),
                FILE_PRIORITY_MAXIMAL to stringResource(CommonR.string.file_priority_maximal),
            )
        val current = commonPriority(item)
        AlertDialog(
            onDismissRequest = { priorityItem = null },
            title = { Text(stringResource(CommonR.string.file_priority_title)) },
            text = {
                Column {
                    options.forEach { (priority, label) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = current == priority,
                                        onClick = {
                                            onSetPriority(fileIndices(item), priority)
                                            priorityItem = null
                                        },
                                    )
                                    .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = current == priority,
                                onClick = {
                                    onSetPriority(fileIndices(item), priority)
                                    priorityItem = null
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { priorityItem = null }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    pathItem?.let { item ->
        val copy = rememberCopyToClipboard()
        val fullPath =
            state.torrentProperties?.savePath?.let { joinPath(it, item.path) } ?: item.path
        AlertDialog(
            onDismissRequest = { pathItem = null },
            title = { Text(if (item.item == null) "Folder path" else "File path") },
            text = { Text(fullPath) },
            confirmButton = {
                TextButton(
                    onClick = {
                        copy("content-path", fullPath, "Copied to clipboard")
                        pathItem = null
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = { TextButton(onClick = { pathItem = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    )
}

@Composable
fun FilesListView(
    state: TorrentDetailsState,
    modifier: Modifier = Modifier,
    onNodeLongClick: (ContentTreeItem) -> Unit = {},
    onToggleDownload: ((ContentTreeItem) -> Unit)? = null,
) {
    if (state.contentLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.contentTree.isEmpty()) {
        Center(modifier) { Text("No content found") }
    } else {
        TorrentContentTreeView(modifier, state.contentTree, onNodeLongClick, onToggleDownload)
    }
}

/** All torrent file indices under this node (itself, for a file). */
private fun fileIndices(item: ContentTreeItem): List<Int> =
    item.item?.let { listOf(it.index) } ?: item.children.orEmpty().flatMap { fileIndices(it) }

/** The single priority shared by every file under this node, or null when they differ. */
private fun commonPriority(item: ContentTreeItem): Int? =
    collectPriorities(item).distinct().singleOrNull()

private fun collectPriorities(item: ContentTreeItem): List<Int> =
    item.item?.let { listOf(it.priority) }
        ?: item.children.orEmpty().flatMap { collectPriorities(it) }

// qBittorrent uses "/" inside torrent paths; match the save path's own separator.
private fun joinPath(base: String, relative: String): String {
    val separator = if (base.contains("\\") && !base.contains("/")) "\\" else "/"
    val normalizedRelative = if (separator == "\\") relative.replace("/", "\\") else relative
    return base.trimEnd('/', '\\') + separator + normalizedRelative
}
