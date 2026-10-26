package dev.yashgarg.qbit.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

private val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

@Composable
fun BackupSettingsScreen(
    appNavigator: AppNavigator,
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val encryptBackupTitle = stringResource(CommonR.string.encrypt_backup_title)

    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            val pending = pendingExport
            pendingExport = null
            if (uri != null && pending != null) {
                backupViewModel.exportConfig(
                    uri,
                    pending.passphrase,
                    pending.serverIds,
                    pending.prefGroups,
                    pending.includeCategoryColors,
                )
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    BackupDialogs.askPassphraseThenImport(context, backupViewModel, uri)
                }
            }
        }

    LaunchedEffect(Unit) { BackupDialogs.offerImports(context, backupViewModel) }

    LaunchedEffect(Unit) {
        backupViewModel.backupEvents.collect { event ->
            when (event) {
                is BackupViewModel.BackupEvent.Exported ->
                    snackbarHostState.showSnackbar(event.message)
                is BackupViewModel.BackupEvent.Failed ->
                    snackbarHostState.showSnackbar(event.message)
                is BackupViewModel.BackupEvent.Imported ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startExport() {
        BackupDialogs.showExportSelectionDialog(context, backupViewModel.servers.value) {
            serverIds,
            prefGroups,
            includeColors ->
            BackupDialogs.showPassphraseDialog(
                context,
                title = encryptBackupTitle,
                confirm = true,
            ) { passphrase ->
                pendingExport = PendingExport(passphrase, serverIds, prefGroups, includeColors)
                exportLauncher.launch("captain-qbit-backup.cqb")
            }
        }
    }

    SettingsSubScreen(
        stringResource(CommonR.string.backup_section_title),
        appNavigator,
        snackbarHostState = snackbarHostState,
    ) {
        ClickableRow(
            title = stringResource(CommonR.string.export_configuration_label),
            onClick = { startExport() },
        )
        ClickableRow(
            title = stringResource(CommonR.string.import_configuration_label),
            onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
        )
    }
}

/** The selection + passphrase, held until the user picks an export destination. */
private data class PendingExport(
    val passphrase: String,
    val serverIds: Set<Int>,
    val prefGroups: Set<PrefGroup>,
    val includeCategoryColors: Boolean,
)
