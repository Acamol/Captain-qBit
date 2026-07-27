package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import qbittorrent.models.RssRule

private val CONTENT_LAYOUTS = listOf("Original", "Subfolder", "NoSubfolder")

private enum class PausedMode {
    GLOBAL,
    PAUSED,
    STARTED,
}

private fun PausedMode.toAddPaused(): Boolean? =
    when (this) {
        PausedMode.GLOBAL -> null
        PausedMode.PAUSED -> true
        PausedMode.STARTED -> false
    }

private fun Boolean?.toPausedMode(): PausedMode =
    when (this) {
        null -> PausedMode.GLOBAL
        true -> PausedMode.PAUSED
        false -> PausedMode.STARTED
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssRuleEditorScreen(appNavigator: AppNavigator, viewModel: RssViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editingName = viewModel.ruleName
    val existing = editingName?.let { state.rules[it] }

    var name by remember { mutableStateOf(editingName.orEmpty()) }
    var rule by remember { mutableStateOf(existing ?: RssRule()) }
    var prefilled by remember { mutableStateOf(false) }
    var showFeedPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var contentLayoutExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (existing != null && !prefilled) {
            prefilled = true
            rule = existing
        }
    }

    val allFeeds = remember(state.items) { state.items.flattenFeeds() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingName != null) "Edit rule" else "New rule") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editingName != null) {
                        IconButton(onClick = { showRemoveConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove rule")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule name") },
                singleLine = true,
                enabled = editingName == null,
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow("Enabled", rule.enabled) { rule = rule.copy(enabled = it) }

            OutlinedTextField(
                value = rule.mustContain,
                onValueChange = { rule = rule.copy(mustContain = it) },
                label = { Text("Must contain") },
                supportingText = {
                    Text("One expression per line (OR); words in a line are ANDed")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rule.mustNotContain,
                onValueChange = { rule = rule.copy(mustNotContain = it) },
                label = { Text("Must not contain") },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Use regex", rule.useRegex) { rule = rule.copy(useRegex = it) }
            OutlinedTextField(
                value = rule.episodeFilter,
                onValueChange = { rule = rule.copy(episodeFilter = it) },
                label = { Text("Episode filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Smart episode filter", rule.smartFilter) {
                rule = rule.copy(smartFilter = it)
            }
            OutlinedTextField(
                value = rule.ignoreDays.toString(),
                onValueChange = { new ->
                    rule = rule.copy(ignoreDays = new.filter(Char::isDigit).toIntOrNull() ?: 0)
                },
                label = { Text("Ignore days after last match") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Add matched torrents as", style = MaterialTheme.typography.titleSmall)
            PausedMode.entries.forEach { mode ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = rule.addPaused.toPausedMode() == mode,
                                onClick = { rule = rule.copy(addPaused = mode.toAddPaused()) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = rule.addPaused.toPausedMode() == mode,
                        onClick = { rule = rule.copy(addPaused = mode.toAddPaused()) },
                    )
                    Text(
                        when (mode) {
                            PausedMode.GLOBAL -> "Use global default"
                            PausedMode.PAUSED -> "Paused"
                            PausedMode.STARTED -> "Started"
                        }
                    )
                }
            }

            OutlinedTextField(
                value = rule.assignedCategory,
                onValueChange = { rule = rule.copy(assignedCategory = it) },
                label = { Text("Category") },
                singleLine = true,
                trailingIcon = {
                    if (state.availableCategories.isNotEmpty()) {
                        IconButton(onClick = { showCategoryPicker = true }) {
                            Icon(Icons.Filled.Category, contentDescription = "Pick category")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rule.savePath,
                onValueChange = { rule = rule.copy(savePath = it) },
                label = { Text("Save path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = contentLayoutExpanded,
                onExpandedChange = { contentLayoutExpanded = it },
            ) {
                OutlinedTextField(
                    value = rule.torrentContentLayout,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Content layout") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = contentLayoutExpanded)
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = contentLayoutExpanded,
                    onDismissRequest = { contentLayoutExpanded = false },
                ) {
                    CONTENT_LAYOUTS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                rule = rule.copy(torrentContentLayout = option)
                                contentLayoutExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showFeedPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Affected feeds (${rule.affectedFeeds.size} of ${allFeeds.size})")
            }

            Button(
                onClick = { viewModel.setRule(name.trim(), rule) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    if (showFeedPicker) {
        FeedPickerDialog(
            feeds = allFeeds,
            selected = rule.affectedFeeds.toSet(),
            onApply = {
                rule = rule.copy(affectedFeeds = it)
                showFeedPicker = false
            },
            onDismiss = { showFeedPicker = false },
        )
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = state.availableCategories,
            onSelect = {
                rule = rule.copy(assignedCategory = it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    if (showRemoveConfirm && editingName != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove rule \"$editingName\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRule(editingName)
                        showRemoveConfirm = false
                        appNavigator.navigate(NavCommand.Back)
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FeedPickerDialog(
    feeds: List<qbittorrent.models.RssFeed>,
    selected: Set<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Affected feeds") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                feeds.forEach { feed ->
                    val isChecked = feed.url in checked.value
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    checked.value =
                                        if (isChecked) checked.value - feed.url
                                        else checked.value + feed.url
                                }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                checked.value =
                                    if (it) checked.value + feed.url else checked.value - feed.url
                            },
                        )
                        Text(feed.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(checked.value.toList()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryPickerDialog(
    categories: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    Text(
                        category,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onSelect(category) }
                                .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
