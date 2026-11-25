package dev.yashgarg.qbit.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.utils.toDate
import kotlinx.coroutines.launch
import qbittorrent.models.LogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLogsContent(serverId: Int, viewModel: LogsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) { viewModel.loadServer(serverId) }
    var query by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf<LogSeverity?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Show the "jump to top" button once the newest entries have scrolled out of view.
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    val filtered =
        remember(state.entries, query, severity) {
            filterLogEntries(state.entries, query, severity).asReversed()
        }
    LaunchedEffect(query, severity) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            Column {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(stringResource(CommonR.string.search_log_messages_placeholder))
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription =
                                            stringResource(
                                                CommonR.string.content_description_clear,
                                            ),
                                    )
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        singleLine = true,
                    )
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.refreshing) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(CommonR.string.refresh_action),
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf(null) + LogSeverity.entries) { level ->
                        FilterChip(
                            selected = severity == level,
                            onClick = { severity = if (severity == level) null else level },
                            label = {
                                Text(
                                    stringResource(
                                        level?.labelResource() ?: CommonR.string.all_label,
                                    ),
                                )
                            },
                            leadingIcon =
                                if (severity == level) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                                        )
                                    }
                                } else null,
                        )
                    }
                }
            }
        },
        contentWindowInsets = NoWindowInsets,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null ->
                        Text(
                            state.error!!,
                            Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    filtered.isEmpty() ->
                        Text(
                            stringResource(
                                if (state.entries.isEmpty()) CommonR.string.no_log_entries
                                else CommonR.string.no_matching_entries,
                            ),
                            Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 88.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // qBittorrent returns entries oldest-first; show the newest at the top.
                            itemsIndexed(filtered, key = { _, entry -> entry.id }) { index, entry ->
                                LogRow(entry, index, filtered.size)
                            }
                        }
                }

                // "Back to top" pill, centered at the bottom where the thumb rests after scrolling
                // down.
                JumpToNewestPill(
                    visible = showScrollTop,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpToNewestPill(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        SmallExtendedFloatingActionButton(
            onClick = onClick,
            icon = {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(CommonR.string.jump_to_newest),
                )
            },
            text = { Text(stringResource(CommonR.string.jump_to_newest)) },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry, index: Int, count: Int) {
    val severity = LogSeverity.fromType(entry.type)
    val accent =
        when (severity) {
            LogSeverity.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
            LogSeverity.INFO -> MaterialTheme.colorScheme.primary
            LogSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
            LogSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        }
    val icon =
        when (severity) {
            LogSeverity.NORMAL -> Icons.Filled.Notes
            LogSeverity.INFO -> Icons.Filled.Info
            LogSeverity.WARNING -> Icons.Filled.Warning
            LogSeverity.CRITICAL -> Icons.Filled.Report
        }
    val copy = rememberCopyToClipboard()
    val copiedLogLineMessage = stringResource(CommonR.string.copied_log_line)
    val copyLine = {
        copy("log", "${logTime(entry.timestamp)}  ${entry.message}", copiedLogLineMessage)
    }
    SegmentedListItem(
        onClick = copyLine,
        onLongClick = copyLine,
        onLongClickLabel = stringResource(CommonR.string.copy_log_entry_action),
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Icon(icon, contentDescription = null, tint = accent) },
        overlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(severity.labelResource()), color = accent)
                Text(
                    logTime(entry.timestamp),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        },
    ) {
        Text(entry.message, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun LogSeverity.labelResource(): Int =
    when (this) {
        LogSeverity.NORMAL -> CommonR.string.log_level_normal
        LogSeverity.INFO -> CommonR.string.log_level_info
        LogSeverity.WARNING -> CommonR.string.log_level_warning
        LogSeverity.CRITICAL -> CommonR.string.log_level_critical
    }

// qBittorrent's log timestamp is unix seconds; guard in case a build reports milliseconds.
private fun logTime(timestamp: Long): String =
    (if (timestamp > 1_000_000_000_000L) timestamp / 1000 else timestamp).toDate()
