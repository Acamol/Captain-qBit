package dev.yashgarg.qbit.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.utils.toDate
import kotlinx.coroutines.launch
import qbittorrent.models.LogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(appNavigator: AppNavigator, viewModel: LogsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Show the "jump to top" button once the newest entries have scrolled out of view.
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    val filtered =
        if (query.isBlank()) state.entries
        else state.entries.filter { it.message.contains(query.trim(), ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server logs") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }
                    ) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Search log messages") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            Box(Modifier.fillMaxSize()) {
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
                            if (state.entries.isEmpty()) "No log entries"
                            else "No matching entries",
                            Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // qBittorrent returns entries oldest-first; show the newest at the top.
                            items(filtered.asReversed(), key = { it.id }) { entry ->
                                LogCard(entry)
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
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Jump to newest", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogCard(entry: LogEntry) {
    val (label, accent) = logLevel(entry.type)
    val copy = rememberCopyToClipboard()
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        copy(
                            "log",
                            "${logTime(entry.timestamp)}  ${entry.message}",
                            "Copied log line",
                        )
                    },
                )
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Severity accent stripe (clipped to the card's rounded corners).
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        logTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Maps a qBittorrent log [LogEntry.type] bitmask to a display label and accent colour. */
@Composable
private fun logLevel(type: Int): Pair<String, Color> =
    when (type) {
        LogEntry.TYPE_CRITICAL -> "CRITICAL" to MaterialTheme.colorScheme.error
        LogEntry.TYPE_WARNING -> "WARNING" to Color(0xFFFFA726)
        LogEntry.TYPE_INFO -> "INFO" to MaterialTheme.colorScheme.primary
        else -> "NORMAL" to MaterialTheme.colorScheme.onSurfaceVariant
    }

// qBittorrent's log timestamp is unix seconds; guard in case a build reports milliseconds.
private fun logTime(timestamp: Long): String =
    (if (timestamp > 1_000_000_000_000L) timestamp / 1000 else timestamp).toDate()
