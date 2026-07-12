package dev.yashgarg.qbit.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.QbitApplication
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.SettingsFragmentBinding
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.settings_fragment) {
    private val binding by viewBinding(SettingsFragmentBinding::bind)
    private val viewModel by viewModels<SettingsViewModel>()

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

            serverSettings.setOnClickListener {
                findNavController().navigate(R.id.action_settingsFragment_to_serverListFragment)
            }
            about.setOnClickListener {
                findNavController().navigate(R.id.action_settingsFragment_to_versionFragment)
            }
        }
    }
}
