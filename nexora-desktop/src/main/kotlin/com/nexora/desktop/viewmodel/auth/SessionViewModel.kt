package com.nexora.desktop.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.AuthSession
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.AuthRepository
import com.nexora.desktop.repository.SystemSettingRepository
import com.nexora.desktop.ui.components.ErrorMessages
import com.nexora.desktop.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { STARTING, SERVER_SETUP, ONBOARDING, LOGIN, APP }

data class SessionState(
    val phase: Phase = Phase.STARTING,
    val user: MeResponse? = null,
    val reLoginPassword: String = "",
    val reLoginError: String? = null,
    val reLoggingIn: Boolean = false,
)

/**
 * Decides which top-level screen is shown (server setup, onboarding, login, the app) and handles the session-expired dialog.
 */
class SessionViewModel(
    private val auth: AuthRepository,
    private val session: AuthSession,
    private val settings: SettingsStore,
    private val systemSettingRepo: SystemSettingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** True while the session-expired dialog must be shown. */
    val expired: StateFlow<Boolean> = session.expired

    fun start() {
        if (settings.current.serverUrl.isNullOrBlank()) {
            _state.update { it.copy(phase = Phase.SERVER_SETUP) }
            return
        }
        viewModelScope.launch {
            // A stored refresh token logs the user in without the password
            when (val result = auth.resume()) {
                is ApiResult.Success -> _state.update { it.copy(phase = Phase.APP, user = result.value) }
                is ApiResult.Failure -> checkSetupOrLogin()
            }
        }
    }

    fun onServerSaved() {
        viewModelScope.launch {
            checkSetupOrLogin()
        }
    }

    private suspend fun checkSetupOrLogin() {
        val setup = systemSettingRepo.getSetupStatus().getOrNull()
        if (setup != null && !setup.initialized) {
            _state.update { it.copy(phase = Phase.ONBOARDING) }
        } else {
            _state.update { it.copy(phase = Phase.LOGIN) }
        }
    }

    fun onOnboardingComplete() {
        viewModelScope.launch {
            when (val me = auth.me()) {
                is ApiResult.Success -> _state.update { it.copy(phase = Phase.APP, user = me.value) }
                is ApiResult.Failure -> _state.update { it.copy(phase = Phase.LOGIN) }
            }
        }
    }

    fun changeServer() = _state.update { it.copy(phase = Phase.SERVER_SETUP) }

    fun startSetup() = _state.update { it.copy(phase = Phase.ONBOARDING) }

    fun backToLogin() = _state.update { it.copy(phase = Phase.LOGIN) }

    fun onLoggedIn(user: MeResponse) = _state.update { it.copy(phase = Phase.APP, user = user) }

    fun onReLoginPasswordChange(text: String) = _state.update { it.copy(reLoginPassword = text, reLoginError = null) }

    fun reLogin() {
        val username = session.username ?: settings.current.lastUsername ?: return
        _state.update { it.copy(reLoggingIn = true) }
        viewModelScope.launch {
            when (val result = auth.login(username, _state.value.reLoginPassword)) {
                is ApiResult.Success -> _state.update { it.copy(user = result.value, reLoginPassword = "", reLoggingIn = false) }
                is ApiResult.Failure -> _state.update { it.copy(reLoginError = ErrorMessages.message(result.error), reLoggingIn = false) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            _state.update { it.copy(phase = Phase.LOGIN, user = null) }
        }
    }

    fun logoutAndChangeServer() {
        viewModelScope.launch {
            auth.logout()
            _state.update { it.copy(phase = Phase.SERVER_SETUP, user = null) }
        }
    }

    fun username(): String = session.username ?: settings.current.lastUsername.orEmpty()
}
