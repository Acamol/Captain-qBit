package dev.yashgarg.qbit.ui.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
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
import dev.yashgarg.qbit.ui.common.StatusViewModel
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
    @ApplicationContext context: Context,
) : StatusViewModel(context) {

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
                    BackupEvent.Exported(getString(CommonR.string.status_config_exported))
                } catch (e: Exception) {
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_export_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        )
                    )
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
                    BackupEvent.Failed(getString(CommonR.string.status_incorrect_passphrase))
                } catch (e: InvalidBackupException) {
                    BackupEvent.Failed(e.message ?: getString(CommonR.string.unknown_error))
                } catch (e: Exception) {
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_import_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        )
                    )
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
                    BackupEvent.Failed(
                        getString(
                            CommonR.string.status_import_failed,
                            e.message ?: getString(CommonR.string.unknown_error),
                        )
                    )
                }
            _backupEvents.emit(event)
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
