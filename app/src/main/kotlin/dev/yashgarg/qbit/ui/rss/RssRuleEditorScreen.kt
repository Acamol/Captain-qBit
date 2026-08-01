package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
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
    var categoryExpanded by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var contentLayoutExpanded by remember { mutableStateOf(false) }
    var loadingMatches by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(existing) {
        if (existing != null && !prefilled) {
            prefilled = true
            rule = existing
        }
    }

    // Only actually shown for a failed save - a successful one navigates back immediately below,
    // so there's no active collector left on this screen to display it.
    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    val allFeeds = remember(state.items) { state.items.flattenFeeds() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (editingName != null) CommonR.string.edit_rule_title
                            else CommonR.string.new_rule_action
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(CommonR.string.content_description_back),
                        )
                    }
                },
                actions = {
                    if (editingName != null) {
                        TooltipIconButton(
                            label = stringResource(CommonR.string.remove_rule_action),
                            icon = Icons.Filled.Delete,
                            onClick = { showRemoveConfirm = true },
                            position = TooltipAnchorPosition.Below,
                        )
                    }
                },
            )
        },
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
                label = { Text(stringResource(CommonR.string.rule_name_label)) },
                singleLine = true,
                enabled = editingName == null,
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(stringResource(CommonR.string.enabled_label), rule.enabled) {
                rule = rule.copy(enabled = it)
            }

            OutlinedTextField(
                value = rule.mustContain,
                onValueChange = { rule = rule.copy(mustContain = it) },
                label = { Text(stringResource(CommonR.string.must_contain_label)) },
                supportingText = {
                    Text(stringResource(CommonR.string.must_contain_supporting_text))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rule.mustNotContain,
                onValueChange = { rule = rule.copy(mustNotContain = it) },
                label = { Text(stringResource(CommonR.string.must_not_contain_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(CommonR.string.use_regex_label), rule.useRegex) {
                rule = rule.copy(useRegex = it)
            }
            OutlinedTextField(
                value = rule.episodeFilter,
                onValueChange = { rule = rule.copy(episodeFilter = it) },
                label = { Text(stringResource(CommonR.string.episode_filter_label)) },
                supportingText = {
                    Text(stringResource(CommonR.string.episode_filter_supporting_text))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                stringResource(CommonR.string.smart_episode_filter_label),
                rule.smartFilter,
                subtitle = stringResource(CommonR.string.smart_episode_filter_subtitle),
            ) {
                rule = rule.copy(smartFilter = it)
            }
            OutlinedTextField(
                value = rule.ignoreDays.toString(),
                onValueChange = { new ->
                    rule = rule.copy(ignoreDays = new.filter(Char::isDigit).toIntOrNull() ?: 0)
                },
                label = { Text(stringResource(CommonR.string.ignore_days_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(CommonR.string.add_matched_torrents_as_label),
                style = MaterialTheme.typography.titleSmall,
            )
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
                        stringResource(
                            when (mode) {
                                PausedMode.GLOBAL -> CommonR.string.use_global_default_label
                                PausedMode.PAUSED -> CommonR.string.paused
                                PausedMode.STARTED -> CommonR.string.started_label
                            }
                        )
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = rule.assignedCategory,
                    onValueChange = { rule = rule.copy(assignedCategory = it) },
                    label = { Text(stringResource(CommonR.string.category_pick_or_type_label)) },
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                val suggestions = state.availableCategories.filter { it.isNotBlank() }
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        suggestions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    rule = rule.copy(assignedCategory = option)
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = rule.savePath,
                onValueChange = { rule = rule.copy(savePath = it) },
                label = { Text(stringResource(CommonR.string.save_path_hint)) },
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
                    label = { Text(stringResource(CommonR.string.content_layout_label)) },
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
                Text(
                    stringResource(
                        CommonR.string.affected_feeds_count,
                        rule.affectedFeeds.size,
                        allFeeds.size,
                    )
                )
            }

            if (editingName != null) {
                OutlinedButton(
                    onClick = {
                        loadingMatches = true
                        viewModel.loadMatchingArticles(editingName) { result ->
                            loadingMatches = false
                            testResult = result
                        }
                    },
                    enabled = !loadingMatches,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (loadingMatches) CommonR.string.loading_ellipsis
                            else CommonR.string.view_matching_articles_action
                        )
                    )
                }
                Text(
                    stringResource(CommonR.string.reflects_last_saved_version_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    viewModel.setRule(name.trim(), rule) {
                        appNavigator.navigate(NavCommand.Back)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(CommonR.string.save_cfg))
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

    testResult?.let { matches ->
        MatchingArticlesResultDialog(
            matches = matches,
            feeds = allFeeds,
            onDismiss = { testResult = null },
        )
    }

    if (showRemoveConfirm && editingName != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text(stringResource(CommonR.string.remove_rule_confirm_title, editingName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRule(editingName)
                        showRemoveConfirm = false
                        appNavigator.navigate(NavCommand.Back)
                    }
                ) {
                    Text(stringResource(CommonR.string.remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Shared by the rule editor's own button and the Rules list's per-row action. */
@Composable
fun MatchingArticlesResultDialog(
    matches: Map<String, List<String>>,
    feeds: List<qbittorrent.models.RssFeed>,
    onDismiss: () -> Unit,
) {
    val nonEmpty = matches.filterValues { it.isNotEmpty() }
    val totalMatches = nonEmpty.values.sumOf { it.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (totalMatches == 0) stringResource(CommonR.string.no_matches_yet_title)
                else stringResource(CommonR.string.matching_articles_count, totalMatches)
            )
        },
        text = {
            if (totalMatches == 0) {
                Text(stringResource(CommonR.string.rule_no_matches_message))
            } else {
                // A loosely-filtered rule spanning many feeds can match hundreds of articles -
                // LazyColumn only composes the rows actually scrolled into view.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    nonEmpty.forEach { (feedUrl, titles) ->
                        val feedName = feeds.firstOrNull { it.url == feedUrl }?.name ?: feedUrl
                        item { Text(feedName, style = MaterialTheme.typography.titleSmall) }
                        items(titles) { title ->
                            Text("• $title", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.close_action)) }
        },
    )
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
        title = { Text(stringResource(CommonR.string.affected_feeds_title)) },
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
            TextButton(onClick = { onApply(checked.value.toList()) }) {
                Text(stringResource(CommonR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}
