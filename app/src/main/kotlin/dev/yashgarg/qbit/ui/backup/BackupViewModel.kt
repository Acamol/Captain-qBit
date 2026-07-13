package dev.yashgarg.qbit.ui.backup

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.backup.BackupManager
import dev.yashgarg.qbit.data.backup.ConfigBackup
import dev.yashgarg.qbit.data.backup.ImportMode
import dev.yashgarg.qbit.data.backup.ImportResult
import dev.yashgarg.qbit.data.backup.InvalidBackupException
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.data.backup.extractGroups
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives encrypted backup export/import for both Settings and the first-run screen. Import is a
 * two-step flow: [beginImport] decrypts and emits [BackupEvent.Loaded] so the UI can offer a
 * selection, then [applyImport] commits the user's choices.
 */
@HiltViewModel
class BackupViewModel
@Inject
constructor(
    private val backupManager: BackupManager,
    private val configDao: ConfigDao,
    private val prefsStore: DataStore<ServerPreferences>,
) : ViewModel() {

    /** One-shot backup outcomes surfaced to the UI. */
    sealed interface BackupEvent {
        data class Exported(val message: String) : BackupEvent

        /**
         * A backup was decrypted and is ready to import. [duplicateServerIds] are the ids (within
         * [backup]) of servers that already exist and would be skipped on a merge.
         */
        data class Loaded(val backup: ConfigBackup, val duplicateServerIds: Set<Int>) : BackupEvent

        /**
         * Import succeeded. The restored theme/dynamic-colors are applied by observing the
         * persisted preferences (see MainActivity), not from this one-shot event, since importing
         * can navigate away and tear down the collector before it's delivered.
         */
        data class Imported(val message: String) : BackupEvent

        data class Failed(val message: String) : BackupEvent
    }

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents = _backupEvents.asSharedFlow()

    /** Current servers, for building the export selection dialog. */
    val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Holds the decrypted backup between beginImport() and applyImport().
    private var pendingImport: ConfigBackup? = null

    fun exportConfig(
        uri: Uri,
        passphrase: String,
        selectedServerIds: Set<Int>,
        prefGroups: Set<PrefGroup>,
        includeCategoryColors: Boolean,
    ) {
        viewModelScope.launch {
            val event =
                try {
                    val prefs = prefsStore.data.first()
                    val servers =
                        configDao.getConfigs().first().filter { it.configId in selectedServerIds }
                    val preferences =
                        if (prefGroups.isNotEmpty()) prefs.extractGroups(prefGroups) else null
                    val categoryColors = if (includeCategoryColors) prefs.categoryColors else null
                    backupManager.export(
                        uri,
                        passphrase,
                        servers,
                        preferences,
                        prefGroups,
                        categoryColors,
                    )
                    BackupEvent.Exported("Configuration exported")
                } catch (e: Exception) {
                    BackupEvent.Failed("Export failed: ${e.message ?: "unknown error"}")
                }
            _backupEvents.emit(event)
        }
    }

    fun beginImport(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val event =
                try {
                    val backup = backupManager.readBackup(uri, passphrase)
                    pendingImport = backup
                    val existingKeys = backupManager.currentServerKeys()
                    val duplicateIds =
                        backup.servers
                            .filter { backupManager.identityKey(it) in existingKeys }
                            .map { it.configId }
                            .toSet()
                    BackupEvent.Loaded(backup, duplicateIds)
                } catch (e: AEADBadTagException) {
                    BackupEvent.Failed("Incorrect passphrase, or the file is corrupted")
                } catch (e: InvalidBackupException) {
                    BackupEvent.Failed(e.message ?: "Invalid backup file")
                } catch (e: Exception) {
                    BackupEvent.Failed("Import failed: ${e.message ?: "unknown error"}")
                }
            _backupEvents.emit(event)
        }
    }

    fun applyImport(
        selectedServerIds: Set<Int>,
        prefGroups: Set<PrefGroup>,
        includeCategoryColors: Boolean,
        mode: ImportMode,
    ) {
        val backup = pendingImport ?: return
        viewModelScope.launch {
            val event =
                try {
                    val selected = backup.servers.filter { it.configId in selectedServerIds }
                    val result =
                        backupManager.applyImport(
                            backup,
                            selected,
                            prefGroups,
                            includeCategoryColors,
                            mode,
                        )
                    pendingImport = null
                    BackupEvent.Imported(summarize(result))
                } catch (e: Exception) {
                    BackupEvent.Failed("Import failed: ${e.message ?: "unknown error"}")
                }
            _backupEvents.emit(event)
        }
    }

    private fun summarize(result: ImportResult): String {
        fun servers(n: Int) = if (n == 1) "1 server" else "$n servers"
        if (result.replaced) return "Replaced with ${servers(result.imported)}"
        return when {
            result.imported == 0 && result.skipped > 0 ->
                "No new servers — ${servers(result.skipped)} already added"
            result.skipped > 0 ->
                "Imported ${servers(result.imported)} (${result.skipped} skipped, already added)"
            else -> "Imported ${servers(result.imported)}"
        }
    }
}
