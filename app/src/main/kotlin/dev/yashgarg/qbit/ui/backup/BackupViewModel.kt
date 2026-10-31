package dev.yashgarg.qbit.ui.backup

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.backup.BackupManager
import dev.yashgarg.qbit.data.backup.ConfigBackup
import dev.yashgarg.qbit.data.backup.ImportMode
import dev.yashgarg.qbit.data.backup.ImportResult
import dev.yashgarg.qbit.data.backup.InvalidBackupException
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.data.backup.appLockWeakening
import dev.yashgarg.qbit.data.backup.availablePrefGroups
import dev.yashgarg.qbit.data.backup.hasCategoryColors
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.manager.LockWeakening
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.ui.common.StatusViewModel
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What to export: chosen in the export selection, then encrypted with a passphrase. */
data class ExportSelection(
    val serverIds: Set<Int>,
    val prefGroups: Set<PrefGroup>,
    val categoryColors: Boolean,
)

/**
 * What to import from a backup. While merging, servers that already exist would be skipped, so they
 * are left out of [serverIds]; replacing brings them back.
 */
data class ImportSelection(
    val mode: ImportMode,
    val serverIds: Set<Int>,
    val prefGroups: Set<PrefGroup>,
    val categoryColors: Boolean,
) {
    fun withMode(mode: ImportMode, duplicateServerIds: Set<Int>): ImportSelection =
        copy(
            mode = mode,
            serverIds =
                if (mode == ImportMode.MERGE) serverIds - duplicateServerIds
                else serverIds + duplicateServerIds,
        )
}

/** Why a passphrase was refused. */
enum class PassphraseError {
    EMPTY,
    MISMATCH,
    INCORRECT,
}

/** The backup dialog on screen, with what has been entered in it so far. */
sealed interface BackupDialog {
    /**
     * Asks for the passphrase of the backup at [uri], and keeps asking until it decrypts it.
     * [checking] is set while it tries.
     */
    data class ImportPassphrase(
        val uri: Uri,
        val passphrase: String = "",
        val error: PassphraseError? = null,
        val checking: Boolean = false,
    ) : BackupDialog

    /** Asks for a new passphrase, twice, to encrypt [selection] with. */
    data class ExportPassphrase(
        val selection: ExportSelection,
        val passphrase: String = "",
        val confirmation: String = "",
        val error: PassphraseError? = null,
    ) : BackupDialog

    data class ChooseExport(val servers: List<ServerConfig>, val selection: ExportSelection) :
        BackupDialog {
        /** A backup with no servers can't be imported. */
        val canExport: Boolean
            get() = selection.serverIds.isNotEmpty()
    }

    /**
     * The choice of what to import from a decrypted [backup]. Servers in [duplicateServerIds]
     * already exist.
     */
    data class ChooseImport(
        val backup: ConfigBackup,
        val duplicateServerIds: Set<Int>,
        val selection: ImportSelection,
    ) : BackupDialog {
        val canImport: Boolean
            get() = selection.serverIds.isNotEmpty()
    }

    /** Warns that importing [choice] would weaken the app lock. Cancelling returns to [choice]. */
    data class ConfirmLockWeakening(val choice: ChooseImport, val weakening: LockWeakening) :
        BackupDialog
}

/**
 * Drives encrypted backup export/import for both Settings and the first-run screen, and the dialogs
 * on the way, held here so that they outlast a rotation.
 *
 * Export: [startExport] offers a selection, then a passphrase, then [pickExportDestination] asks
 * the screen for a file to write to. Import: [askImportPassphrase] for a backup file, which
 * decrypts it and offers a selection of what to import.
 */
