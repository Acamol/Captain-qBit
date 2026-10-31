package dev.yashgarg.qbit.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.backup.BackupDialogHost
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator

private val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

@Composable
fun BackupSettingsScreen(
    appNavigator: AppNavigator,
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
            backupViewModel::exportTo,
        )
    val pickExportDestination by backupViewModel.pickExportDestination.collectAsStateWithLifecycle()
    LaunchedEffect(pickExportDestination) {
        if (pickExportDestination) {
            backupViewModel.exportDestinationAsked()
            exportLauncher.launch("captain-qbit-backup.cqb")
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) backupViewModel.askImportPassphrase(uri)
        }

    BackupDialogHost(backupViewModel)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    SettingsSubScreen(
        stringResource(CommonR.string.backup_section_title),
        appNavigator,
        snackbarHostState = snackbarHostState,
    ) {
        ClickableRow(
            title = stringResource(CommonR.string.export_configuration_label),
            onClick = backupViewModel::startExport,
        )
        ClickableRow(
            title = stringResource(CommonR.string.import_configuration_label),
            onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
        )
    }
}
