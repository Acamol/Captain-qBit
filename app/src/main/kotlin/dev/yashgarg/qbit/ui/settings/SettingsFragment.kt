package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.QbitApplication
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.databinding.SettingsFragmentBinding
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.worker.StatusWorker

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.settings_fragment) {
    private val binding by viewBinding(SettingsFragmentBinding::bind)
    private val viewModel by viewModels<SettingsViewModel>()
    private val backupViewModel by viewModels<BackupViewModel>()

    // The selection + passphrase, held until the user picks an export destination.
    private data class PendingExport(
        val passphrase: String,
        val serverIds: Set<Int>,
        val prefGroups: Set<PrefGroup>,
        val includeCategoryColors: Boolean,
    )

    private var pendingExport: PendingExport? = null

    // SAF: the system file creator returns where to write the backup. Selection and passphrase are
    // collected first and held in pendingExport until the user picks a destination.
    private val exportLauncher =
        registerForActivityResult(
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

    // SAF: the system file picker returns the backup to restore. The passphrase is collected next;
    // the selection dialog is shown once the file is decrypted (BackupEvent.Loaded).
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                BackupDialogs.showPassphraseDialog(
                    requireContext(),
                    title = "Backup passphrase",
                    confirm = false,
                ) { passphrase ->
                    backupViewModel.beginImport(uri, passphrase)
                }
            }
        }

    // On Android 13+ notifications need runtime permission; if granted, (re)start the service.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) StatusWorker.enqueue(requireContext())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

            // Dynamic color only exists on Android 12+ with OEM support; hide the whole option
            // (not just a caveat) when the device can't do it.
            if (DynamicColors.isDynamicColorAvailable()) {
                // Reflect the stored value. Uses a click listener (not checked-change) so this
                // programmatic update doesn't loop back into a toggle.
                viewModel.dynamicColors.collectWithLifecycle(this@SettingsFragment) {
                    dynamicColorsSwitch.isChecked = it
                }
                dynamicColorsSwitch.setOnClickListener {
                    val enabled = dynamicColorsSwitch.isChecked
                    viewModel.setDynamicColors(enabled)
                    // Update the cached flag the DynamicColors precondition reads, then recreate
                    // so the palette is applied/reverted immediately.
                    QbitApplication.dynamicColorsEnabled = enabled
                    requireActivity().recreate()
                }
            } else {
                dynamicColorsGroup.visibility = View.GONE
            }

            viewModel.themeMode.collectWithLifecycle(this@SettingsFragment) {
                themeSubtitle.text = themeLabel(it)
            }
            themeSetting.setOnClickListener { showThemeDialog() }

            serverSettings.setOnClickListener {
                findNavController().navigate(R.id.action_settingsFragment_to_serverListFragment)
            }
            about.setOnClickListener {
                findNavController().navigate(R.id.action_settingsFragment_to_versionFragment)
            }

            exportConfig.setOnClickListener { startExport() }
            importConfig.setOnClickListener {
                importLauncher.launch(
                    arrayOf("application/json", "application/octet-stream", "*/*")
                )
            }

            // Notification toggles. Reflect stored values via collect and use click listeners so
            // the programmatic update doesn't loop back. Any change re-evaluates the service.
            viewModel.statusNotification.collectWithLifecycle(this@SettingsFragment) {
                statusNotificationSwitch.isChecked = it
            }
            viewModel.notifyOnComplete.collectWithLifecycle(this@SettingsFragment) {
                notifyCompleteSwitch.isChecked = it
            }
            viewModel.notifyOnChecked.collectWithLifecycle(this@SettingsFragment) {
                notifyCheckedSwitch.isChecked = it
            }
            statusNotificationSwitch.setOnClickListener {
                viewModel.setStatusNotification(statusNotificationSwitch.isChecked)
                applyNotificationPrefs()
            }
            notifyCompleteSwitch.setOnClickListener {
                viewModel.setNotifyOnComplete(notifyCompleteSwitch.isChecked)
                applyNotificationPrefs()
            }
            notifyCheckedSwitch.setOnClickListener {
                viewModel.setNotifyOnChecked(notifyCheckedSwitch.isChecked)
                applyNotificationPrefs()
            }
        }

        backupViewModel.backupEvents.collectWithLifecycle(this) { event ->
            when (event) {
                is BackupViewModel.BackupEvent.Exported ->
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                is BackupViewModel.BackupEvent.Failed ->
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                is BackupViewModel.BackupEvent.Loaded ->
                    BackupDialogs.showImportSelectionDialog(
                        requireContext(),
                        event.backup,
                        event.duplicateServerIds,
                    ) { serverIds, prefGroups, includeColors, mode ->
                        backupViewModel.applyImport(serverIds, prefGroups, includeColors, mode)
                    }
                // Theme/dynamic colors are applied by MainActivity's prefs observer.
                is BackupViewModel.BackupEvent.Imported ->
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun themeLabel(mode: Int): String =
        when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System default"
        }

    private fun showThemeDialog() {
        val modes =
            intArrayOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
            )
        val labels = arrayOf("System default", "Light", "Dark")
        val checked = modes.indexOf(viewModel.themeMode.value).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Theme")
            .setNegativeButton("Cancel", null)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val mode = modes[which]
                viewModel.setThemeMode(mode)
                // Applies immediately and recreates started activities.
                AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
            }
            .show()
    }

    // Starts the monitoring service when any notification is enabled (requesting permission first
    // on Android 13+), or stops it when all are off.
    private fun applyNotificationPrefs() {
        val anyOn =
            with(binding) {
                statusNotificationSwitch.isChecked ||
                    notifyCompleteSwitch.isChecked ||
                    notifyCheckedSwitch.isChecked
            }
        val context = requireContext()
        if (!anyOn) {
            StatusWorker.cancel(context)
            return
        }
        if (AppNotificationManager.checkPermission(context)) {
            StatusWorker.enqueue(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startExport() {
        BackupDialogs.showExportSelectionDialog(requireContext(), backupViewModel.servers.value) {
            serverIds,
            prefGroups,
            includeColors ->
            BackupDialogs.showPassphraseDialog(
                requireContext(),
                title = "Encrypt backup",
                confirm = true,
            ) { passphrase ->
                pendingExport = PendingExport(passphrase, serverIds, prefGroups, includeColors)
                exportLauncher.launch("captain-qbit-backup.cqb")
            }
        }
    }
}
