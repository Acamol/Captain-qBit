package dev.yashgarg.qbit.ui.server

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.logs.ServerLogsContent
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.ui.settings.ServerOptionsContent
import dev.yashgarg.qbit.utils.friendlyMessage
import dev.yashgarg.qbit.utils.rememberFriendlyMessageResolver
import dev.yashgarg.qbit.utils.toHumanReadable
import qbittorrent.models.ServerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerOverviewScreen(
    appNavigator: AppNavigator,
    viewModel: ServerViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serverId by viewModel.activeServerId.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var managementDialog by remember(serverId) { mutableStateOf<ServerDialog?>(null) }

    LaunchedEffect(Unit) {
        viewModel.status.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    ServerPickerDropdown(
                        name = state.serverName,
                        servers = servers,
                        activeServerId = serverId,
                        onSelect = viewModel::switchServer,
                        appNavigator = appNavigator,
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        TooltipIconButton(
                            label = stringResource(CommonR.string.refresh_action),
                            icon = Icons.Filled.Refresh,
                            enabled = !state.dataLoading,
                            onClick = viewModel::refresh,
                        )
                    }
                },
            )
        },
        contentWindowInsets = NoWindowInsets,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                listOf(
                        CommonR.string.server_overview_tab,
                        CommonR.string.server_logs_tab,
                        CommonR.string.server_organization_tab,
                        CommonR.string.server_options_tab,
                    )
                    .forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            text = { Text(stringResource(label)) },
                        )
                    }
            }
            if (selectedTab == 3) {
                key(serverId) {
                    ServerOptionsContent(
                        appNavigator = appNavigator,
                        viewModel = hiltViewModel(key = "server-options-$serverId"),
                    )
                }
            } else if (selectedTab == 1) {
                key(serverId) { ServerLogsContent(serverId = serverId) }
            } else {
                val serverState = state.data?.serverState
                when {
                    state.hasError -> {
                        val resolve = rememberFriendlyMessageResolver()
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement =
                                androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Text(
                                state.error?.friendlyMessage(
                                    resolve,
                                    stringResource(CommonR.string.error),
                                ) ?: stringResource(CommonR.string.error),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = viewModel::refresh) {
                                Text(stringResource(CommonR.string.retry_action))
                            }
                        }
                    }
                    state.dataLoading || serverState == null ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    selectedTab == 2 ->
                        ServerOrganizationContent(
                            onCategories = { managementDialog = ServerDialog.ManageCategories },
                            onTags = { managementDialog = ServerDialog.ManageTags },
                        )
                    else -> ServerStatistics(serverState)
                }
            }
        }
    }
    ServerDialogHost(
        dialog = managementDialog,
        onDialogChange = { managementDialog = it },
        viewModel = viewModel,
    )
}

@Composable
private fun ServerOrganizationContent(onCategories: () -> Unit, onTags: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SegmentedListItem(
            onClick = onCategories,
            shapes = ListItemDefaults.segmentedShapes(0, 2),
            leadingContent = { Icon(Icons.Filled.Category, contentDescription = null) },
            supportingContent = {
                Text(stringResource(CommonR.string.organization_categories_summary))
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        ) {
            Text(stringResource(CommonR.string.categories_section_title))
        }
        SegmentedListItem(
            onClick = onTags,
            shapes = ListItemDefaults.segmentedShapes(1, 2),
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
            },
            supportingContent = { Text(stringResource(CommonR.string.organization_tags_summary)) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        ) {
            Text(stringResource(CommonR.string.tags_section_title))
        }
    }
}

@Composable
private fun ServerStatistics(state: ServerState) {
    val groups =
        listOf(
            Triple(
                CommonR.string.stat_header_user,
                Icons.Filled.SwapVert,
                listOf(
                    CommonR.string.stat_all_time_upload to state.allTimeUpload.toHumanReadable(),
                    CommonR.string.stat_all_time_download to
                        state.allTimeDownload.toHumanReadable(),
                    CommonR.string.stat_all_time_share_ratio to state.globalShareRatio,
                    CommonR.string.stat_session_waste to state.sessionWaste.toHumanReadable(),
                ),
            ),
            Triple(
                CommonR.string.stat_header_cache,
                Icons.Filled.Memory,
                listOf(
                    CommonR.string.stat_read_cache_hits to "${state.readCacheHits}%",
                    CommonR.string.stat_total_buffer_size to
                        state.totalBuffersSize.toLong().toHumanReadable(),
                ),
            ),
            Triple(
                CommonR.string.stat_header_performance,
                Icons.Filled.Speed,
                listOf(
                    CommonR.string.stat_write_cache_overload to "${state.writeCacheOverload}%",
                    CommonR.string.stat_read_cache_overload to "${state.readCacheOverload}%",
                    CommonR.string.stat_queued_io_jobs to state.queuedIoJobs.toString(),
                    CommonR.string.stat_average_time_in_queue to
                        stringResource(CommonR.string.value_ms, state.averageTimeInQueue),
                    CommonR.string.stat_total_queued_size to
                        state.totalQueuedSize.toLong().toHumanReadable(),
                ),
            ),
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            ServerTransferOverview(state)
            Spacer(Modifier.height(8.dp))
        }
        groups.forEach { (title, icon, rows) ->
            item(key = title) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                }
            }
            itemsIndexed(rows, key = { _, row -> row.first }) { index, (label, value) ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                    trailingContent = { Text(value, style = MaterialTheme.typography.titleSmall) },
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ServerTransferOverview(state: ServerState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(CommonR.string.live_transfers_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TransferMetric(
                        label = stringResource(CommonR.string.dl_speed),
                        value =
                            stringResource(
                                CommonR.string.transfer_rate,
                                state.dlInfoSpeed.toHumanReadable(),
                            ),
                        icon = Icons.Filled.ArrowDownward,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        valueStyle = MaterialTheme.typography.headlineSmall,
                    )
                    TransferMetric(
                        label = stringResource(CommonR.string.up_speed),
                        value =
                            stringResource(
                                CommonR.string.transfer_rate,
                                state.upInfoSpeed.toHumanReadable(),
                            ),
                        icon = Icons.Filled.ArrowUpward,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        valueStyle = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                TransferMetric(
                    label = stringResource(CommonR.string.free_space_label),
                    value = state.freeSpace.toHumanReadable(),
                    icon = Icons.Filled.Storage,
                    modifier = Modifier.padding(16.dp),
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }
            Card(modifier = Modifier.weight(1f)) {
                TransferMetric(
                    label = stringResource(CommonR.string.connected_peers),
                    value = state.totalPeerConnections.toString(),
                    icon = Icons.Filled.Groups,
                    modifier = Modifier.padding(16.dp),
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
