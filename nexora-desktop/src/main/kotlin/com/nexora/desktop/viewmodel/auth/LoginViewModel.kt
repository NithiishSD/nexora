package com.nexora.desktop.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.AuthRepository
import com.nexora.desktop.repository.SystemSettingRepository
import com.nexora.desktop.ui.components.ErrorMessages
import com.nexora.desktop.util.CredentialStore
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.util.s
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val submitting: Boolean = false,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    /** One-line notice when the OS has no credential store (Linux without Secret Service). */
    val credentialStoreMissing: Boolean = false,
    val showForgotPasswordDialog: Boolean = false,
    val showAlreadyRegisteredDialog: Boolean = false,
    val checkingSetup: Boolean = false,
)

/** S-AUTH-02 Login (FR-AUTH-01, FR-AUTH-07). */
class LoginViewModel(
    private val auth: AuthRepository,
    settings: SettingsStore,
    credentials: CredentialStore,
    private val systemSettingRepo: SystemSettingRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        // The username defaults to the last one used on this computer (06 S-AUTH-02).
        LoginState(username = settings.current.lastUsername.orEmpty(), credentialStoreMissing = !credentials.available),
    )
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onUsernameChange(text: String) = _state.update { it.copy(username = text.take(100), usernameError = null, error = null) }

    fun onPasswordChange(text: String) = _state.update { it.copy(password = text.take(200), passwordError = null, error = null) }

    fun toggleShowPassword() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun onForgotPasswordClick() = _state.update { it.copy(showForgotPasswordDialog = true) }

    fun dismissForgotPasswordDialog() = _state.update { it.copy(showForgotPasswordDialog = false) }

    fun onCreateAccountClick(onNavigateToSetup: () -> Unit) {
        val repo = systemSettingRepo
        if (repo == null) {
            onNavigateToSetup()
            return
        }
        if (_state.value.checkingSetup) return
        _state.update { it.copy(checkingSetup = true, error = null) }
        viewModelScope.launch {
            val result = repo.getSetupStatus()
            val setup = result.getOrNull()
            _state.update { it.copy(checkingSetup = false) }
            if (setup != null) {
                if (!setup.initialized) {
                    onNavigateToSetup()
                } else {
                    _state.update { it.copy(showAlreadyRegisteredDialog = true) }
                }
            } else {
                _state.update { it.copy(showAlreadyRegisteredDialog = true) }
            }
        }
    }

    fun dismissAlreadyRegisteredDialog() = _state.update { it.copy(showAlreadyRegisteredDialog = false) }

    fun login(onSuccess: (MeResponse) -> Unit) {
        val current = _state.value
        if (current.submitting) return // a second Enter while waiting does nothing
        val usernameError = if (current.username.isBlank()) s("validation.required") else null
        val passwordError = if (current.password.isEmpty()) s("validation.required") else null
        if (usernameError != null || passwordError != null) {
            _state.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.login(current.username, current.password)) {
                is ApiResult.Success -> {
                    // The password is not kept once it has been used.
                    _state.update { it.copy(submitting = false, password = "") }
                    onSuccess(result.value)
                }
                // Only the message for the code is shown, never the server detail: a locked account must look
                // exactly like a wrong password (FR-AUTH-07).
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = ErrorMessages.message(result.error)) }
            }
        }
    }
}