@HiltViewModel
class BackupViewModel
@Inject
constructor(
    private val backupManager: BackupManager,
    private val configDao: ConfigDao,
    private val prefsStore: DataStore<AppPreferences>,
    private val appLockManager: AppLockManager,
    application: Application,
) : StatusViewModel(application) {

    /** One-shot backup outcomes surfaced to the UI. */
    sealed interface BackupEvent {
        data class Exported(val message: String) : BackupEvent

        /**
         * Import succeeded. The restored theme/dynamic-colors are applied by observing the
         * persisted preferences (see MainActivity), not from this one-shot event, since importing
         * can navigate away and tear down the collector before it's delivered.
         */
        data class Imported(val message: String) : BackupEvent

        data class Failed(val message: String) : BackupEvent
    }

    // Kept until collected, so that an outcome reached while the app was in the background is
    // shown on its return.
    private val _backupEvents = Channel<BackupEvent>(Channel.BUFFERED)
    val backupEvents: Flow<BackupEvent> = _backupEvents.receiveAsFlow()

    private val _dialog = MutableStateFlow<BackupDialog?>(null)

    /** The backup dialog to show, if any. */
    val dialog: StateFlow<BackupDialog?> = _dialog.asStateFlow()

    private val _awaitingFile = MutableStateFlow(false)

    /**
     * Whether a backup file opened from another app is on its way to [dialog]: from when it arrives
     * until its dialog has come and gone. Not saved, so it is lost along with the dialog.
     */
    val awaitingFile: StateFlow<Boolean> = _awaitingFile.asStateFlow()

    /** A backup file was opened, and its dialog will follow once the app can show it. */
    fun awaitFile() {
        if (_awaitingFile.value) return
        _awaitingFile.value = true
        viewModelScope.launch {
            dialog.first { it != null }
            dialog.first { it == null }
            _awaitingFile.value = false
        }
    }

    /**
     * Whether the app is locked. The dialogs aren't shown meanwhile: they are windows of their own
     * and would show above the lock screen.
     */
    val locked: StateFlow<Boolean> = appLockManager.locked

    // The export waiting for a file to be written to, and whether the screen has asked for one.
    private data class PendingExport(
        val passphrase: String,
        val selection: ExportSelection,
        val destinationAsked: Boolean = false,
    )

    private val pendingExport = MutableStateFlow<PendingExport?>(null)

    /**
     * Set when the screen should ask where to write the export: answer with [exportTo], after
     * calling [exportDestinationAsked]. State rather than an event, so that a screen recreated
     * before it could ask still does.
     */
    val pickExportDestination: StateFlow<Boolean> =
        pendingExport
            .map { it != null && !it.destinationAsked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var decrypting: Job? = null

    /** Closes the dialog on screen, cancelling the export or import it belongs to. */
    fun dismissDialog() {
        val dialog = _dialog.value
        if (dialog is BackupDialog.ConfirmLockWeakening) {
            _dialog.value = dialog.choice
            return
        }
        decrypting?.cancel()
        _dialog.value = null
    }

    /** Offers a selection of what to export, everything to begin with. */
    fun startExport() {
        val servers = servers.value
        _dialog.value =
            BackupDialog.ChooseExport(
                servers,
                ExportSelection(
                    serverIds = servers.map { it.configId }.toSet(),
                    prefGroups = PrefGroup.entries.toSet(),
                    categoryColors = true,
                ),
            )
    }

    fun updateExportSelection(transform: (ExportSelection) -> ExportSelection) {
        _dialog.update {
            if (it is BackupDialog.ChooseExport) it.copy(selection = transform(it.selection))
            else it
        }
    }

    fun confirmExportSelection() {
        val dialog = _dialog.value as? BackupDialog.ChooseExport ?: return
        if (!dialog.canExport) return
        _dialog.value = BackupDialog.ExportPassphrase(dialog.selection)
    }

    fun onPassphraseChange(passphrase: String) {
        _dialog.update {
            when (it) {
                is BackupDialog.ImportPassphrase ->
                    if (it.checking) it else it.copy(passphrase = passphrase, error = null)
                is BackupDialog.ExportPassphrase -> it.copy(passphrase = passphrase, error = null)
                else -> it
            }
        }
    }

    fun onConfirmationChange(confirmation: String) {
        _dialog.update {
            if (it is BackupDialog.ExportPassphrase) {
                it.copy(confirmation = confirmation, error = null)
            } else {
                it
            }
        }
    }

    /**
     * Checks the passphrase entered. It must be non-empty: it's the only key protecting the
     * exported credentials, so an empty one would leave the backup effectively unencrypted.
     */
    fun submitPassphrase() {
        when (val dialog = _dialog.value) {
            is BackupDialog.ExportPassphrase ->
                when {
                    dialog.passphrase.isEmpty() ->
                        _dialog.value = dialog.copy(error = PassphraseError.EMPTY)
                    dialog.passphrase != dialog.confirmation ->
                        _dialog.value = dialog.copy(error = PassphraseError.MISMATCH)
                    else -> {
                        _dialog.value = null
                        pendingExport.value = PendingExport(dialog.passphrase, dialog.selection)
                    }
                }
            is BackupDialog.ImportPassphrase ->
                when {
                    dialog.checking -> Unit
                    dialog.passphrase.isEmpty() ->
                        _dialog.value = dialog.copy(error = PassphraseError.EMPTY)
                    else -> decrypt(dialog)
                }
            else -> Unit
        }
    }

    /** The screen is asking where to write the export. */
    fun exportDestinationAsked() {
        pendingExport.update { it?.copy(destinationAsked = true) }
    }

    /** Writes the pending export to [uri], or drops it if no file was chosen. */
    fun exportTo(uri: Uri?) {
        val pending = pendingExport.value ?: return
        pendingExport.value = null
        if (uri == null) return
        // Not cancelled by leaving the screen: that would leave a partly written file.
        viewModelScope.launch(NonCancellable) {
            val event =
                try {
                    val prefs = prefsStore.data.first()
                    val selection = pending.selection
                    val servers =
                        configDao.getConfigs().first().filter {
                            it.configId in selection.serverIds
                        }
                    val preferences = if (selection.prefGroups.isNotEmpty()) prefs else null
                    val categoryColors =
                        if (selection.categoryColors) prefs.categoryColors else null
                    backupManager.export(
                        uri,
                        pending.passphrase,
                        servers,
                        preferences,
                        selection.prefGroups,
                        categoryColors,
                    )
                    BackupEvent.Exported(getString(CommonR.string.status_config_exported))
                } catch (e: Exception) {
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_export_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        ),
                    )
                }
            _backupEvents.trySend(event)
        }
    }

    /** Asks for the passphrase of the backup at [uri], to import it. */
    fun askImportPassphrase(uri: Uri) {
        decrypting?.cancel()
        _dialog.value = BackupDialog.ImportPassphrase(uri)
    }

    private fun decrypt(dialog: BackupDialog.ImportPassphrase) {
        val checking = dialog.copy(checking = true)
        _dialog.value = checking
        decrypting = viewModelScope.launch {
            val failure =
                try {
                    val backup = backupManager.readBackup(dialog.uri, dialog.passphrase)
                    val existingKeys = backupManager.currentServerKeys()
                    val duplicateIds =
                        backup.servers
                            .filter { backupManager.identityKey(it) in existingKeys }
                            .map { it.configId }
                            .toSet()
                    _dialog.value =
                        BackupDialog.ChooseImport(
                            backup,
                            duplicateIds,
                            ImportSelection(
                                mode = ImportMode.MERGE,
                                serverIds =
                                    backup.servers.map { it.configId }.toSet() - duplicateIds,
                                prefGroups = backup.availablePrefGroups(),
                                categoryColors = backup.hasCategoryColors(),
                            ),
                        )
                    null
                } catch (e: AEADBadTagException) {
                    // Left open to try again.
                    _dialog.compareAndSet(
                        checking,
                        checking.copy(checking = false, error = PassphraseError.INCORRECT),
                    )
                    null
                } catch (e: InvalidBackupException) {
                    BackupEvent.Failed(e.message ?: getString(CommonR.string.unknown_error))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_import_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        ),
                    )
                }
            if (failure != null) {
                _dialog.compareAndSet(checking, null)
                _backupEvents.trySend(failure)
            }
        }
    }

    fun updateImportSelection(transform: (ImportSelection) -> ImportSelection) {
        _dialog.update {
            if (it is BackupDialog.ChooseImport) it.copy(selection = transform(it.selection))
            else it
        }
    }

    /**
     * Imports the selection, warning first if it would weaken the app lock: a backup file mustn't
     * be able to do that unasked.
     */
    fun confirmImportSelection() {
        val choice = _dialog.value as? BackupDialog.ChooseImport ?: return
        if (!choice.canImport) return
        val weakening =
            choice.backup.appLockWeakening(choice.selection.prefGroups, appLockManager.settings)
        if (weakening != null) {
            _dialog.value = BackupDialog.ConfirmLockWeakening(choice, weakening)
        } else {
            applyImport(choice)
        }
    }

    /**
     * Goes ahead with an import that weakens the app lock, once the user has authenticated with the
     * prompt titled [promptTitle]. Cancelling the prompt returns to the selection.
     */
    fun confirmLockWeakening(promptTitle: String) {
        val dialog = _dialog.value as? BackupDialog.ConfirmLockWeakening ?: return
        val choice = dialog.choice
        _dialog.value = choice
        appLockManager.authenticate(
            promptTitle,
            onSuccess = { if (_dialog.value == choice) applyImport(choice) },
        )
    }

    private fun applyImport(choice: BackupDialog.ChooseImport) {
        _dialog.value = null
        val selection = choice.selection
        // Not cancelled by leaving the screen: replacing removes the servers before adding the
        // backup's, so stopping part-way could leave none.
        viewModelScope.launch(NonCancellable) {
            val event =
                try {
                    val selected =
                        choice.backup.servers.filter { it.configId in selection.serverIds }
                    val result =
                        backupManager.applyImport(
                            choice.backup,
                            selected,
                            selection.prefGroups,
                            selection.categoryColors,
                            selection.mode,
                        )
                    BackupEvent.Imported(summarize(result))
                } catch (e: Exception) {
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_import_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        ),
                    )
                }
            _backupEvents.trySend(event)
        }
    }

    private fun summarize(result: ImportResult): String {
        fun servers(n: Int) = getQuantityString(CommonR.plurals.server_count, n, n)
        if (result.replaced) {
            return getString(CommonR.string.status_replaced_with_servers, servers(result.imported))
        }
        return when {
            result.imported == 0 && result.skipped > 0 ->
                getString(CommonR.string.status_no_new_servers, servers(result.skipped))
            result.skipped > 0 ->
                getString(
                    CommonR.string.status_imported_servers_with_skipped,
                    servers(result.imported),
                    result.skipped,
                )
            else -> getString(CommonR.string.status_imported_servers, servers(result.imported))
        }
    }
}
