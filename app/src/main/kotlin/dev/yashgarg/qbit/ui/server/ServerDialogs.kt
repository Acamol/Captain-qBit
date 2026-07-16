package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.utils.toHumanReadable

/**
 * Bulk-action pickers and tag/category/server management dialogs shown from the server screen.
 * Native Compose port of the former imperative `ServerActionDialogs` class: one host composable
 * switches on a [ServerDialog] state (hoisted in [ServerScreen]), so opening a dialog is just
 * setting that state and dismissing is clearing it.
 */
sealed interface ServerDialog {
    data class BulkCategory(val hashes: List<String>) : ServerDialog

    data class BulkTags(val hashes: List<String>) : ServerDialog

    /** New-tag text prompt. [forSelection] non-null → add to those torrents; null → create only. */
    data class CreateTag(val forSelection: List<String>?) : ServerDialog

    data object ManageTags : ServerDialog

    data object ManageCategories : ServerDialog

    data class CreateCategory(val returnToManage: Boolean) : ServerDialog

    data class EditCategorySavePath(val name: String, val returnToManage: Boolean) : ServerDialog

    data class ConfirmDeleteCategory(val name: String, val returnToManage: Boolean) : ServerDialog

    data class CategoryLongPress(val name: String) : ServerDialog

    data class TagLongPress(val name: String) : ServerDialog

    data class ConfirmDeleteTag(val name: String) : ServerDialog

    data object Statistics : ServerDialog

    data object SortPicker : ServerDialog

    data object GlobalLimits : ServerDialog

    data object ServerPicker : ServerDialog

    data class ServerLongPress(val server: ServerConfig) : ServerDialog

    data class ConfirmDeleteServer(val server: ServerConfig) : ServerDialog
}

