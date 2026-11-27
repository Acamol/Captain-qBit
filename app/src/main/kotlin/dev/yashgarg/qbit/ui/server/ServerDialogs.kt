package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.CheckboxRow
import dev.yashgarg.qbit.ui.compose.ConfirmDialog
import dev.yashgarg.qbit.ui.compose.FullScreenEditor
import dev.yashgarg.qbit.ui.compose.MultipleChoiceSheet
import dev.yashgarg.qbit.ui.compose.SelectableRow
import dev.yashgarg.qbit.ui.compose.SingleChoiceSheet
import dev.yashgarg.qbit.ui.compose.TextInputDialog

/**
 * Torrent assignment pickers and server organization editors. Each screen owns its [ServerDialog]
 * state and shares this host for presentation.
 */
sealed interface ServerDialog {
    data class BulkCategory(val hashes: List<String>) : ServerDialog

    data class BulkTags(val hashes: List<String>) : ServerDialog

    /** New-tag text prompt. [forSelection] non-null → add to those torrents; null → create only. */
    data class CreateTag(val forSelection: List<String>?) : ServerDialog

    data object ManageTags : ServerDialog

    data object ManageCategories : ServerDialog

    data object CreateCategory : ServerDialog

    data class EditCategorySavePath(val name: String) : ServerDialog

    data class ConfirmDeleteCategory(val name: String) : ServerDialog

    data object SortPicker : ServerDialog
}

internal fun ServerDialog.reconcileTargets(state: ServerScreenState): ServerDialog? =
    when (this) {
        is ServerDialog.BulkCategory ->
            state.visibleTargets(hashes).takeIf { it.isNotEmpty() }?.let { copy(hashes = it) }
        is ServerDialog.BulkTags ->
            state.visibleTargets(hashes).takeIf { it.isNotEmpty() }?.let { copy(hashes = it) }
        is ServerDialog.CreateTag ->
            if (forSelection == null) this
            else
                state
                    .visibleTargets(forSelection)
                    .takeIf { it.isNotEmpty() }
                    ?.let { copy(forSelection = it) }
        else -> this
    }

