package com.nexora.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.desktop.ui.auth.LoginScreen
import com.nexora.desktop.ui.auth.OnboardingScreen
import com.nexora.desktop.ui.auth.ServerSetupScreen
import com.nexora.desktop.ui.auth.SessionExpiredDialog
import com.nexora.desktop.ui.shell.AppShell
import com.nexora.desktop.ui.theme.AppTheme
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.auth.Phase
import com.nexora.desktop.viewmodel.auth.SessionViewModel
import org.koin.compose.getKoin

/**
 * Top of the UI tree: picks server setup, login or the application, and lays the session-expired dialog over
 * whatever is open. View-models live in a store owned here, so they survive recomposition.
 */
@Composable
fun AppRoot() {
    val owner = remember { RootOwner() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val koin = getKoin()
        val session: SessionViewModel = viewModel { koin.get() }
        val state by session.state.collectAsState()
        val expired by session.expired.collectAsState()
        LaunchedEffect(Unit) { session.start() }
        val settings by koin.get<SettingsStore>().settings.collectAsState()
        AppTheme(settings.textScale, darkMode = settings.darkMode) {
            Surface(Modifier.fillMaxSize()) {
                when (state.phase) {
                    Phase.STARTING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    Phase.SERVER_SETUP -> ServerSetupScreen(viewModel { koin.get() }, onContinue = session::onServerSaved)
                    Phase.ONBOARDING -> OnboardingScreen(viewModel { koin.get() }, onCompleted = session::onOnboardingComplete, onBackToLogin = session::backToLogin)
                    Phase.LOGIN -> LoginScreen(viewModel { koin.get() }, onLoggedIn = session::onLoggedIn, onChangeServer = session::changeServer, onStartSetup = session::startSetup)
                    Phase.APP -> {
                        val nav: NavigationViewModel = viewModel { koin.get() }
                        val user = state.user!!
                        // Menus and the landing screen follow the permissions from /auth/me (FR-AUTH-11).
                        LaunchedEffect(user) { nav.start(user) }
                        AppShell(nav, onLogout = session::logout, onChangeServer = session::logoutAndChangeServer)
                    }
                }
                if (expired && state.phase == Phase.APP) {
                    SessionExpiredDialog(
                        username = session.username(),
                        state = state,
                        onPasswordChange = session::onReLoginPasswordChange,
                        onLogin = session::reLogin,
                        onLogout = session::logout,
                    )
                }
            }
        }
    }
}

private class RootOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
