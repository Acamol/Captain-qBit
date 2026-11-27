package dev.yashgarg.qbit.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.FormSection
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
import dev.yashgarg.qbit.utils.toHumanReadable
import qbittorrent.models.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(appNavigator: AppNavigator, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    // The ViewModel outlives a trip to the plugins screen, so the plugin list is re-read on every
    // resume to pick up plugins enabled, installed or removed there.
    LifecycleResumeEffect(viewModel) {
        viewModel.loadPlugins()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(CommonR.string.search_title),
                        style = MaterialTheme.typography.headlineMedium,
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
                    TooltipIconButton(
                        label = stringResource(CommonR.string.search_plugins_action),
                        icon = Icons.Filled.Extension,
                        onClick = { appNavigator.navigate(NavCommand.OpenSearchPlugins) },
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = stringResource(CommonR.string.sort),
                        icon = Icons.AutoMirrored.Filled.Sort,
                        onClick = { sortMenuOpen = true },
                        position = TooltipAnchorPosition.Below,
                    )
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        SearchSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label()) },
                                onClick = {
                                    viewModel.setSort(sort)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SearchBody(
            state = state,
            modifier = Modifier.padding(padding),
            onAdd = { result ->
                viewModel.addResult(result)
                appNavigator.navigate(NavCommand.PopToServer)
            },
            onOpenPlugins = { appNavigator.navigate(NavCommand.OpenSearchPlugins) },
            controls = {
                SearchControls(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onSearch = {
                        viewModel.search()
                        keyboard?.hide()
                    },
                    onStop = viewModel::stop,
                    onPluginChange = viewModel::selectPlugin,
                    onCategoryChange = viewModel::selectCategory,
                )
            },
        )
    }
}

@Composable
private fun SearchControls(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onStop: () -> Unit,
    onPluginChange: (String?) -> Unit,
    onCategoryChange: (String) -> Unit,
) {
    val running = state.phase == SearchPhase.RUNNING
    FormSection(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        highlighted = true,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(CommonR.string.search_query_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription =
                                stringResource(CommonR.string.content_description_clear),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val allPlugins = stringResource(CommonR.string.search_all_enabled_plugins)
            Dropdown(
                label = stringResource(CommonR.string.search_plugin_label),
                selected =
                    state.selectedPlugin?.let { name ->
                        state.plugins.firstOrNull { it.name == name }?.fullName ?: name
                    } ?: allPlugins,
                options = listOf<String?>(null) + state.enabledPlugins.map { it.name },
                optionLabel = { name ->
                    if (name == null) allPlugins
                    else state.plugins.firstOrNull { it.name == name }?.fullName ?: name
                },
                onSelect = onPluginChange,
                modifier = Modifier.weight(1f),
            )
            val allCategories = stringResource(CommonR.string.search_all_categories)
            val categories = remember(state.plugins, state.selectedPlugin) { state.categories }
            Dropdown(
                label = stringResource(CommonR.string.search_category_label),
                selected =
                    categories.firstOrNull { it.first == state.category }?.second ?: allCategories,
                options = listOf(SearchState.CATEGORY_ALL) + categories.map { it.first },
                optionLabel = { id ->
                    if (id == SearchState.CATEGORY_ALL) allCategories
                    else categories.firstOrNull { it.first == id }?.second ?: id
                },
                onSelect = { it?.let(onCategoryChange) },
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = if (running) onStop else onSearch,
            modifier = Modifier.fillMaxWidth(),
            enabled = running || state.query.isNotBlank(),
        ) {
            Icon(
                if (running) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = null,
            )
            Text(
                stringResource(
                    if (running) CommonR.string.search_stop_action else CommonR.string.search_title,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .focusable(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchBody(
    state: SearchState,
    onAdd: (SearchResult) -> Unit,
    onOpenPlugins: () -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
) {
    val noPlugins = state.pluginsLoaded && state.enabledPlugins.isEmpty() && state.error == null
    // Not keyed by link: different plugins can return the same one.
    val sorted = remember(state.results, state.sort) { state.results.sortedBy(state.sort) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "controls") { controls() }
        when {
            state.error != null ->
                item {
                    CenteredText(state.error, MaterialTheme.colorScheme.error)
                }
            noPlugins ->
                item {
                    CenteredText(stringResource(CommonR.string.search_no_plugins)) {
                        TextButton(onClick = onOpenPlugins) {
                            Text(stringResource(CommonR.string.search_plugins_action))
                        }
                    }
                }
            state.phase == SearchPhase.IDLE ->
                item {
                    CenteredText(stringResource(CommonR.string.search_intro))
                }
            state.results.isEmpty() && state.phase == SearchPhase.RUNNING ->
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(CommonR.string.search_running),
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            state.results.isEmpty() ->
                item {
                    CenteredText(stringResource(CommonR.string.search_no_results))
                }
            else -> {
                item(key = "result-count") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(
                                CommonR.plurals.search_results_count,
                                state.results.size,
                                state.results.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (state.phase == SearchPhase.RUNNING) {
                            CircularProgressIndicator(
                                Modifier.padding(start = 12.dp).size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                items(sorted) { result ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ResultRow(result, onClick = { onAdd(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val unknown = stringResource(CommonR.string.search_unknown_value)
    val uriHandler = LocalUriHandler.current
    // Plugins also report problems as a result row (e.g. Jackett's missing API key) whose
    // description page explains the fix, so every row with a web page offers to open it.
    val descriptionPage = result.descrLink.takeIf { it.isWebLink() }
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                result.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    CommonR.string.search_result_seeders_leechers,
                    result.nbSeeders.takeIf { it >= 0 }?.toString() ?: unknown,
                    result.nbLeechers.takeIf { it >= 0 }?.toString() ?: unknown,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(
                        result.fileSize.takeIf { it >= 0 }?.toHumanReadable(),
                        (result.engineName ?: result.siteUrl).takeIf { !it.isNullOrBlank() },
                    )
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (descriptionPage != null) {
            IconButton(onClick = { runCatching { uriHandler.openUri(descriptionPage) } }) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription =
                        stringResource(CommonR.string.search_open_description_page),
                )
            }
        }
    }
}

// Descriptions come from plugin code on the server, so only hand web links to the browser.
private fun String.isWebLink(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

@Composable
private fun CenteredText(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(32.dp),
            )
        }
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
        action()
    }
}

@Composable
private fun SearchSort.label(): String =
    stringResource(
        when (this) {
            SearchSort.SEEDERS -> CommonR.string.search_sort_seeders
            SearchSort.SIZE -> CommonR.string.search_sort_size
            SearchSort.NAME -> CommonR.string.search_sort_name
        },
    )
