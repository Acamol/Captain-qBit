package dev.yashgarg.qbit.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
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
    var sortMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.search_title)) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchControls(
                state = state,
                onQueryChange = viewModel::setQuery,
                onSearch = viewModel::search,
                onStop = viewModel::stop,
                onPluginChange = viewModel::selectPlugin,
                onCategoryChange = viewModel::selectCategory,
            )
            SearchBody(
                state = state,
                onAdd = { result ->
                    viewModel.addResult(result)
                    appNavigator.navigate(NavCommand.PopToServer)
                },
                onOpenPlugins = { appNavigator.navigate(NavCommand.OpenSearchPlugins) },
            )
        }
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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(CommonR.string.search_query_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (running) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(CommonR.string.search_stop_action),
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
            val categories = state.categories
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
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
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
) {
    val noPlugins = state.pluginsLoaded && state.enabledPlugins.isEmpty() && state.error == null
    when {
        state.error != null -> CenteredText(state.error, MaterialTheme.colorScheme.error)
        noPlugins ->
            CenteredText(stringResource(CommonR.string.search_no_plugins)) {
                TextButton(onClick = onOpenPlugins) {
                    Text(stringResource(CommonR.string.search_plugins_action))
                }
            }
        state.phase == SearchPhase.IDLE -> CenteredText(stringResource(CommonR.string.search_intro))
        state.results.isEmpty() && state.phase == SearchPhase.RUNNING ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(CommonR.string.search_running),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        state.results.isEmpty() -> CenteredText(stringResource(CommonR.string.search_no_results))
        else -> {
            Column(Modifier.fillMaxSize()) {
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sortedResults, key = { it.fileUrl }) { result ->
                        ResultRow(result, onClick = { onAdd(result) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val unknown = stringResource(CommonR.string.search_unknown_value)
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            result.fileName,
            style = MaterialTheme.typography.bodyLarge,
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
}

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
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
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