// App-local preset palette for category colors (qBittorrent has no category color of its own).
private val presetColors =
    listOf(
        0xFFEF5350.toInt(),
        0xFFEC407A.toInt(),
        0xFFAB47BC.toInt(),
        0xFF7E57C2.toInt(),
        0xFF5C6BC0.toInt(),
        0xFF42A5F5.toInt(),
        0xFF26A69A.toInt(),
        0xFF66BB6A.toInt(),
        0xFF9CCC65.toInt(),
        0xFFFFCA28.toInt(),
        0xFFFFA726.toInt(),
        0xFFFF7043.toInt(),
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerDialogHost(
    dialog: ServerDialog?,
    onDialogChange: (ServerDialog?) -> Unit,
    viewModel: ServerViewModel,
    appNavigator: AppNavigator,
) {
    if (dialog == null) return

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryColors by viewModel.categoryColors.collectAsStateWithLifecycle()
    val dismiss = { onDialogChange(null) }

    // Color picker layered over the manage/edit dialogs (does not dismiss the one beneath it).
    var colorPickerFor by remember { mutableStateOf<String?>(null) }

    when (dialog) {
        is ServerDialog.BulkCategory -> {
            val options = listOf("") + state.availableCategories
            // Preselect the current category. With multiple torrents, only preselect when they all
            // share one category; a mixed selection stays unset so we don't silently overwrite.
            val currentCategories =
                dialog.hashes.mapNotNull { state.data?.torrents?.get(it)?.category }.toSet()
            val selectedIndex = currentCategories.singleOrNull()?.let { options.indexOf(it) } ?: -1
            SingleChoiceDialog(
                title = "Set category",
                labels = options.map { it.ifBlank { "None" } },
                selectedIndex = selectedIndex,
                onSelect = { i ->
                    viewModel.bulkSetCategory(dialog.hashes, options[i])
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        is ServerDialog.BulkTags -> {
            val tags = state.availableTags
            if (tags.isEmpty()) {
                onDialogChange(ServerDialog.CreateTag(dialog.hashes))
            } else {
                val torrentTags =
                    dialog.hashes
                        .mapNotNull { state.data?.torrents?.get(it) }
                        .flatMap { it.tags }
                        .toSet()
                val initialChecked = remember(tags) { tags.map { it in torrentTags } }
                val checked = remember(tags) { initialChecked.toMutableStateList() }
                AlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text("Set tags") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            tags.forEachIndexed { i, tag ->
                                CheckboxRow(tag, checked[i]) { checked[i] = it }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val toAdd = tags.filterIndexed { i, _ ->
                                    checked[i] && !initialChecked[i]
                                }
                                val toRemove = tags.filterIndexed { i, _ ->
                                    !checked[i] && initialChecked[i]
                                }
                                if (toAdd.isNotEmpty()) viewModel.bulkAddTags(dialog.hashes, toAdd)
                                if (toRemove.isNotEmpty())
                                    viewModel.bulkRemoveTags(dialog.hashes, toRemove)
                                dismiss()
                            }
                        ) {
                            Text("Apply")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = { onDialogChange(ServerDialog.CreateTag(dialog.hashes)) }
                            ) {
                                Text("New tag…")
                            }
                            TextButton(onClick = dismiss) { Text("Cancel") }
                        }
                    },
                )
            }
        }
        is ServerDialog.CreateTag -> {
            TextInputDialog(
                title = "New tag",
                label = "Tag name",
                confirmLabel = "Create",
                validate = { name ->
                    when {
                        name.isBlank() -> "Name cannot be empty"
                        state.availableTags.contains(name) -> "Tag already exists"
                        else -> null
                    }
                },
                onConfirm = { name ->
                    val forSelection = dialog.forSelection
                    if (forSelection != null) viewModel.bulkAddTags(forSelection, listOf(name))
                    else viewModel.createTag(name)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        ServerDialog.ManageTags -> {
            val tags = state.availableTags
            val marked = remember(tags) { tags.map { false }.toMutableStateList() }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Manage tags") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (tags.isEmpty()) {
                            Text("No tags yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        tags.forEachIndexed { i, tag ->
                            CheckboxRow(tag, marked[i]) { marked[i] = it }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toDelete = tags.filterIndexed { i, _ -> marked[i] }
                            if (toDelete.isNotEmpty()) {
                                viewModel.deleteTags(toDelete)
                                dismiss()
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDialogChange(ServerDialog.CreateTag(null)) }) {
                        Text("New tag…")
                    }
                },
            )
        }
        ServerDialog.ManageCategories -> {
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Manage categories") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.availableCategories.forEach { name ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, Modifier.weight(1f))
                                ColorSwatch(
                                    color = categoryColors[name],
                                    onClick = { colorPickerFor = name },
                                )
                                IconButton(
                                    onClick = {
                                        onDialogChange(
                                            ServerDialog.EditCategorySavePath(name, true)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit $name")
                                }
                                IconButton(
                                    onClick = {
                                        onDialogChange(
                                            ServerDialog.ConfirmDeleteCategory(name, true)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete $name")
                                }
                            }
                        }
                        TextButton(
                            onClick = { onDialogChange(ServerDialog.CreateCategory(true)) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("New category", Modifier.padding(start = 4.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
            )
        }
        is ServerDialog.CreateCategory -> {
            val back = {
                if (dialog.returnToManage) onDialogChange(ServerDialog.ManageCategories)
                else dismiss()
            }
            TextInputDialog(
                title = "New category",
                label = "Category name",
                confirmLabel = "Create",
                validate = { name ->
                    when {
                        name.isBlank() -> "Name cannot be empty"
                        state.availableCategories.contains(name) -> "Category already exists"
                        else -> null
                    }
                },
                onConfirm = { name ->
                    viewModel.createCategory(name)
                    back()
                },
                onDismiss = back,
            )
        }
        is ServerDialog.EditCategorySavePath -> {
            val back = {
                if (dialog.returnToManage) onDialogChange(ServerDialog.ManageCategories)
                else dismiss()
            }
            val currentSavePath = state.data?.categories?.get(dialog.name)?.savePath.orEmpty()
            TextInputDialog(
                title = stringResource(CommonR.string.edit_category_title),
                label = stringResource(CommonR.string.save_path_hint),
                initial = currentSavePath,
                confirmLabel = stringResource(CommonR.string.edit),
                validate = { null },
                onConfirm = { savePath ->
                    viewModel.editCategorySavePath(dialog.name, savePath.trim())
                    back()
                },
                onDismiss = back,
                extraContent = {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Color", Modifier.weight(1f))
                        ColorSwatch(
                            color = categoryColors[dialog.name],
                            onClick = { colorPickerFor = dialog.name },
                        )
                    }
                },
            )
        }
        is ServerDialog.ConfirmDeleteCategory -> {
            val back = {
                if (dialog.returnToManage) onDialogChange(ServerDialog.ManageCategories)
                else dismiss()
            }
            ConfirmDialog(
                title = "Delete ${dialog.name}?",
                confirmLabel = stringResource(CommonR.string.delete),
                onConfirm = {
                    viewModel.deleteCategories(listOf(dialog.name))
                    back()
                },
                onDismiss = back,
            )
        }
        is ServerDialog.CategoryLongPress -> {
            ThreeActionDialog(
                title = dialog.name,
                positiveLabel = stringResource(CommonR.string.edit),
                onPositive = {
                    onDialogChange(ServerDialog.EditCategorySavePath(dialog.name, false))
                },
                neutralLabel = stringResource(CommonR.string.delete),
                onNeutral = {
                    onDialogChange(ServerDialog.ConfirmDeleteCategory(dialog.name, false))
                },
                onDismiss = dismiss,
            )
        }
        is ServerDialog.TagLongPress -> {
            ConfirmDialog(
                title = dialog.name,
                confirmLabel = stringResource(CommonR.string.delete),
                onConfirm = { onDialogChange(ServerDialog.ConfirmDeleteTag(dialog.name)) },
                onDismiss = dismiss,
            )
        }
        is ServerDialog.ConfirmDeleteTag -> {
            ConfirmDialog(
                title = "Delete ${dialog.name}?",
                confirmLabel = stringResource(CommonR.string.delete),
                onConfirm = {
                    viewModel.deleteTags(listOf(dialog.name))
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        ServerDialog.Statistics -> {
            val serverState = state.data?.serverState
            if (serverState == null) {
                dismiss()
            } else {
                AlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text("Statistics") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            StatHeader("User statistics")
                            StatRow("All-time upload", serverState.allTimeUpload.toHumanReadable())
                            StatRow(
                                "All-time download",
                                serverState.allTimeDownload.toHumanReadable(),
                            )
                            StatRow("All-time share ratio", serverState.globalShareRatio)
                            StatRow("Session waste", serverState.sessionWaste.toHumanReadable())
                            StatRow("Connected peers", serverState.totalPeerConnections.toString())

                            StatHeader("Cache statistics")
                            StatRow("Read cache hits", "${serverState.readCacheHits}%")
                            StatRow(
                                "Total buffer size",
                                serverState.totalBuffersSize.toLong().toHumanReadable(),
                            )

                            StatHeader("Performance statistics")
                            StatRow("Write cache overload", "${serverState.writeCacheOverload}%")
                            StatRow("Read cache overload", "${serverState.readCacheOverload}%")
                            StatRow("Queued I/O jobs", serverState.queuedIoJobs.toString())
                            StatRow("Average time in queue", "${serverState.averageTimeInQueue} ms")
                            StatRow(
                                "Total queued size",
                                serverState.totalQueuedSize.toLong().toHumanReadable(),
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
                )
            }
        }
        ServerDialog.SortPicker -> {
            val options = SortOption.entries
            val labels = options.map { option ->
                when {
                    option != state.sortOption -> option.label
                    state.sortDirection == SortDirection.ASC -> "↑ ${option.label}"
                    else -> "↓ ${option.label}"
                }
            }
            val dirLabel =
                if (state.sortDirection == SortDirection.ASC) "↑ Ascending" else "↓ Descending"
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Sort by") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        options.forEachIndexed { i, option ->
                            SelectableRow(
                                label = labels[i],
                                selected = option == state.sortOption,
                                onClick = {
                                    viewModel.setSort(option)
                                    dismiss()
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.toggleSortDirection()
                            dismiss()
                        }
                    ) {
                        Text(dirLabel)
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
            )
        }
        ServerDialog.GlobalLimits -> {
            // Shown in KiB/s (qBittorrent's own unit); blank/zero clears the limit.
            var dl by remember {
                mutableStateOf(
                    if (state.globalDownloadLimit > 0) (state.globalDownloadLimit / 1024).toString()
                    else ""
                )
            }
            var ul by remember {
                mutableStateOf(
                    if (state.globalUploadLimit > 0) (state.globalUploadLimit / 1024).toString()
                    else ""
                )
            }
            fun toBytes(kib: String): Int =
                ((kib.toLongOrNull() ?: 0L) * 1024).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Global speed limits") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = dl,
                            onValueChange = { new -> dl = new.filter { it.isDigit() } },
                            singleLine = true,
                            label = { Text("Download (KiB/s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.size(12.dp))
                        OutlinedTextField(
                            value = ul,
                            onValueChange = { new -> ul = new.filter { it.isDigit() } },
                            singleLine = true,
                            label = { Text("Upload (KiB/s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text(
                            "Leave empty for unlimited",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setGlobalLimits(toBytes(dl), toBytes(ul))
                            dismiss()
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
            )
        }
        ServerDialog.ServerPicker -> {
            val servers by viewModel.servers.collectAsStateWithLifecycle()
            val activeId by viewModel.activeServerId.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Servers") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        servers.forEach { server ->
                            val active = server.configId == activeId
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.switchServer(server.configId)
                                            dismiss()
                                        },
                                        onLongClick = {
                                            onDialogChange(ServerDialog.ServerLongPress(server))
                                        },
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (active) "●" else "○",
                                    color =
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Text(server.serverName, Modifier.weight(1f))
                            }
                        }
                        TextButton(
                            onClick = {
                                appNavigator.navigate(NavCommand.OpenConfig(-1))
                                dismiss()
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Add server", Modifier.padding(start = 4.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
            )
        }
        is ServerDialog.ServerLongPress -> {
            ThreeActionDialog(
                title = dialog.server.serverName,
                positiveLabel = stringResource(CommonR.string.edit),
                onPositive = {
                    appNavigator.navigate(NavCommand.OpenConfig(dialog.server.configId))
                    dismiss()
                },
                neutralLabel = stringResource(CommonR.string.delete),
                onNeutral = { onDialogChange(ServerDialog.ConfirmDeleteServer(dialog.server)) },
                onDismiss = dismiss,
            )
        }
        is ServerDialog.ConfirmDeleteServer -> {
            ConfirmDialog(
                title = "Delete ${dialog.server.serverName}?",
                confirmLabel = stringResource(CommonR.string.delete),
                onConfirm = {
                    viewModel.deleteServer(dialog.server.configId)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
    }

    // Layered color picker: sits on top of the manage/edit dialog; picking updates the color and
    // closes only the picker (the categoryColors flow repaints the swatch underneath).
    colorPickerFor?.let { name ->
        val current = categoryColors[name]
        AlertDialog(
            onDismissRequest = { colorPickerFor = null },
            title = { Text("Category color") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorSwatch(
                        color = null,
                        selected = current == null,
                        onClick = {
                            viewModel.setCategoryColor(name, null)
                            colorPickerFor = null
                        },
                    )
                    presetColors.forEach { c ->
                        ColorSwatch(
                            color = c,
                            selected = c == current,
                            onClick = {
                                viewModel.setCategoryColor(name, c)
                                colorPickerFor = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { colorPickerFor = null }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }
}

// ---- Shared building blocks ------------------------------------------------------------------

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    extraContent: @Composable (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text(label) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                extraContent?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val err = validate(value.trim())
                    if (err != null) error = err else onConfirm(value)
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ThreeActionDialog(
    title: String,
    positiveLabel: String,
    onPositive: () -> Unit,
    neutralLabel: String,
    onNeutral: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onPositive) { Text(positiveLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNeutral) { Text(neutralLabel) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { i, label ->
                    SelectableRow(
                        label = label,
                        selected = i == selectedIndex,
                        onClick = { onSelect(i) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ColorSwatch(color: Int?, onClick: () -> Unit, selected: Boolean = false) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.padding(4.dp)
            .size(32.dp)
            .clip(CircleShape)
            .let { if (color != null) it.background(Color(color)) else it }
            .border(
                width = if (selected) 3.dp else if (color == null) 2.dp else 0.dp,
                color = if (selected) onSurface else outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun StatHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun stringResource(resId: Int): String = androidx.compose.ui.res.stringResource(resId)
