package dev.yashgarg.qbit.ui.search.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.ConfirmDialog
import dev.yashgarg.qbit.ui.compose.TextInputDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
import qbittorrent.models.SearchPlugin

private sealed interface PluginDialog {
    data object Install : PluginDialog

    data class ConfirmInstall(val url: String) : PluginDialog

    data class ConfirmUninstall(val plugin: SearchPlugin) : PluginDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPluginsScreen(
    appNavigator: AppNavigator,
    viewModel: SearchPluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<PluginDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(CommonR.string.search_plugins_action)) },
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
                        label = stringResource(CommonR.string.search_plugins_update_all_action),
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.updateAll() },
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = stringResource(CommonR.string.search_plugin_install_action),
                        icon = Icons.Filled.Add,
                        onClick = { dialog = PluginDialog.Install },
                        position = TooltipAnchorPosition.Below,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading ->
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                state.error != null -> CenteredMessage(state.error!!, isError = true)
                state.plugins.isEmpty() ->
                    CenteredMessage(stringResource(CommonR.string.search_plugins_empty))
                else ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.plugins, key = { it.name }) { plugin ->
                            PluginRow(
                                plugin = plugin,
                                onToggle = { viewModel.setEnabled(plugin.name, it) },
                                onUninstall = { dialog = PluginDialog.ConfirmUninstall(plugin) },
                            )
                        }
                    }
            }
        }
    }

    when (val current = dialog) {
        PluginDialog.Install -> {
            val invalid = stringResource(CommonR.string.search_plugin_url_invalid)
            TextInputDialog(
                title = stringResource(CommonR.string.search_plugin_install_action),
                confirmLabel = stringResource(CommonR.string.search_plugin_install_next),
                label = stringResource(CommonR.string.search_plugin_url_label),
                supportingText = stringResource(CommonR.string.search_plugin_install_warning),
                // Plugins are Python the server will execute, so only fetch them over TLS.
                validate = { if (it.trim().startsWith("https://")) null else invalid },
                onConfirm = { dialog = PluginDialog.ConfirmInstall(it.trim()) },
                onDismiss = { dialog = null },
            )
        }
        is PluginDialog.ConfirmInstall ->
            ConfirmDialog(
                title = stringResource(CommonR.string.search_plugin_install_confirm, current.url),
                confirmLabel = stringResource(CommonR.string.search_plugin_install_confirm_action),
                onConfirm = {
                    viewModel.install(current.url)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is PluginDialog.ConfirmUninstall ->
            ConfirmDialog(
                title =
                    stringResource(
                        CommonR.string.search_plugin_uninstall_confirm,
                        current.plugin.fullName,
                    ),
                confirmLabel = stringResource(CommonR.string.search_plugin_uninstall_action),
                onConfirm = {
                    viewModel.uninstall(current.plugin.name)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

@Composable
private fun PluginRow(plugin: SearchPlugin, onToggle: (Boolean) -> Unit, onUninstall: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (plugin.enabled) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f)
                    .toggleable(
                        value = plugin.enabled,
                        role = Role.Switch,
                        onValueChange = onToggle,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plugin.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(plugin.version, plugin.url)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = plugin.enabled, onCheckedChange = null)
            }
            IconButton(onClick = onUninstall) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription =
                        stringResource(
                            CommonR.string.search_plugin_uninstall_named_action,
                            plugin.fullName,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
