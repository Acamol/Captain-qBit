package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import dev.yashgarg.qbit.databinding.SettingsFragmentBinding
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.worker.StatusWorker

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.settings_fragment) {
    private val binding by viewBinding(SettingsFragmentBinding::bind)
    private val viewModel by viewModels<SettingsViewModel>()

    private var pendingExportPassphrase: String? = null

    // SAF: the system file creator returns where to write the backup. Passphrase is collected first
    // and held in pendingExportPassphrase until the user picks a destination.
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            val passphrase = pendingExportPassphrase
            pendingExportPassphrase = null
            if (uri != null && passphrase != null) viewModel.exportConfig(uri, passphrase)
        }

    // SAF: the system file picker returns the backup to restore. Confirmation + passphrase are
    // collected after a file is chosen.
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmThenImport(uri)
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

        viewModel.backupEvents.collectWithLifecycle(this) { event ->
            when (event) {
                is SettingsViewModel.BackupEvent.Exported ->
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                is SettingsViewModel.BackupEvent.Failed ->
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                is SettingsViewModel.BackupEvent.Imported -> {
                    // Recreate so a restored theme (dynamic colors) is applied immediately.
                    QbitApplication.dynamicColorsEnabled = viewModel.dynamicColors.value
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    requireActivity().recreate()
                }
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
        showPassphraseDialog(title = "Encrypt backup", confirm = true) { passphrase ->
            pendingExportPassphrase = passphrase
            exportLauncher.launch("captain-qbit-backup.json")
        }
    }

    private fun confirmThenImport(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import configuration")
            .setMessage(
                "This replaces your current servers and app settings with the backup's contents. " +
                    "Continue?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                showPassphraseDialog(title = "Backup passphrase", confirm = false) { passphrase ->
                    viewModel.importConfig(uri, passphrase)
                }
            }
            .show()
    }

    /**
     * Prompts for a passphrase (min 8 chars). When [confirm] is set, a second field must match. The
     * OK button validates inline and only dismisses once the input is valid.
     */
    private fun showPassphraseDialog(title: String, confirm: Boolean, onOk: (String) -> Unit) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val horizontal = (24 * density).toInt()
        val vertical = (8 * density).toInt()

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(horizontal, vertical, horizontal, 0)
            }
        val passField =
            EditText(context).apply {
                hint = "Passphrase"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        container.addView(passField)
        val confirmField =
            if (confirm) {
                EditText(context)
                    .apply {
                        hint = "Confirm passphrase"
                        inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    .also(container::addView)
            } else null

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val passphrase = passField.text?.toString().orEmpty()
                when {
                    passphrase.length < 8 -> passField.error = "At least 8 characters"
                    confirm && passphrase != confirmField?.text?.toString() ->
                        confirmField?.error = "Passphrases don't match"
                    else -> {
                        dialog.dismiss()
                        onOk(passphrase)
                    }
                }
            }
        }
        dialog.show()
    }
}
