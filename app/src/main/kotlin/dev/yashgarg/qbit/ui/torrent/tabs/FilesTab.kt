package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.ActionSheet
import dev.yashgarg.qbit.ui.compose.ActionSheetItem
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.ContentSearchField
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_HIGH
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_MAXIMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_NORMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_SKIP
import dev.yashgarg.qbit.ui.compose.TextInputDialog
import dev.yashgarg.qbit.ui.compose.TorrentContentTreeView
import dev.yashgarg.qbit.ui.compose.allFileIndices
import dev.yashgarg.qbit.ui.compose.isSkipped
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.filterContentTree
import dev.yashgarg.qbit.utils.rememberCopyToClipboard

@Composable
fun FilesTab(
    state: TorrentDetailsState,
    onSetPriority: (List<Int>, Int) -> Unit,
    onRename: (ContentTreeItem, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuItem by remember { mutableStateOf<ContentTreeItem?>(null) }
    var renameItem by remember { mutableStateOf<ContentTreeItem?>(null) }

    Column(modifier) {
        ContentSearchField(
            query = query,
            onQueryChange = { query = it },
            onClose = { query = "" },
            modifier = Modifier.padding(top = 12.dp),
            autoFocus = false,
            persistent = true,
        )
        state.contentError?.let { message ->
            Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        FilesListView(
            state = state,
            query = query,
            modifier = Modifier.weight(1f),
            onNodeLongClick = { menuItem = it },
            onFileClick = { menuItem = it },
            onToggleDownload = { item ->
                onSetPriority(
                    fileIndices(item),
                    if (item.isSkipped()) FILE_PRIORITY_NORMAL else FILE_PRIORITY_SKIP,
                )
            },
        )
    }

    menuItem?.let { item ->
        val copy = rememberCopyToClipboard()
        val fullPath =
            state.torrentProperties?.savePath?.let { joinPath(it, item.path) } ?: item.path
        val copied = stringResource(CommonR.string.clipboard_copied)
        val current = commonPriority(item)
        val options =
            listOf(
                FILE_PRIORITY_SKIP to stringResource(CommonR.string.file_priority_skip),
                FILE_PRIORITY_NORMAL to stringResource(CommonR.string.file_priority_normal),
                FILE_PRIORITY_HIGH to stringResource(CommonR.string.file_priority_high),
                FILE_PRIORITY_MAXIMAL to stringResource(CommonR.string.file_priority_maximal),
            )
        ActionSheet(
            onDismissRequest = { menuItem = null },
            title = item.name,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (item.item == null) CommonR.string.folder_path_title
                                else CommonR.string.file_path_title,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                fullPath,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = TextDirection.Ltr,
                                    ),
                            )
                        }
                    }
                    IconButton(onClick = { copy("content-path", fullPath, copied) }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(CommonR.string.copy_action),
                        )
                    }
                }
                Text(
                    stringResource(CommonR.string.file_priority_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { (priority, label) ->
                        FilterChip(
                            selected = current == priority,
                            onClick = {
                                onSetPriority(fileIndices(item), priority)
                                menuItem = null
                            },
                            label = { Text(label) },
                            leadingIcon =
                                if (current == priority) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else null,
                        )
                    }
                }
            }
            ActionSheetItem(
                stringResource(CommonR.string.rename),
                Icons.Filled.DriveFileRenameOutline,
            ) {
                menuItem = null
                renameItem = item
            }
        }
    }

    renameItem?.let { item ->
        TextInputDialog(
            title =
                stringResource(
                    if (item.item == null) CommonR.string.rename_folder_title
                    else CommonR.string.rename_file_title,
                ),
            confirmLabel = stringResource(CommonR.string.rename),
            validate = { null },
            initial = item.name,
            onConfirm = {
                onRename(item, it)
                renameItem = null
            },
            onDismiss = { renameItem = null },
        )
    }
}

@Composable
fun FilesListView(
    state: TorrentDetailsState,
    query: String,
    modifier: Modifier = Modifier,
    onNodeLongClick: (ContentTreeItem) -> Unit = {},
    onToggleDownload: ((ContentTreeItem) -> Unit)? = null,
    onFileClick: ((ContentTreeItem) -> Unit)? = null,
) {
    val visible = remember(state.contentTree, query) { filterContentTree(state.contentTree, query) }
    if (state.contentLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.contentTree.isEmpty()) {
        if (state.contentError == null) {
            Center(modifier) { Text(stringResource(CommonR.string.no_content_found)) }
        }
    } else if (visible.isEmpty()) {
        Center(modifier) { Text(stringResource(CommonR.string.no_files_match)) }
    } else {
        TorrentContentTreeView(
            modifier,
            visible,
            onNodeLongClick,
            onToggleDownload,
            filterKey = query.trim(),
            onFileClick = onFileClick,
        )
    }
}

/** All torrent file indices under this node (itself, for a file). */
private fun fileIndices(item: ContentTreeItem): List<Int> = item.allFileIndices()

/** The single priority shared by every file under this node, or null when they differ. */
private fun commonPriority(item: ContentTreeItem): Int? =
    collectPriorities(item).distinct().singleOrNull()

private fun collectPriorities(item: ContentTreeItem): List<Int> {
    val node = item.whole()
    return node.item?.let { listOf(it.priority) }
        ?: node.children.orEmpty().flatMap { collectPriorities(it) }
}

// qBittorrent uses "/" inside torrent paths; match the save path's own separator.
private fun joinPath(base: String, relative: String): String {
    val separator = if (base.contains("\\") && !base.contains("/")) "\\" else "/"
    val normalizedRelative = if (separator == "\\") relative.replace("/", "\\") else relative
    return base.trimEnd('/', '\\') + separator + normalizedRelative
}
