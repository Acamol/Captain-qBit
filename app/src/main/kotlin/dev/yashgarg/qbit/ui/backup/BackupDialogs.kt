package dev.yashgarg.qbit.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.backup.ImportMode
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.data.backup.availablePrefGroups
import dev.yashgarg.qbit.data.backup.hasCategoryColors
import dev.yashgarg.qbit.data.manager.LockWeakening
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.CheckboxRow
import dev.yashgarg.qbit.ui.compose.SelectableRow

/**
 * Shows the backup dialog [viewModel] has open, shared by Settings, the first-run screen and a
 * backup file opened from another app. Hidden while the app is locked, and shown again on unlock.
 * Dialogs inherit the activity's FLAG_SECURE, so passphrases and server details stay out of
 * screenshots whenever the app does.
 */
@Composable
fun BackupDialogHost(viewModel: BackupViewModel) {
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    if (locked) return
    when (val d = dialog) {
        null -> Unit
        is BackupDialog.ImportPassphrase -> ImportPassphraseDialog(d, viewModel)
        is BackupDialog.ExportPassphrase -> ExportPassphraseDialog(d, viewModel)
        is BackupDialog.ChooseExport -> ExportSelectionDialog(d, viewModel)
        is BackupDialog.ChooseImport -> ImportSelectionDialog(d, viewModel)
        is BackupDialog.ConfirmLockWeakening -> LockWeakeningDialog(d.weakening, viewModel)
    }
}

@Composable
private fun ImportPassphraseDialog(
    dialog: BackupDialog.ImportPassphrase,
    viewModel: BackupViewModel,
) {
    PassphraseDialog(
        title = stringResource(CommonR.string.backup_passphrase_title),
        passphrase = dialog.passphrase,
        error = dialog.error,
        checking = dialog.checking,
        viewModel = viewModel,
    )
}

@Composable
private fun ExportPassphraseDialog(
    dialog: BackupDialog.ExportPassphrase,
    viewModel: BackupViewModel,
) {
    PassphraseDialog(
        title = stringResource(CommonR.string.encrypt_backup_title),
        passphrase = dialog.passphrase,
        error = dialog.error,
        checking = false,
        viewModel = viewModel,
        confirmation = dialog.confirmation,
    )
}

/**
 * A passphrase field, and a second one that must match when [confirmation] is given. While
 * [checking] the fields are read-only rather than disabled, so they keep the keyboard's focus for a
 * retry.
 */
