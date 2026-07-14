package dev.yashgarg.qbit.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.databinding.HomeFragmentBinding
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.home_fragment) {
    private val binding by viewBinding(HomeFragmentBinding::bind)
    private val backupViewModel by viewModels<BackupViewModel>()

    @Inject lateinit var clientManager: ClientManager

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)

        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.homeFragment) {
            binding.addServerFab.setOnClickListener {
                navController.navigate(R.id.action_homeFragment_to_configFragment)
            }
        }

        binding.restoreBackupButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }

        // Keep the spinner until we know there's genuinely no server, so the welcome screen doesn't
        // flash before MainActivity navigates an existing server to the list.
        clientManager.configStatus.collectWithLifecycle(this) { status ->
            val noServer = status == ConfigStatus.DOES_NOT_EXIST
            binding.loadingIndicator.isVisible = !noServer
            binding.welcomeContent.isVisible = noServer
            binding.addServerFab.isVisible = noServer
        }

        backupViewModel.backupEvents.collectWithLifecycle(this) { event ->
            when (event) {
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
                // Theme is applied by MainActivity's prefs observer; leaving the empty state is
                // handled by the config-status observer once a server exists.
                is BackupViewModel.BackupEvent.Imported ->
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                // Export isn't offered from the first-run screen.
                is BackupViewModel.BackupEvent.Exported -> Unit
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (activity as AppCompatActivity).setSupportActionBar(null)
    }
}
