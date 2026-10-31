package dev.yashgarg.qbit.ui.backup

import android.app.Application
import android.content.res.Resources
import android.net.Uri
import androidx.datastore.core.DataStore
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.data.backup.BackupManager
import dev.yashgarg.qbit.data.backup.ConfigBackup
import dev.yashgarg.qbit.data.backup.ImportMode
import dev.yashgarg.qbit.data.backup.ImportResult
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.manager.AppLockSettings
import dev.yashgarg.qbit.data.manager.LockWeakening
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun server(id: Int) =
        ServerConfig(
            configId = id,
            serverName = "Server $id",
            baseUrl = "host$id",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTP,
        )

    private val uri = mock<Uri>()

    // Server 1 is already on this device.
    private val backup = ConfigBackup(servers = listOf(server(1), server(2)))

    private val backupManager: BackupManager = mock {
        onBlocking { readBackup(any(), eq("right")) } doReturn backup
        onBlocking { readBackup(any(), eq("wrong")) } doAnswer { throw AEADBadTagException() }
        onBlocking { currentServerKeys() } doReturn setOf("host1")
        on { identityKey(any()) } doAnswer { (it.arguments[0] as ServerConfig).baseUrl }
        onBlocking { applyImport(any(), any(), any(), any(), any()) } doReturn
            ImportResult(imported = 1, skipped = 0, replaced = false)
    }

    private val configDao: ConfigDao = mock {
        on { getConfigs() } doReturn MutableStateFlow(listOf(server(1), server(3)))
    }

    private val appLockManager: AppLockManager = mock {
        on { locked } doReturn MutableStateFlow(false)
        on { settings } doReturn
            AppLockSettings(enabled = true, timeoutMs = 0L, lockOnScreenOff = true)
    }

    // Status messages aren't under test: any lookup gives an empty string.
    private val resources: Resources = mock {
        on { getQuantityString(any(), any(), anyVararg()) } doReturn ""
    }
    private val application: Application = mock {
        on { getString(any(), anyVararg()) } doReturn ""
        on { resources } doReturn resources
    }

    private fun viewModel() =
        BackupViewModel(
            backupManager,
            configDao,
            mock<DataStore<AppPreferences>>(),
            appLockManager,
            application,
        )

    private fun BackupViewModel.enterImportPassphrase(passphrase: String) {
        askImportPassphrase(uri)
        onPassphraseChange(passphrase)
        submitPassphrase()
    }

    @Test
    fun `an opened backup file is awaited until its dialog has come and gone`() = runTest {
        val viewModel = viewModel()

        viewModel.awaitFile()
        assertTrue(viewModel.awaitingFile.value)

        viewModel.askImportPassphrase(uri)
        assertTrue(viewModel.awaitingFile.value)

        viewModel.dismissDialog()
        assertFalse(viewModel.awaitingFile.value)
    }

    @Test
    fun `a wrong passphrase keeps the dialog open to try again`() = runTest {
        val viewModel = viewModel()

        viewModel.enterImportPassphrase("wrong")

        val dialog = viewModel.dialog.value as BackupDialog.ImportPassphrase
        assertEquals(PassphraseError.INCORRECT, dialog.error)
        assertFalse(dialog.checking)

        viewModel.onPassphraseChange("right")
        viewModel.submitPassphrase()

        assertTrue(viewModel.dialog.value is BackupDialog.ChooseImport)
    }

    @Test
    fun `servers already added are left out while merging and brought back when replacing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.enterImportPassphrase("right")

            val choice = viewModel.dialog.value as BackupDialog.ChooseImport
            assertEquals(setOf(1), choice.duplicateServerIds)
            assertEquals(setOf(2), choice.selection.serverIds)

            viewModel.updateImportSelection {
                it.withMode(ImportMode.REPLACE, choice.duplicateServerIds)
            }
            assertEquals(
                setOf(1, 2),
                (viewModel.dialog.value as BackupDialog.ChooseImport).selection.serverIds,
            )
        }

    @Test
    fun `the import applies the selection and closes the dialog`() = runTest {
        val viewModel = viewModel()
        viewModel.enterImportPassphrase("right")

        viewModel.confirmImportSelection()

        assertNull(viewModel.dialog.value)
        verify(backupManager)
            .applyImport(backup, listOf(server(2)), emptySet(), false, ImportMode.MERGE)
    }

    @Test
    fun `an import turning the lock off is confirmed and authenticated before it happens`() =
        runTest {
            backupManager.stub {
                onBlocking { readBackup(any(), eq("right")) } doReturn
                    backup.copy(privacyPreferences = AppPreferences(appLockEnabled = false))
            }
            val viewModel = viewModel()
            viewModel.enterImportPassphrase("right")
            val choice = viewModel.dialog.value as BackupDialog.ChooseImport

            viewModel.confirmImportSelection()
            val warning = viewModel.dialog.value as BackupDialog.ConfirmLockWeakening
            assertEquals(LockWeakening.TURNED_OFF, warning.weakening)

            // Cancelling the warning returns to the selection.
            viewModel.dismissDialog()
            assertEquals(choice, viewModel.dialog.value)

            viewModel.confirmImportSelection()
            viewModel.confirmLockWeakening("Confirm")
            assertEquals(choice, viewModel.dialog.value)
            val onSuccess = argumentCaptor<() -> Unit>()
            verify(appLockManager).authenticate(eq("Confirm"), onSuccess.capture(), any())
            verify(backupManager, never()).applyImport(any(), any(), any(), any(), any())

            onSuccess.firstValue()

            assertNull(viewModel.dialog.value)
            verify(backupManager).applyImport(any(), any(), any(), any(), any())
        }

    @Test
    fun `an export passphrase has to be entered twice the same`() = runTest {
        val viewModel = viewModel()
        viewModel.startExport()
        val choice = viewModel.dialog.value as BackupDialog.ChooseExport
        assertEquals(setOf(1, 3), choice.selection.serverIds)
        assertEquals(PrefGroup.entries.toSet(), choice.selection.prefGroups)

        viewModel.confirmExportSelection()
        viewModel.submitPassphrase()
        assertEquals(
            PassphraseError.EMPTY,
            (viewModel.dialog.value as BackupDialog.ExportPassphrase).error,
        )

        viewModel.onPassphraseChange("secret")
        viewModel.onConfirmationChange("secrer")
        viewModel.submitPassphrase()
        assertEquals(
            PassphraseError.MISMATCH,
            (viewModel.dialog.value as BackupDialog.ExportPassphrase).error,
        )

        viewModel.onConfirmationChange("secret")
        viewModel.submitPassphrase()
        assertNull(viewModel.dialog.value)
        assertTrue(viewModel.pickExportDestination.value)
    }

    @Test
    fun `the export destination is asked for once, and not choosing one drops the export`() =
        runTest {
            val viewModel = viewModel()
            viewModel.startExport()
            viewModel.confirmExportSelection()
            viewModel.onPassphraseChange("secret")
            viewModel.onConfirmationChange("secret")
            viewModel.submitPassphrase()

            viewModel.exportDestinationAsked()
            assertFalse(viewModel.pickExportDestination.value)

            viewModel.exportTo(null)

            verify(backupManager, never()).export(any(), any(), any(), any(), any(), any())
        }

    @Test
    fun `an export with no server chosen can't go ahead`() = runTest {
        val viewModel = viewModel()
        viewModel.startExport()

        viewModel.updateExportSelection { it.copy(serverIds = emptySet()) }
        viewModel.confirmExportSelection()

        assertTrue(viewModel.dialog.value is BackupDialog.ChooseExport)
    }
}
