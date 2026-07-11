package dev.yashgarg.qbit.ui.config

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.R as AppR
import dev.yashgarg.qbit.common.R
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.CryptoManager
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.databinding.ConfigFragmentBinding
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConfigFragment : Fragment(AppR.layout.config_fragment) {
    private val binding by viewBinding(ConfigFragmentBinding::bind)
    private val viewModel by viewModels<ConfigViewModel>()
    private var fieldsPopulated = false

    private val connectionTypes = arrayOf("HTTP", "HTTPS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeFlows()
        watchTextFields()
        setupActionbar()

        val adapter = ArrayAdapter(requireContext(), AppR.layout.list_item, connectionTypes)

        with(binding) {
            (typeDropdown.editText as? AutoCompleteTextView)?.setAdapter(adapter)

            useBasicAuth.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleBasicAuth(isChecked)
                basicAuthUsernameTil.isVisible = isChecked
                basicAuthPasswordTil.isVisible = isChecked
            }

            saveButton.setOnClickListener {
                viewModel.validateForm(
                    serverNameTil.editText?.text.toString(),
                    serverHostTil.editText?.text.toString(),
                    serverPortTil.editText?.text.toString(),
                    typeDropdown.editText?.text.toString(),
                    serverUsernameTil.editText?.text.toString(),
                    serverPasswordTil.editText?.text.toString(),
                    useBasicAuth.isChecked,
                    basicAuthUsernameTil.editText?.text.toString(),
                    basicAuthPasswordTil.editText?.text.toString(),
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (activity as AppCompatActivity).setSupportActionBar(null)
    }

    private fun setupActionbar() {
        val activity = (activity as AppCompatActivity)
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setHomeButtonEnabled(true)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener { it.findNavController().navigateUp() }
    }

    private fun observeFlows() {
        viewModel.uiState.collectWithLifecycle(this, ::render)

        viewModel.validationEvents.collectWithLifecycle(this, ::handleEvent)

        viewModel.existingConfig.collectWithLifecycle(this) { config ->
            if (config != null && !fieldsPopulated) {
                fieldsPopulated = true
                populateFields(config)
            }
        }
    }

    private fun populateFields(config: ServerConfig) {
        with(binding) {
            serverNameTiet.setText(config.serverName)
            serverHostTiet.setText(config.baseUrl)
            config.port?.let { serverPortTiet.setText(it.toString()) }
            serverPathTiet.setText(config.path?.removePrefix("/") ?: "")
            serverUsernameTiet.setText(config.username)
            serverPasswordTiet.setText(CryptoManager.decrypt(config.password))
            typeTextview.setText(config.connectionType.name, false)
            if (!config.basicAuthUsername.isNullOrEmpty()) {
                useBasicAuth.isChecked = true
                basicAuthUsernameTil.isVisible = true
                basicAuthPasswordTil.isVisible = true
                basicAuthUsernameTiet.setText(config.basicAuthUsername)
                basicAuthPasswordTiet.setText(CryptoManager.decrypt(config.basicAuthPassword) ?: "")
            }
            toolbar.title = getString(R.string.edit_server)
        }
    }

    private fun watchTextFields() {
        with(binding) {
            serverHostTiet.doAfterTextChanged { text -> viewModel.validateHostUrl(text.toString()) }

            serverPortTiet.doAfterTextChanged { text -> viewModel.validatePort(text.toString()) }

            serverUsernameTiet.doAfterTextChanged { text ->
                viewModel.validateUsername(text.toString())
            }

            typeTextview.doAfterTextChanged { text ->
                viewModel.validateConnectionType(text.toString())
            }

            serverNameTiet.doAfterTextChanged { text -> viewModel.validateName(text.toString()) }

            serverPasswordTiet.doAfterTextChanged { text ->
                viewModel.validatePassword(text.toString())
            }

            basicAuthUsernameTiet.doAfterTextChanged { text ->
                viewModel.validateBasicAuthUsername(text.toString())
            }

            basicAuthPasswordTiet.doAfterTextChanged { text ->
                viewModel.validateBasicAuthPassword(text.toString())
            }
        }
    }

    private fun enableFields(enabled: Boolean) {
        with(binding) {
            enabled.apply {
                serverNameTiet.isEnabled = this
                serverHostTiet.isEnabled = this
                serverPortTiet.isEnabled = this
                typeDropdown.isEnabled = this
                serverUsernameTiet.isEnabled = this
                serverPasswordTiet.isEnabled = this
                useBasicAuth.isEnabled = this
                basicAuthUsernameTiet.isEnabled = this
                basicAuthPasswordTiet.isEnabled = this
                saveButton.isEnabled = this
            }
        }
    }

    private fun handleEvent(event: ConfigViewModel.ValidationEvent) {
        when (event) {
            is ConfigViewModel.ValidationEvent.Success -> {
                enableFields(false)
                val checkSnackbar =
                    Snackbar.make(
                        requireView(),
                        "Checking connection, please wait...",
                        Snackbar.LENGTH_SHORT
                    )
                checkSnackbar.show()

                with(binding) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val connectionType = typeDropdown.editText?.text.toString().lowercase()
                        val serverHost = serverHostTil.editText?.text.toString()
                        val username = serverUsernameTil.editText?.text.toString()
                        val password = serverPasswordTil.editText?.text.toString()
                        val path = serverPathTil.editText?.text.toString()
                        val port = serverPortTil.editText?.text
                        val basicAuthUser =
                            basicAuthUsernameTil.editText?.text?.toString()?.ifEmpty { null }
                        val basicAuthPass =
                            basicAuthPasswordTil.editText?.text?.toString()?.ifEmpty { null }

                        val connectionResponse =
                            viewModel.testConfig(
                                "$connectionType://$serverHost${if (!port.isNullOrEmpty()) ":$port" else ""}" +
                                    if (path.isNotEmpty()) "/$path" else "",
                                username,
                                password,
                                if (useBasicAuth.isChecked) basicAuthUser else null,
                                if (useBasicAuth.isChecked) basicAuthPass else null,
                            )

                        when (connectionResponse) {
                            is Ok -> {
                                checkSnackbar.dismiss()
                                Toast.makeText(
                                        context,
                                        "Success! Client app version is ${connectionResponse.value}",
                                        Toast.LENGTH_LONG
                                    )
                                    .show()

                                viewModel.insert(
                                    serverNameTil.editText?.text.toString(),
                                    serverHost,
                                    port.toString(),
                                    path,
                                    connectionType,
                                    username,
                                    password,
                                    if (useBasicAuth.isChecked) basicAuthUser else null,
                                    if (useBasicAuth.isChecked) basicAuthPass else null,
                                )

                                val isEdit =
                                    findNavController().previousBackStackEntry?.destination?.id ==
                                        AppR.id.serverFragment
                                if (isEdit) {
                                    // Pop old ServerFragment + ConfigFragment, push fresh one
                                    val navOptions =
                                        NavOptions.Builder()
                                            .setPopUpTo(AppR.id.serverFragment, true)
                                            .build()
                                    findNavController()
                                        .navigate(AppR.id.serverFragment, null, navOptions)
                                } else {
                                    findNavController()
                                        .navigate(AppR.id.action_configFragment_to_serverFragment)
                                }
                            }
                            is Err -> {
                                Log.e(ClientManager.tag, connectionResponse.error.toString())
                                Snackbar.make(
                                        requireView(),
                                        "Failed! ${connectionResponse.error.message}",
                                        Snackbar.LENGTH_LONG
                                    )
                                    .show()
                            }
                        }
                        enableFields(true)
                    }
                }
            }
        }
    }

    private fun render(state: ConfigState) {
        with(binding) {
            if (state.showServerNameError) {
                serverNameTil.error = getString(R.string.invalid_name)
            } else {
                serverNameTil.error = null
            }

            if (state.showUrlError) {
                serverHostTil.error = getString(R.string.invalid_url)
            } else {
                serverHostTil.error = null
            }

            if (state.showPortError) {
                serverPortTil.error = getString(R.string.invalid_port)
            } else {
                serverPortTil.error = null
            }

            if (state.showUsernameError) {
                serverUsernameTil.error = getString(R.string.invalid_username)
            } else {
                serverUsernameTil.error = null
            }

            if (state.showConnectionTypeError) {
                typeDropdown.error = getString(R.string.invalid_type)
            } else {
                typeDropdown.error = null
            }

            if (state.showPasswordError) {
                serverPasswordTil.error = getString(R.string.invalid_password)
            } else {
                serverPasswordTil.error = null
            }

            if (state.showBasicAuthUsernameError) {
                basicAuthUsernameTil.error = getString(R.string.invalid_username)
            } else {
                basicAuthUsernameTil.error = null
            }

            if (state.showBasicAuthPasswordError) {
                basicAuthPasswordTil.error = getString(R.string.invalid_password)
            } else {
                basicAuthPasswordTil.error = null
            }
        }
    }
}