@Composable
private fun PassphraseDialog(
    title: String,
    passphrase: String,
    error: PassphraseError?,
    checking: Boolean,
    viewModel: BackupViewModel,
    confirmation: String? = null,
) {
    val errorText =
        when (error) {
            null -> null
            PassphraseError.EMPTY -> stringResource(CommonR.string.enter_a_passphrase_error)
            PassphraseError.MISMATCH -> stringResource(CommonR.string.passphrases_dont_match_error)
            PassphraseError.INCORRECT -> stringResource(CommonR.string.status_incorrect_passphrase)
        }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(title) },
        text = {
            Column {
                PassphraseField(
                    value = passphrase,
                    onChange = viewModel::onPassphraseChange,
                    label = stringResource(CommonR.string.passphrase_label),
                    // The mismatch is reported on the confirmation instead.
                    error = errorText.takeIf { error != PassphraseError.MISMATCH },
                    readOnly = checking,
                    last = confirmation == null,
                    onDone = viewModel::submitPassphrase,
                )
                if (confirmation != null) {
                    PassphraseField(
                        value = confirmation,
                        onChange = viewModel::onConfirmationChange,
                        label = stringResource(CommonR.string.confirm_passphrase_label),
                        error = errorText.takeIf { error == PassphraseError.MISMATCH },
                        readOnly = checking,
                        last = true,
                        onDone = viewModel::submitPassphrase,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submitPassphrase, enabled = !checking) {
                Text(stringResource(CommonR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) {
                Text(stringResource(CommonR.string.cancel))
            }
        },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    readOnly: Boolean,
    last: Boolean,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        readOnly = readOnly,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Which servers, app settings and category colors to export. The Export button stays disabled until
 * at least one server is chosen.
 */
@Composable
private fun ExportSelectionDialog(dialog: BackupDialog.ChooseExport, viewModel: BackupViewModel) {
    val selection = dialog.selection
    SelectionDialog(
        title = stringResource(CommonR.string.export_configuration_label),
        confirmLabel = stringResource(CommonR.string.export_action),
        confirmEnabled = dialog.canExport,
        onConfirm = viewModel::confirmExportSelection,
        onDismiss = viewModel::dismissDialog,
    ) {
        SectionHeader(stringResource(CommonR.string.app_settings_section_header), first = true)
        PrefGroup.entries.forEach { group ->
            CheckboxRow(prefGroupLabel(group), group in selection.prefGroups) { checked ->
                viewModel.updateExportSelection {
                    it.copy(prefGroups = it.prefGroups.toggled(group, checked))
                }
            }
        }
        CheckboxRow(
            stringResource(CommonR.string.category_colors_label),
            selection.categoryColors,
        ) { checked ->
            viewModel.updateExportSelection { it.copy(categoryColors = checked) }
        }
        SectionHeader(stringResource(CommonR.string.servers_title))
        dialog.servers.forEach { server ->
            CheckboxRow(server.serverName, server.configId in selection.serverIds) { checked ->
                viewModel.updateExportSelection {
                    it.copy(serverIds = it.serverIds.toggled(server.configId, checked))
                }
            }
        }
    }
}

/**
 * The merge/replace mode, and which servers, app settings and category colors to import. Servers
 * that already exist are marked "(already added)" and can't be chosen while merging, which would
 * skip them. The Import button stays disabled until at least one server is chosen.
 */
@Composable
private fun ImportSelectionDialog(dialog: BackupDialog.ChooseImport, viewModel: BackupViewModel) {
    val selection = dialog.selection
    val merging = selection.mode == ImportMode.MERGE
    val availableGroups = dialog.backup.availablePrefGroups()
    val hasCategoryColors = dialog.backup.hasCategoryColors()
    SelectionDialog(
        title = stringResource(CommonR.string.import_configuration_label),
        confirmLabel = stringResource(CommonR.string.import_action),
        confirmEnabled = dialog.canImport,
        onConfirm = viewModel::confirmImportSelection,
        onDismiss = viewModel::dismissDialog,
    ) {
        SectionHeader(stringResource(CommonR.string.import_mode_section_header), first = true)
        listOf(
                ImportMode.MERGE to CommonR.string.merge_with_current_label,
                ImportMode.REPLACE to CommonR.string.replace_everything_label,
            )
            .forEach { (mode, label) ->
                SelectableRow(stringResource(label), selected = selection.mode == mode) {
                    viewModel.updateImportSelection {
                        it.withMode(mode, dialog.duplicateServerIds)
                    }
                }
            }
        if (availableGroups.isNotEmpty() || hasCategoryColors) {
            SectionHeader(stringResource(CommonR.string.app_settings_section_header))
        }
        PrefGroup.entries
            .filter { it in availableGroups }
            .forEach { group ->
                CheckboxRow(prefGroupLabel(group), group in selection.prefGroups) { checked ->
                    viewModel.updateImportSelection {
                        it.copy(prefGroups = it.prefGroups.toggled(group, checked))
                    }
                }
            }
        if (hasCategoryColors) {
            CheckboxRow(
                stringResource(CommonR.string.category_colors_label),
                selection.categoryColors,
            ) { checked ->
                viewModel.updateImportSelection { it.copy(categoryColors = checked) }
            }
        }
        SectionHeader(stringResource(CommonR.string.servers_title))
        dialog.backup.servers.forEach { server ->
            val duplicate = server.configId in dialog.duplicateServerIds
            CheckboxRow(
                if (duplicate) {
                    stringResource(CommonR.string.backup_server_already_added, server.serverName)
                } else {
                    server.serverName
                },
                checked = server.configId in selection.serverIds,
                enabled = !(duplicate && merging),
            ) { checked ->
                viewModel.updateImportSelection {
                    it.copy(serverIds = it.serverIds.toggled(server.configId, checked))
                }
            }
        }
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** Warns that the import weakens the app lock. Continuing asks the user to authenticate. */
@Composable
private fun LockWeakeningDialog(weakening: LockWeakening, viewModel: BackupViewModel) {
    val (title, message) =
        when (weakening) {
            LockWeakening.TURNED_OFF ->
                CommonR.string.import_turns_off_lock_title to
                    CommonR.string.import_turns_off_lock_message
            LockWeakening.LOOSENED ->
                CommonR.string.import_loosens_lock_title to
                    CommonR.string.import_loosens_lock_message
        }
    val promptTitle = stringResource(CommonR.string.app_lock_confirm_title)
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmLockWeakening(promptTitle) }) {
                Text(stringResource(CommonR.string.import_turns_off_lock_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) {
                Text(stringResource(CommonR.string.cancel))
            }
        },
    )
}

/** A label separating groups of options in a selection dialog. */
@Composable
private fun SectionHeader(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 16.dp, bottom = 4.dp),
    )
}

/** User-facing label for a preference group checkbox. */
@Composable
private fun prefGroupLabel(group: PrefGroup): String =
    stringResource(
        when (group) {
            PrefGroup.APPEARANCE -> CommonR.string.appearance_section_title
            PrefGroup.NOTIFICATIONS -> CommonR.string.notifications_section_header
            PrefGroup.FILTERS -> CommonR.string.filters_and_sorting_label
            PrefGroup.PRIVACY -> CommonR.string.privacy_section_title
        },
    )

private fun <T> Set<T>.toggled(item: T, present: Boolean): Set<T> =
    if (present) this + item else this - item
