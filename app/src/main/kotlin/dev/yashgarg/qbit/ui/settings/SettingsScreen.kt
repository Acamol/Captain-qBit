package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.worker.StatusWorker

private val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

private val THEME_OPTIONS =
    listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to "System default",
        AppCompatDelegate.MODE_NIGHT_NO to "Light",
        AppCompatDelegate.MODE_NIGHT_YES to "Dark",
    )

private fun themeLabel(mode: Int): String =
    THEME_OPTIONS.firstOrNull { it.first == mode }?.second ?: "System default"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColors by viewModel.dynamicColors.collectAsStateWithLifecycle()
    val statusNotif by viewModel.statusNotification.collectAsStateWithLifecycle()
    val notifyComplete by viewModel.notifyOnComplete.collectAsStateWithLifecycle()
    val notifyChecked by viewModel.notifyOnChecked.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }

    val notifPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) StatusWorker.enqueue(context)
        }

    fun applyNotificationPrefs(status: Boolean, complete: Boolean, checked: Boolean) {
        if (!(status || complete || checked)) {
            StatusWorker.cancel(context)
            return
        }
        if (AppNotificationManager.checkPermission(context)) {
            StatusWorker.enqueue(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
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
                is BackupViewModel.BackupEvent.Exported ->
                    snackbarHostState.showSnackbar(event.message)
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
            BackupDialogs.showPassphraseDialog(context, title = "Encrypt backup", confirm = true) {
                passphrase ->
                pendingExport = PendingExport(passphrase, serverIds, prefGroups, includeColors)
                exportLauncher.launch("captain-qbit-backup.cqb")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SectionHeader("General")
            ClickableRow(
                title = "Servers",
                onClick = { appNavigator.navigate(NavCommand.OpenServerList) },
            )

            HorizontalDivider()
            SectionHeader("Appearance")
            ClickableRow(
                title = "Theme",
                subtitle = themeLabel(themeMode),
                onClick = { showThemeDialog = true },
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                SwitchRow(
                    "Dynamic colors",
                    dynamicColors,
                    subtitle = "Use the wallpaper-based Material You palette",
                ) {
                    viewModel.setDynamicColors(it)
                }
            }

            HorizontalDivider()
            SectionHeader("Notifications")
            SwitchRow(
                "Status notification",
                statusNotif,
                subtitle = "Ongoing notification showing current transfer speeds",
            ) {
                viewModel.setStatusNotification(it)
                applyNotificationPrefs(it, notifyComplete, notifyChecked)
            }
            SwitchRow(
                "Notify on complete",
                notifyComplete,
                subtitle = "Alert when a torrent finishes downloading",
            ) {
                viewModel.setNotifyOnComplete(it)
                applyNotificationPrefs(statusNotif, it, notifyChecked)
            }
            SwitchRow(
                "Notify on checked",
                notifyChecked,
                subtitle = "Alert when a torrent finishes rechecking",
            ) {
                viewModel.setNotifyOnChecked(it)
                applyNotificationPrefs(statusNotif, notifyComplete, it)
            }

            HorizontalDivider()
            SectionHeader("Backup")
            ClickableRow(title = "Export configuration", onClick = { startExport() })
            ClickableRow(
                title = "Import configuration",
                onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
            )

            HorizontalDivider()
            ClickableRow(
                title = "About",
                onClick = { appNavigator.navigate(NavCommand.OpenVersion) },
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    THEME_OPTIONS.forEach { (mode, label) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** The selection + passphrase, held until the user picks an export destination. */
private data class PendingExport(
    val passphrase: String,
    val serverIds: Set<Int>,
    val prefGroups: Set<dev.yashgarg.qbit.data.backup.PrefGroup>,
    val includeCategoryColors: Boolean,
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClickableRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
