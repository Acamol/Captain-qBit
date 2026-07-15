package dev.yashgarg.qbit.ui.config

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.manager.CryptoManager
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.utils.friendlyMessage

private val CONNECTION_TYPES = listOf("HTTP", "HTTPS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(appNavigator: AppNavigator, viewModel: ConfigViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existing by viewModel.existingConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val genericError = stringResource(CommonR.string.error)

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connectionType by remember { mutableStateOf(CONNECTION_TYPES.first()) }
    var useBasicAuth by remember { mutableStateOf(false) }
    var basicAuthUser by remember { mutableStateOf("") }
    var basicAuthPass by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var basicPassVisible by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        val config = existing ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        prefilled = true
        name = config.serverName
        host = config.baseUrl
        port = config.port?.toString().orEmpty()
        path = config.path?.removePrefix("/").orEmpty()
        username = config.username
        password = CryptoManager.decrypt(config.password).orEmpty()
        connectionType = config.connectionType.name
        if (!config.basicAuthUsername.isNullOrEmpty()) {
            useBasicAuth = true
            basicAuthUser = config.basicAuthUsername
            basicAuthPass = CryptoManager.decrypt(config.basicAuthPassword).orEmpty()
        }
    }

    // validateForm passing → either test the connection or save, depending on which button was
    // pressed.
    LaunchedEffect(Unit) {
        viewModel.validationEvents.collect { event ->
            val type = connectionType.lowercase()
            val basicUser = if (useBasicAuth) basicAuthUser.ifEmpty { null } else null
            val basicPass = if (useBasicAuth) basicAuthPass.ifEmpty { null } else null
            when ((event as ConfigViewModel.ValidationEvent.Success).action) {
                ConfigViewModel.FormAction.TEST -> {
                    checking = true
                    snackbarHostState.showSnackbar("Checking connection, please wait…")
                    val portPart = if (port.isNotEmpty()) ":$port" else ""
                    val pathPart = if (path.isNotEmpty()) "/$path" else ""
                    viewModel
                        .testConfig(
                            "$type://$host$portPart$pathPart",
                            username,
                            password,
                            basicUser,
                            basicPass,
                        )
                        .onOk { version ->
                            Toast.makeText(
                                    context,
                                    "Success! Client version is $version",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                        .onErr { error ->
                            snackbarHostState.showSnackbar(
                                "Failed! ${error.friendlyMessage(genericError)}"
                            )
                        }
                    checking = false
                }
                ConfigViewModel.FormAction.SAVE -> {
                    viewModel.insert(
                        name,
                        host,
                        port,
                        path,
                        type,
                        username,
                        password,
                        basicUser,
                        basicPass,
                    )
                    appNavigator.navigate(NavCommand.OpenServerAsRoot)
                }
            }
        }
    }

    val title =
        if (viewModel.editing) stringResource(CommonR.string.server_settings)
        else stringResource(CommonR.string.add_server)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(
                value = name,
                onChange = {
                    name = it
                    viewModel.validateName(it)
                },
                label = stringResource(CommonR.string.server_name),
                placeholder = "e.g. homeserver 01",
                isError = state.showServerNameError,
                errorText = stringResource(CommonR.string.invalid_name),
                enabled = !checking,
            )

            // Type + Host on one row (dropdown left, host right), matching the original layout.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { if (!checking) typeExpanded = it },
                    modifier = Modifier.weight(0.42f),
                ) {
                    OutlinedTextField(
                        value = connectionType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(CommonR.string.type)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                        },
                        modifier =
                            Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        CONNECTION_TYPES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    connectionType = option
                                    viewModel.validateConnectionType(option)
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Field(
                    value = host,
                    onChange = {
                        host = it
                        viewModel.validateHostUrl(it)
                    },
                    label = stringResource(CommonR.string.host_or_ip),
                    placeholder = "example.com",
                    isError = state.showUrlError,
                    errorText = stringResource(CommonR.string.invalid_url),
                    enabled = !checking,
                    modifier = Modifier.weight(0.58f),
                )
            }

            Field(
                value = port,
                onChange = {
                    port = it
                    viewModel.validatePort(it)
                },
                label = stringResource(CommonR.string.port),
                placeholder = "0 - 65535",
                prefix = ":",
                isError = state.showPortError,
                errorText = stringResource(CommonR.string.invalid_port),
                keyboardType = KeyboardType.Number,
                enabled = !checking,
            )
            Field(
                value = path,
                onChange = { path = it },
                label = stringResource(CommonR.string.path),
                placeholder = "your/path",
                prefix = "/",
                enabled = !checking,
            )
            Field(
                value = username,
                onChange = {
                    username = it
                    viewModel.validateUsername(it)
                },
                label = stringResource(CommonR.string.username),
                placeholder = "e.g. admin",
                isError = state.showUsernameError,
                errorText = stringResource(CommonR.string.invalid_username),
                enabled = !checking,
            )
            PasswordField(
                value = password,
                onChange = {
                    password = it
                    viewModel.validatePassword(it)
                },
                label = stringResource(CommonR.string.password),
                isError = state.showPasswordError,
                errorText = stringResource(CommonR.string.invalid_password),
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible },
                enabled = !checking,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = useBasicAuth,
                    onCheckedChange = {
                        useBasicAuth = it
                        viewModel.toggleBasicAuth(it)
                        // Prefill basic-auth credentials from the client ones when turning it on,
                        // without clobbering anything the user already typed. They can still edit.
                        if (it) {
                            if (basicAuthUser.isEmpty()) {
                                basicAuthUser = username
                                viewModel.validateBasicAuthUsername(username)
                            }
                            if (basicAuthPass.isEmpty()) {
                                basicAuthPass = password
                                viewModel.validateBasicAuthPassword(password)
                            }
                        }
                    },
                    enabled = !checking,
                )
                Spacer(Modifier.size(12.dp))
                Text(stringResource(CommonR.string.use_basic_auth))
            }

            if (useBasicAuth) {
                Field(
                    value = basicAuthUser,
                    onChange = {
                        basicAuthUser = it
                        viewModel.validateBasicAuthUsername(it)
                    },
                    label = stringResource(CommonR.string.basic_auth_username),
                    isError = state.showBasicAuthUsernameError,
                    errorText = stringResource(CommonR.string.invalid_username),
                    enabled = !checking,
                )
                PasswordField(
                    value = basicAuthPass,
                    onChange = {
                        basicAuthPass = it
                        viewModel.validateBasicAuthPassword(it)
                    },
                    label = stringResource(CommonR.string.basic_auth_password),
                    isError = state.showBasicAuthPasswordError,
                    errorText = stringResource(CommonR.string.invalid_password),
                    visible = basicPassVisible,
                    onToggleVisible = { basicPassVisible = !basicPassVisible },
                    enabled = !checking,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.validateForm(
                            name,
                            host,
                            port,
                            connectionType,
                            username,
                            password,
                            useBasicAuth,
                            basicAuthUser,
                            basicAuthPass,
                            ConfigViewModel.FormAction.TEST,
                        )
                    },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    } else {
                        Text(stringResource(CommonR.string.test_cfg))
                    }
                }
                Button(
                    onClick = {
                        viewModel.validateForm(
                            name,
                            host,
                            port,
                            connectionType,
                            username,
                            password,
                            useBasicAuth,
                            basicAuthUser,
                            basicAuthPass,
                            ConfigViewModel.FormAction.SAVE,
                        )
                    },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(CommonR.string.save_cfg))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        prefix = if (prefix != null) ({ Text(prefix) }) else null,
        isError = isError,
        supportingText = if (isError && errorText != null) ({ Text(errorText) }) else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError) ({ Text(errorText) }) else null,
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
