package dev.yashgarg.qbit.ui.serverlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    appNavigator: AppNavigator,
    viewModel: ServerListViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val activeServerId by viewModel.activeServerId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    val haptics = LocalHapticFeedback.current
    val dragState = remember { ServerDragState(haptics) }
    // Only substitute the reordered snapshot while a drag is live - otherwise just render the
    // repository's own order directly, so unrelated list changes (add/delete) show up immediately.
    val displayServers = if (dragState.draggedId != null) dragState.items else servers

    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add server") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { appNavigator.navigate(NavCommand.OpenConfig(serverId = -1)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(displayServers, key = { it.configId }) { server ->
                val isDragged = server.configId == dragState.draggedId
                ServerRow(
                    server = server,
                    active = server.configId == activeServerId,
                    offsetY = if (isDragged) dragState.dragOffsetY else 0f,
                    dragged = isDragged,
                    onClick = { appNavigator.navigate(NavCommand.OpenConfig(server.configId)) },
                    onDelete = { pendingDelete = server },
                    onRowHeightMeasured = { dragState.rowHeightPx = it },
                    onDragStart = { dragState.start(server.configId, servers) },
                    onDrag = { dragState.drag(it) },
                    onDragEnd = {
                        dragState.end { newOrder ->
                            viewModel.reorderServers(newOrder.map { it.configId })
                        }
                    },
                    onDragCancel = { dragState.cancel() },
                )
                HorizontalDivider()
            }
        }
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${server.serverName}\"?") },
            text = { Text("This removes the saved server from the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServer(server.configId)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Drives a drag-to-reorder gesture over the flat server list. Unlike the RSS feed tree, there's no
 * nesting to resolve - dragging just walks [items] up/down by whole rows, ticking a haptic each
 * time the dragged row crosses into a new slot, and [end] hands the resulting order back to
 * persist.
 */
private class ServerDragState(private val haptics: HapticFeedback) {
    var items by mutableStateOf<List<ServerConfig>>(emptyList())
        private set

    var draggedId by mutableStateOf<Int?>(null)
        private set

    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    var rowHeightPx = 0f

    fun start(id: Int, current: List<ServerConfig>) {
        items = current
        draggedId = id
        dragOffsetY = 0f
    }

    fun drag(deltaY: Float) {
        val id = draggedId ?: return
        dragOffsetY += deltaY
        val height = rowHeightPx
        if (height <= 0f) return
        val currentIndex = items.indexOfFirst { it.configId == id }
        if (currentIndex < 0) return
        val shift = (dragOffsetY / height).roundToInt()
        if (shift == 0) return
        val newIndex = (currentIndex + shift).coerceIn(0, items.lastIndex)
        if (newIndex != currentIndex) {
            items = items.toMutableList().apply { add(newIndex, removeAt(currentIndex)) }
            dragOffsetY -= shift * height
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun end(onReorder: (List<ServerConfig>) -> Unit) {
        val wasDragging = draggedId != null
        draggedId = null
        dragOffsetY = 0f
        if (wasDragging) onReorder(items)
    }

    fun cancel() {
        draggedId = null
        dragOffsetY = 0f
    }
}

@Composable
private fun ServerRow(
    server: ServerConfig,
    active: Boolean,
    offsetY: Float,
    dragged: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRowHeightMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .zIndex(if (dragged) 1f else 0f)
                .graphicsLayer { translationY = offsetY }
                .let {
                    if (dragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    else it
                }
                .onSizeChanged { onRowHeightMeasured(it.height.toFloat()) }
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                server.serverName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                serverUrl(server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Surface(
                modifier = Modifier.size(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Spacer(Modifier.size(8.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.pointerInput(server.configId) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                },
        )
    }
}

private fun serverUrl(server: ServerConfig): String = buildString {
    append(server.connectionType.name.lowercase())
    append("://")
    append(server.baseUrl)
    server.port?.let { append(":$it") }
}
