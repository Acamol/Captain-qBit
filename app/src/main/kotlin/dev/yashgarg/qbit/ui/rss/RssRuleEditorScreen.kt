package dev.yashgarg.qbit.ui.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.FormSection
import dev.yashgarg.qbit.ui.compose.FormSwitch
import dev.yashgarg.qbit.ui.compose.FullScreenEditor
import dev.yashgarg.qbit.ui.compose.MultipleChoiceSheet
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
import qbittorrent.models.RssRule

private val CONTENT_LAYOUTS = listOf(null, "Original", "Subfolder", "NoSubfolder")

@Composable
private fun contentLayoutLabel(layout: String?): String =
    when (layout) {
        null -> stringResource(CommonR.string.use_global_default_label)
        "Original" -> stringResource(CommonR.string.content_layout_original)
        "Subfolder" -> stringResource(CommonR.string.content_layout_subfolder)
        "NoSubfolder" -> stringResource(CommonR.string.content_layout_no_subfolder)
        else -> layout
    }

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

    var name by remember(viewModel) { mutableStateOf(editingName.orEmpty()) }
    var draft by remember(viewModel) { mutableStateOf<RssRule?>(null) }
    val editorRule =
        if (editingName == null) draft ?: remember { RssRule() } else existing?.let { draft ?: it }
    var showFeedPicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var contentLayoutExpanded by remember { mutableStateOf(false) }
    var loadingMatches by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Only actually shown for a failed save - a successful one navigates back immediately below,
    // so there's no active collector left on this screen to display it.
    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    val allFeeds = remember(state.items) { state.items.flattenFeeds().distinctBy { it.url } }
    val listedUrls = remember(allFeeds) { allFeeds.map { it.url } }
    val canEditExisting = existing != null && state.rulesLoad == RssLoad.Ready

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = {
                        if (editorRule != null) {
                            viewModel.saveEditorRule(name, editorRule) {
                                appNavigator.navigate(NavCommand.Back)
                            }
                        }
                    },
                    enabled = editorRule != null && state.canSaveRule(editingName, name),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(stringResource(CommonR.string.save_cfg))
                }
            }
        },
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (editingName != null) CommonR.string.edit_rule_title
                            else CommonR.string.new_rule_action,
                        ),
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
                    if (canEditExisting) {
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
        if (editorRule == null || (editingName != null && !canEditExisting)) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                when (val load = state.rulesLoad) {
                    RssLoad.Loading,
                    RssLoad.Refreshing -> CircularProgressIndicator()
                    is RssLoad.Failed -> RssLoadError(load.message, viewModel::refreshRules)
                    RssLoad.Ready ->
                        RssLoadError(
                            stringResource(CommonR.string.rss_rule_missing),
                            viewModel::refreshRules,
                        )
                }
            }
            return@Scaffold
        }
        val rule = editorRule
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (editingName == null) {
                when (val load = state.rulesLoad) {
                    is RssLoad.Failed -> RssLoadError(load.message, viewModel::refreshRules)
                    RssLoad.Loading,
                    RssLoad.Refreshing -> CircularProgressIndicator()
                    RssLoad.Ready -> Unit
                }
            }
            FormSection {
                val duplicate = editingName == null && name.trim() in state.rules
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(CommonR.string.rule_name_label)) },
                    singleLine = true,
                    enabled = editingName == null,
                    isError = duplicate,
                    supportingText =
                        if (duplicate)
                            ({ Text(stringResource(CommonR.string.rss_rule_name_in_use)) })
                        else null,
                    modifier = Modifier.fillMaxWidth(),
                )

                FormSwitch(stringResource(CommonR.string.enabled_label), rule.enabled) {
                    draft = rule.copy(enabled = it)
                }
            }

            FormSection(
                title = stringResource(CommonR.string.matching_section_title),
                icon = Icons.Filled.FilterAlt,
            ) {
                OutlinedTextField(
                    value = rule.mustContain,
                    onValueChange = { draft = rule.copy(mustContain = it) },
                    label = { Text(stringResource(CommonR.string.must_contain_label)) },
                    supportingText = {
                        Text(stringResource(CommonR.string.must_contain_supporting_text))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rule.mustNotContain,
                    onValueChange = { draft = rule.copy(mustNotContain = it) },
                    label = { Text(stringResource(CommonR.string.must_not_contain_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FormSwitch(stringResource(CommonR.string.use_regex_label), rule.useRegex) {
                    draft = rule.copy(useRegex = it)
                }
                OutlinedTextField(
                    value = rule.episodeFilter,
                    onValueChange = { draft = rule.copy(episodeFilter = it) },
                    label = { Text(stringResource(CommonR.string.episode_filter_label)) },
                    supportingText = {
                        Text(stringResource(CommonR.string.episode_filter_supporting_text))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormSwitch(
                    stringResource(CommonR.string.smart_episode_filter_label),
                    rule.smartFilter,
                    subtitle = stringResource(CommonR.string.smart_episode_filter_subtitle),
                ) {
                    draft = rule.copy(smartFilter = it)
                }
                OutlinedTextField(
                    value = rule.ignoreDays.toString(),
                    onValueChange = { new ->
                        draft = rule.copy(ignoreDays = new.filter(Char::isDigit).toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(CommonR.string.ignore_days_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection(
                title = stringResource(CommonR.string.download_options_section_title),
                icon = Icons.Filled.Tune,
            ) {
                Text(
                    stringResource(CommonR.string.add_matched_torrents_as_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(Modifier.selectableGroup()) {
                    PausedMode.entries.forEach { mode ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = rule.addPaused.toPausedMode() == mode,
                                        role = Role.RadioButton,
                                        onClick = {
                                            draft = rule.copy(addPaused = mode.toAddPaused())
                                        },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = rule.addPaused.toPausedMode() == mode,
                                onClick = null,
                            )
                            Text(
                                stringResource(
                                    when (mode) {
                                        PausedMode.GLOBAL -> CommonR.string.use_global_default_label
                                        PausedMode.PAUSED -> CommonR.string.paused
                                        PausedMode.STARTED -> CommonR.string.started_label
                                    },
                                ),
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = rule.assignedCategory,
                        onValueChange = { draft = rule.copy(assignedCategory = it) },
                        label = {
                            Text(stringResource(CommonR.string.category_pick_or_type_label))
                        },
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
                                        draft = rule.copy(assignedCategory = option)
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = rule.savePath,
                    onValueChange = { draft = rule.copy(savePath = it) },
                    label = { Text(stringResource(CommonR.string.save_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = contentLayoutExpanded,
                    onExpandedChange = { contentLayoutExpanded = it },
                ) {
                    OutlinedTextField(
                        value = contentLayoutLabel(rule.torrentContentLayout),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(CommonR.string.content_layout_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = contentLayoutExpanded,
                            )
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
                                text = { Text(contentLayoutLabel(option)) },
                                onClick = {
                                    draft = rule.copy(torrentContentLayout = option)
                                    contentLayoutExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showFeedPicker = true },
                    enabled = state.itemsLoad == RssLoad.Ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            CommonR.string.affected_feeds_count,
                            rule.affectedFeeds.distinct().size,
                            (listedUrls + rule.affectedFeeds).distinct().size,
                        ),
                    )
                }
                (state.itemsLoad as? RssLoad.Failed)?.let {
                    RssLoadError(it.message, viewModel::refreshItems)
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
                                else CommonR.string.view_matching_articles_action,
                            ),
                        )
                    }
                    Text(
                        stringResource(CommonR.string.reflects_last_saved_version_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (
        showFeedPicker &&
            state.itemsLoad == RssLoad.Ready &&
            editorRule != null &&
            (editingName == null || canEditExisting)
    ) {
        FeedPickerDialog(
            feeds = allFeeds,
            selected = editorRule.affectedFeeds.toSet(),
            onApply = { checked ->
                viewModel.uiState.value
                    .applyFeedSelection(editorRule.affectedFeeds, listedUrls, checked)
                    ?.let { draft = editorRule.copy(affectedFeeds = it) }
                showFeedPicker = false
            },
            onDismiss = { showFeedPicker = false },
        )
    }

    testResult?.let { matches ->
        MatchingArticlesResultsScreen(
            matches = matches,
            feeds = allFeeds,
            onDismiss = { testResult = null },
        )
    }

    if (showRemoveConfirm && editingName != null && canEditExisting) {
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
                    },
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

/** Shared by the rule editor's own button and the Rules list's per-row action. */
@Composable
fun MatchingArticlesResultsScreen(
    matches: Map<String, List<String>>,
    feeds: List<qbittorrent.models.RssFeed>,
    onDismiss: () -> Unit,
) {
    val nonEmpty = matches.filterValues { it.isNotEmpty() }
    val totalMatches = nonEmpty.values.sumOf { it.size }
    FullScreenEditor(
        onDismiss = onDismiss,
        title =
            if (totalMatches == 0) stringResource(CommonR.string.no_matches_yet_title)
            else
                pluralStringResource(
                    CommonR.plurals.matching_articles_count,
                    totalMatches,
                    totalMatches,
                ),
    ) { padding ->
        if (totalMatches == 0) {
            Text(
                stringResource(CommonR.string.rule_no_matches_message),
                Modifier.padding(padding).padding(24.dp),
            )
        } else {
            // A loosely-filtered rule spanning many feeds can match hundreds of articles -
            // LazyColumn only composes the rows actually scrolled into view.
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                nonEmpty.forEach { (feedUrl, titles) ->
                    val feedName = feeds.firstOrNull { it.url == feedUrl }?.name ?: feedUrl
                    item {
                        Text(
                            feedName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                    items(titles) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPickerDialog(
    feeds: List<qbittorrent.models.RssFeed>,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    MultipleChoiceSheet(
        title = stringResource(CommonR.string.affected_feeds_title),
        labels = feeds.map { it.name },
        initialSelected = feeds.indices.filter { feeds[it].url in selected }.toSet(),
        onApply = { checked -> onApply(checked.map { feeds[it].url }.toSet()) },
        onDismiss = onDismiss,
    )
}
