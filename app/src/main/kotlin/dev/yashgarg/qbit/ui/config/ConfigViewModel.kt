package dev.yashgarg.qbit.ui.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.CryptoManager
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.validation.HostValidator
import dev.yashgarg.qbit.validation.PortValidator
import dev.yashgarg.qbit.validation.StringValidator
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qbittorrent.*

@HiltViewModel
class ConfigViewModel
@Inject
constructor(
    private val configDao: ConfigDao,
    private val clientManager: ClientManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // -1 = adding a new server; >= 0 = editing that server id.
    private val serverId: Int = savedStateHandle.get<Int>("serverId") ?: -1

    private val hostValidator = HostValidator()
    private val portValidator = PortValidator()
    private val textValidator = StringValidator()

    private val _uiState = MutableStateFlow(ConfigState())
    val uiState = _uiState.asStateFlow()

    private val _existingConfig = MutableStateFlow<ServerConfig?>(null)
    val existingConfig = _existingConfig.asStateFlow()

    init {
        if (serverId >= 0) {
            viewModelScope.launch(Dispatchers.IO) {
                _existingConfig.value = configDao.getConfigById(serverId)
            }
        }
    }

    private val validationEventChannel = Channel<ValidationEvent>()
    val validationEvents = validationEventChannel.receiveAsFlow()

    sealed class ValidationEvent {
        object Success : ValidationEvent()
    }

    fun validateHostUrl(url: String) {
        if (url.isEmpty()) {
            _uiState.update { state -> state.copy(isServerUrlValid = false, showUrlError = false) }
            return
        }

        val isValid = hostValidator.isValid(url)
        _uiState.update { state -> state.copy(isServerUrlValid = true, showUrlError = !isValid) }
    }

    fun validatePort(port: String) {
        if (port.isEmpty()) {
            _uiState.update { state -> state.copy(isPortValid = false, showPortError = false) }
            return
        }

        val isValid = portValidator.isValid(port)
        _uiState.update { state -> state.copy(isPortValid = true, showPortError = !isValid) }
    }

    fun validateUsername(user: String) {
        if (user.isEmpty()) {
            _uiState.update { state ->
                state.copy(isUsernameValid = false, showUsernameError = false)
            }
            return
        }

        val isValid = textValidator.isValid(user)
        _uiState.update { state ->
            state.copy(isUsernameValid = true, showUsernameError = !isValid)
        }
    }

    fun validatePassword(text: String) {
        if (text.isEmpty()) {
            _uiState.update { state ->
                state.copy(isPasswordValid = false, showPasswordError = false)
            }
            return
        }

        val isValid = textValidator.isValid(text)
        _uiState.update { state ->
            state.copy(isPasswordValid = true, showPasswordError = !isValid)
        }
    }

    fun validateName(name: String) {
        if (name.isEmpty()) {
            _uiState.update { state ->
                state.copy(isServerNameValid = false, showServerNameError = false)
            }
            return
        }

        val isValid = textValidator.isValid(name)
        _uiState.update { state ->
            state.copy(isServerNameValid = true, showServerNameError = !isValid)
        }
    }

    fun toggleBasicAuth(enabled: Boolean) {
        _uiState.update { state -> state.copy(useBasicAuth = enabled) }
    }

    fun validateBasicAuthUsername(user: String) {
        if (user.isEmpty()) {
            _uiState.update { state ->
                state.copy(isBasicAuthUsernameValid = false, showBasicAuthUsernameError = false)
            }
            return
        }
        val isValid = textValidator.isValid(user)
        _uiState.update { state ->
            state.copy(isBasicAuthUsernameValid = isValid, showBasicAuthUsernameError = !isValid)
        }
    }

    fun validateBasicAuthPassword(text: String) {
        if (text.isEmpty()) {
            _uiState.update { state ->
                state.copy(isBasicAuthPasswordValid = false, showBasicAuthPasswordError = false)
            }
            return
        }
        val isValid = textValidator.isValid(text)
        _uiState.update { state ->
            state.copy(isBasicAuthPasswordValid = isValid, showBasicAuthPasswordError = !isValid)
        }
    }

    fun validateConnectionType(type: String) {
        if (type.isEmpty()) {
            _uiState.update { state ->
                state.copy(isConnectionTypeValid = false, showConnectionTypeError = false)
            }
            return
        }

        val isValid = textValidator.isValid(type)
        _uiState.update { state ->
            state.copy(isConnectionTypeValid = true, showConnectionTypeError = !isValid)
        }
    }

    fun validateForm(
        serverName: String,
        serverHost: String,
        port: String,
        connectionType: String,
        username: String,
        password: String,
        useBasicAuth: Boolean,
        basicAuthUsername: String,
        basicAuthPassword: String,
    ) {
        val serverNameValid = textValidator.isValid(serverName)
        val serverHostValid = hostValidator.isValid(serverHost)
        val portValid = portValidator.isValid(port)
        val usernameValid = textValidator.isValid(username)
        val passwordValid = textValidator.isValid(password)
        val connectionTypeValid = textValidator.isValid(connectionType)
        val basicAuthUsernameValid = !useBasicAuth || textValidator.isValid(basicAuthUsername)
        val basicAuthPasswordValid = !useBasicAuth || textValidator.isValid(basicAuthPassword)

        val hasError =
            listOf(
                    serverNameValid,
                    serverHostValid,
                    portValid,
                    usernameValid,
                    passwordValid,
                    connectionTypeValid,
                    basicAuthUsernameValid,
                    basicAuthPasswordValid,
                )
                .any { !it }

        if (hasError) {
            _uiState.update { state ->
                state.copy(
                    isServerNameValid = serverNameValid,
                    isServerUrlValid = serverHostValid,
                    isPortValid = portValid,
                    isUsernameValid = usernameValid,
                    isPasswordValid = passwordValid,
                    isConnectionTypeValid = connectionTypeValid,
                    showServerNameError = !serverNameValid,
                    showUsernameError = !usernameValid,
                    showPasswordError = !passwordValid,
                    showPortError = !portValid,
                    showUrlError = !serverHostValid,
                    showConnectionTypeError = !connectionTypeValid,
                    showBasicAuthUsernameError = !basicAuthUsernameValid,
                    showBasicAuthPasswordError = !basicAuthPasswordValid,
                )
            }
        } else {
            viewModelScope.launch { validationEventChannel.send(ValidationEvent.Success) }
        }
    }

    fun insert(
        serverName: String,
        serverHost: String,
        port: String,
        path: String,
        connectionType: String,
        username: String,
        password: String,
        basicAuthUsername: String?,
        basicAuthPassword: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val editing = serverId >= 0
            val wasEmpty = configDao.maxConfigId() == -1
            val newId = if (editing) serverId else configDao.maxConfigId() + 1
            val config =
                ServerConfig(
                    configId = newId,
                    serverName = serverName.trim(),
                    baseUrl = serverHost.trim(),
                    port = if (port.isEmpty()) null else port.trim().toInt(),
                    path = if (path.isEmpty()) null else "/$path",
                    username = username.trim(),
                    password = CryptoManager.encrypt(password.trim()) ?: password.trim(),
                    connectionType =
                        if (connectionType.trim() == "http") ConnectionType.HTTP
                        else ConnectionType.HTTPS,
                    trustSelfSigned = false,
                    basicAuthUsername = basicAuthUsername?.trim()?.ifEmpty { null },
                    basicAuthPassword =
                        CryptoManager.encrypt(basicAuthPassword?.trim()?.ifEmpty { null }),
                )
            configDao.addConfig(config)
            // First server ever added becomes the active one.
            if (!editing && wasEmpty) clientManager.setActiveServer(newId)
        }
    }

    suspend fun testConfig(
        baseUrl: String,
        username: String,
        password: String,
        basicAuthUsername: String?,
        basicAuthPassword: String?,
    ): Result<String, Throwable> {
        return runCatching {
            val basicAuth =
                if (!basicAuthUsername.isNullOrEmpty() && !basicAuthPassword.isNullOrEmpty()) {
                    basicAuthUsername to basicAuthPassword
                } else null
            val client =
                QBittorrentClient(
                    baseUrl,
                    username,
                    password,
                    httpClient = ClientManager.httpClient(basicAuth),
                    dispatcher = Dispatchers.Default
                )
            client.getVersion()
        }
    }
}