@Composable
fun ServerDialogHost(
    dialog: ServerDialog?,
    onDialogChange: (ServerDialog?) -> Unit,
    viewModel: ServerViewModel,
) {
    if (dialog == null) return

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dismiss = { onDialogChange(null) }

    val current = dialog.reconcileTargets(state)
    if (dialog != current) LaunchedEffect(dialog, current) { onDialogChange(current) }
    when (val dialog = current) {
        null -> return
        is ServerDialog.BulkCategory -> {
            val options = listOf("") + state.availableCategories
            // Preselect the current category. With multiple torrents, only preselect when they all
            // share one category; a mixed selection stays unset so we don't silently overwrite.
            val currentCategories =
                dialog.hashes.mapNotNull { state.data?.torrents?.get(it)?.category }.toSet()
            val selectedIndex = currentCategories.singleOrNull()?.let { options.indexOf(it) } ?: -1
            val noneLabel = stringResource(CommonR.string.none_option)
            SingleChoiceSheet(
                title = stringResource(CommonR.string.set_category),
                labels = options.map { it.ifBlank { noneLabel } },
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
            var edits by remember { mutableStateOf(BulkTagEdits()) }
            val torrentTags =
                dialog.hashes
                    .mapNotNull { state.data?.torrents?.get(it) }
                    .flatMap { it.tags }
                    .toSet()
            MultipleChoiceSheet(
                title = stringResource(CommonR.string.set_tags),
                labels = tags,
                selected = tags.indices.filter { edits.checked(tags[it], torrentTags) }.toSet(),
                onToggle = { edits = edits.toggle(tags[it], torrentTags) },
                onApply = {
                    val available = viewModel.uiState.value.availableTags
                    val toAdd = edits.additions(available)
                    val toRemove = edits.removals(available)
                    if (toAdd.isNotEmpty()) viewModel.bulkAddTags(dialog.hashes, toAdd)
                    if (toRemove.isNotEmpty()) viewModel.bulkRemoveTags(dialog.hashes, toRemove)
                    dismiss()
                },
                onDismiss = dismiss,
                newLabel = stringResource(CommonR.string.new_tag_ellipsis),
                onNew = { onDialogChange(ServerDialog.CreateTag(dialog.hashes)) },
            )
        }
        is ServerDialog.CreateTag -> {
            val nameEmptyError = stringResource(CommonR.string.invalid_name)
            val tagExistsError = stringResource(CommonR.string.tag_already_exists)
            TextInputDialog(
                title = stringResource(CommonR.string.new_tag_title),
                label = stringResource(CommonR.string.tag_name_label),
                confirmLabel = stringResource(CommonR.string.create_action),
                validate = { name ->
                    when {
                        name.isBlank() -> nameEmptyError
                        state.availableTags.contains(name) -> tagExistsError
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
            ClassificationManager(categories = false, viewModel = viewModel, onDismiss = dismiss)
        }
        ServerDialog.ManageCategories -> {
            ClassificationManager(categories = true, viewModel = viewModel, onDismiss = dismiss)
        }
        ServerDialog.CreateCategory -> {
            val nameEmptyError = stringResource(CommonR.string.invalid_name)
            val categoryExistsError = stringResource(CommonR.string.category_already_exists)
            val colors by viewModel.categoryColors.collectAsStateWithLifecycle()
            var name by remember { mutableStateOf("") }
            var color by remember { mutableStateOf<Int?>(null) }
            var auto by remember { mutableStateOf(false) }
            var savePath by remember { mutableStateOf("") }
            TextInputDialog(
                title = stringResource(CommonR.string.new_category_title),
                label = stringResource(CommonR.string.category_name_label),
                confirmLabel = stringResource(CommonR.string.create_action),
                validate = { name ->
                    when {
                        name.isBlank() -> nameEmptyError
                        state.availableCategories.contains(name) -> categoryExistsError
                        else -> null
                    }
                },
                onConfirm = { name ->
                    viewModel.createCategory(name.trim(), savePath.trim(), color, auto)
                    dismiss()
                },
                onDismiss = dismiss,
                onValueChange = { name = it.trim() },
                extraContent = {
                    OutlinedTextField(
                        value = savePath,
                        onValueChange = { savePath = it },
                        label = { Text(stringResource(CommonR.string.save_path_optional_label)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    CategoryColorPicker(
                        name = name.ifEmpty { stringResource(CommonR.string.category_name_label) },
                        color = color,
                        auto = auto,
                        onNone = {
                            color = null
                            auto = false
                        },
                        onAuto = { another ->
                            val others = state.availableCategories.mapNotNull { colors[it] }
                            color =
                                CategoryColors.autoPick(
                                    name,
                                    others,
                                    if (another) color else null,
                                )
                            auto = true
                        },
                        onColor = {
                            color = it
                            auto = false
                        },
                        contentPadding = PaddingValues(top = 8.dp),
                        stateKey = Unit,
                    )
                },
            )
        }
        is ServerDialog.EditCategorySavePath -> {
            val currentSavePath = state.data?.categories?.get(dialog.name)?.savePath.orEmpty()
            TextInputDialog(
                title = stringResource(CommonR.string.edit_category_title),
                label = stringResource(CommonR.string.save_path_hint),
                initial = currentSavePath,
                confirmLabel = stringResource(CommonR.string.edit),
                validate = { null },
                onConfirm = { savePath ->
                    viewModel.editCategorySavePath(dialog.name, savePath.trim())
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        is ServerDialog.ConfirmDeleteCategory -> {
            ConfirmDialog(
                title = stringResource(CommonR.string.confirm_delete_named_title, dialog.name),
                confirmLabel = stringResource(CommonR.string.delete),
                onConfirm = {
                    viewModel.deleteCategories(listOf(dialog.name))
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        ServerDialog.SortPicker -> {
            val options = SortOption.entries
            val ascendingLabel = stringResource(CommonR.string.ascending_label)
            val descendingLabel = stringResource(CommonR.string.descending_label)
            SingleChoiceSheet(
                onDismiss = dismiss,
                title = stringResource(CommonR.string.sort_by_title),
                labels = options.map { stringResource(it.labelRes) },
                selectedIndex = options.indexOf(state.sortOption),
                onSelect = { viewModel.setSort(options[it], state.sortDirection) },
                dismissLabel = stringResource(CommonR.string.filters_done_action),
                header = {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).selectableGroup()) {
                        listOf(
                                SortDirection.ASC to ascendingLabel,
                                SortDirection.DESC to descendingLabel,
                            )
                            .forEach { (direction, label) ->
                                Column(Modifier.weight(1f)) {
                                    SelectableRow(label, state.sortDirection == direction) {
                                        viewModel.setSort(state.sortOption, direction)
                                    }
                                }
                            }
                    }
                },
            )
        }
    }
}

@Composable
private fun ClassificationManager(
    categories: Boolean,
    viewModel: ServerViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors by viewModel.categoryColors.collectAsStateWithLifecycle()
    val autoColors by viewModel.autoCategoryColors.collectAsStateWithLifecycle()
    val names = if (categories) state.availableCategories else state.availableTags
    var childDialog by remember { mutableStateOf<ServerDialog?>(null) }
    var paletteFor by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember(names) { mutableStateOf(emptySet<String>()) }
    FullScreenEditor(
        title =
            stringResource(
                if (categories) CommonR.string.manage_categories_title
                else CommonR.string.manage_tags_title,
            ),
        onDismiss = onDismiss,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(
                    if (categories) CommonR.string.manage_categories_description
                    else CommonR.string.manage_tags_description,
                ),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        childDialog =
                            if (categories) ServerDialog.CreateCategory
                            else ServerDialog.CreateTag(null)
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        stringResource(
                            if (categories) CommonR.string.new_category_title
                            else CommonR.string.new_tag_title,
                        ),
                        Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!categories) {
                    TextButton(
                        enabled = selectedTags.isNotEmpty(),
                        onClick = {
                            viewModel.deleteTags(selectedTags.toList())
                            selectedTags = emptySet()
                        },
                    ) {
                        Text(stringResource(CommonR.string.delete))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (names.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                if (categories) CommonR.string.no_content_found
                                else CommonR.string.no_tags_yet,
                            ),
                            Modifier.padding(24.dp),
                        )
                    }
                }
                items(names, key = { it }) { name ->
                    if (categories) {
                        ListItem(
                            onClick = { childDialog = ServerDialog.EditCategorySavePath(name) },
                            supportingContent = {
                                Column {
                                    Text(
                                        state.data?.categories?.get(name)?.savePath?.takeIf {
                                            it.isNotBlank()
                                        } ?: stringResource(CommonR.string.unspecified),
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                textDirection = TextDirection.Ltr,
                                            ),
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                childDialog =
                                                    ServerDialog.EditCategorySavePath(name)
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = null)
                                            Text(
                                                stringResource(
                                                    CommonR.string.edit_save_path_action,
                                                ),
                                                Modifier.padding(start = 8.dp),
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                paletteFor = if (paletteFor == name) null else name
                                            },
                                        ) {
                                            Icon(
                                                Icons.Outlined.Palette,
                                                contentDescription = null,
                                                tint =
                                                    colors[name]?.let {
                                                        Color(CategoryColors.swatch(it))
                                                    } ?: LocalContentColor.current,
                                            )
                                            Text(
                                                stringResource(CommonR.string.color_label),
                                                Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        childDialog = ServerDialog.ConfirmDeleteCategory(name)
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription =
                                            stringResource(
                                                CommonR.string.content_description_delete_named,
                                                name,
                                            ),
                                    )
                                }
                            },
                        ) {
                            Text(name)
                        }
                        if (paletteFor == name) {
                            CategoryColorPicker(
                                name = name,
                                color = colors[name],
                                auto = name in autoColors,
                                onNone = { viewModel.setCategoryColor(name, null) },
                                onAuto = { another ->
                                    viewModel.setCategoryAutoColor(name, another)
                                },
                                onColor = { viewModel.setCategoryColor(name, it) },
                            )
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            CheckboxRow(name, name in selectedTags) { checked ->
                                selectedTags =
                                    if (checked) selectedTags + name else selectedTags - name
                            }
                        }
                    }
                }
            }
        }
        ServerDialogHost(childDialog, { childDialog = it }, viewModel)
    }
}

// ---- Shared building blocks ------------------------------------------------------------------

/**
 * Two KiB/s inputs (download/upload) for a pair of speed limits. Values are passed in/out as
 * bytes/s (0 = unlimited); the fields display and accept KiB/s, matching qBittorrent's own dialogs.
 */
@Composable
internal fun SpeedLimitsDialog(
    title: String,
    baseline: SpeedLimits,
    onConfirm: (SpeedLimitChanges) -> Unit,
    onDismiss: () -> Unit,
) {
    var dl by remember { mutableStateOf(baseline.downloadText) }
    var ul by remember { mutableStateOf(baseline.uploadText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = dl,
                    onValueChange = { new -> dl = new.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text(stringResource(CommonR.string.download_kib_per_sec)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = ul,
                    onValueChange = { new -> ul = new.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text(stringResource(CommonR.string.upload_kib_per_sec)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    stringResource(CommonR.string.leave_empty_for_unlimited),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val changes = baseline.changes(dl, ul)
                    if (changes.download != null || changes.upload != null) onConfirm(changes)
                    onDismiss()
                },
            ) {
                Text(stringResource(CommonR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

@Composable
private fun stringResource(resId: Int): String = androidx.compose.ui.res.stringResource(resId)
