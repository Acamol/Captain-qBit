package dev.yashgarg.qbit.ui.settings

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.backup.BackupManager
import dev.yashgarg.qbit.data.backup.InvalidBackupException
import dev.yashgarg.qbit.data.models.ServerPreferences
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val prefsStore: DataStore<ServerPreferences>,
    private val backupManager: BackupManager,
) : ViewModel() {

    /** One-shot results of a backup export/import, surfaced to the UI as a message. */
    sealed interface BackupEvent {
        data class Exported(val message: String) : BackupEvent

        /** Import succeeded; the UI should recreate itself so a restored theme takes effect. */
        data class Imported(val message: String) : BackupEvent

        data class Failed(val message: String) : BackupEvent
    }

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents = _backupEvents.asSharedFlow()

    val dynamicColors: StateFlow<Boolean> =
        prefsStore.data
            .map { it.dynamicColors }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val statusNotification: StateFlow<Boolean> =
        prefsStore.data
            .map { it.statusNotification }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notifyOnComplete: StateFlow<Boolean> =
        prefsStore.data
            .map { it.notifyOnComplete }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notifyOnChecked: StateFlow<Boolean> =
        prefsStore.data
            .map { it.notifyOnChecked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(dynamicColors = enabled) } }
    }

    fun setStatusNotification(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(statusNotification = enabled) } }
    }

    fun setNotifyOnComplete(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(notifyOnComplete = enabled) } }
    }

    fun setNotifyOnChecked(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(notifyOnChecked = enabled) } }
    }

    fun exportConfig(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val event =
                try {
                    backupManager.export(uri, passphrase)
                    BackupEvent.Exported("Configuration exported")
                } catch (e: Exception) {
                    BackupEvent.Failed("Export failed: ${e.message ?: "unknown error"}")
                }
            _backupEvents.emit(event)
        }
    }

    fun importConfig(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val event =
                try {
                    backupManager.import(uri, passphrase)
                    BackupEvent.Imported("Configuration imported")
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
}
