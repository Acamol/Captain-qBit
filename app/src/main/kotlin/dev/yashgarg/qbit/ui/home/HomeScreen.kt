package dev.yashgarg.qbit.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand

/**
 * First-run / launch screen. Shows a spinner until the config status resolves, then the welcome
 * content when there's no server (MainActivity routes to the list when one exists). Also hosts the
 * restore-from-backup flow (SAF pick -> passphrase -> import), reusing the shared [BackupDialogs].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    appNavigator: AppNavigator,
    homeViewModel: HomeViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by homeViewModel.configStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val noServer = status == ConfigStatus.DOES_NOT_EXIST

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                BackupDialogs.showPassphraseDialog(
                    context,
                    title = "Backup passphrase",
                    confirm = false,
                ) { passphrase ->
                    backupViewModel.beginImport(uri, passphrase)
                }
            }
        }

    LaunchedEffect(Unit) {
        backupViewModel.backupEvents.collect { event ->
            when (event) {
                is BackupViewModel.BackupEvent.Failed ->
                    snackbarHostState.showSnackbar(event.message)
                is BackupViewModel.BackupEvent.Loaded ->
                    BackupDialogs.showImportSelectionDialog(
                        context,
                        event.backup,
                        event.duplicateServerIds,
                    ) { serverIds, prefGroups, includeColors, mode ->
                        backupViewModel.applyImport(serverIds, prefGroups, includeColors, mode)
                    }
                // Theme + leaving the empty state are handled by MainActivity's prefs/config-status
                // observers once a server exists; just acknowledge.
                is BackupViewModel.BackupEvent.Imported ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is BackupViewModel.BackupEvent.Exported -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (noServer) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(CommonR.string.add_server)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { appNavigator.navigate(NavCommand.OpenConfig(serverId = -1)) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (noServer) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(CommonR.string.welcome_text),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }) {
                        Text(stringResource(CommonR.string.restore_from_backup))
                    }
                }
            } else {
                // null (unresolved) or EXISTS (about to be routed to the list): keep spinning.
                LoadingIndicator()
            }
        }
    }
}

private val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")
