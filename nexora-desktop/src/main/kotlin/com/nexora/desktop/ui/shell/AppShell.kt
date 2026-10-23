package com.nexora.desktop.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.viewmodel.shell.OfflineStateViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.getKoin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.desktop.viewmodel.notifications.NotificationBellViewModel
import com.nexora.desktop.viewmodel.shell.Screen

/**
 * The main window after login: top bar, sidebar, the open screen and any form/dialog over it.
 * All global shortcuts are caught here before they reach a field (onPreviewKeyEvent).
 */
/** Session actions screens may need (Settings: change server). */
data class ShellActions(val logout: () -> Unit, val changeServer: () -> Unit)

val LocalShellActions = staticCompositionLocalOf { ShellActions({}, {}) }

@Composable
fun AppShell(nav: NavigationViewModel, onLogout: () -> Unit, onChangeServer: () -> Unit = {}) {
    val state by nav.state.collectAsState()
    val api: ApiClient = koinInject()
    val koin = getKoin()
    val status by api.status.collectAsState()
    val user = state.user ?: return
    val offlineVm: OfflineStateViewModel = viewModel { OfflineStateViewModel(api) }
    val offline by offlineVm.state.collectAsState()
    val settingsStore: com.nexora.desktop.util.SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()

    // The reminders bell: count of PENDING notifications, refreshed every 5 minutes (S-NTF-01).
    val bell: NotificationBellViewModel = viewModel { koin.get() }
    val pending by bell.count.collectAsState()
    val showBell = nav.can("REPORT_VIEW")
    LaunchedEffect(showBell) {
        while (showBell) {
            bell.refresh()
            delay(NotificationBellViewModel.REFRESH_MILLIS)
        }
    }

    LaunchedEffect(state.hint) {
        if (state.hint != null) {
            delay(3_000)
            nav.clearHint()
        }
    }

    // The shell itself can hold focus, so shortcuts work even before any field was clicked.
    val shellFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { shellFocus.requestFocus() }
    BoxWithConstraints(
        Modifier.fillMaxSize().focusRequester(shellFocus).focusable().onPreviewKeyEvent { handleGlobalShortcut(it, nav) },
    ) {
        // Below 1366 px the sidebar becomes the icon rail automatically (06 section 2).
        val collapsed = state.sidebarCollapsed || maxWidth < 1366.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(
                status = status,
                pendingReminders = if (showBell) pending?.toInt() ?: 0 else null,
                userName = user.displayName,
                onBell = { nav.push(Screen.Notifications) },
                onLogout = { nav.show(Overlay.ConfirmLogout) },
                darkMode = settings.darkMode,
                onToggleDarkMode = settingsStore::toggleDarkMode,
            )
            Row(Modifier.fillMaxSize()) {
                Sidebar(state.sections, state.section, collapsed, nav::open, nav::toggleSidebar)
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    // No offline writes: a clear "Cannot reach the server" with Retry (= F5), 06 section 3.4.
                    if (offline.showBanner) ErrorBanner(ApiError.network(null), onRetry = { offlineVm.retry(nav::reload) })
                    state.current?.let { entry ->
                        // Each entry owns its view-models; key() gives each entry its own remembered UI state.
                        key(entry) {
                            CompositionLocalProvider(
                                LocalViewModelStoreOwner provides entry,
                                LocalShellActions provides ShellActions(onLogout, onChangeServer),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    ScreenContent(entry.screen, nav, state.reloadTick, state.searchTick)
                                }
                            }
                        }
                    }
                }
            }
        }
        state.overlay?.let { overlay ->
            when (overlay) {
                Overlay.ConfirmLogout -> LogoutDialog(onConfirm = { nav.closeOverlay(); onLogout() }, onCancel = nav::closeOverlay)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    // Forms open as a 560 px panel on the right; the list stays visible on the left.
                    Surface(Modifier.width(560.dp).fillMaxHeight(), tonalElevation = 6.dp, shadowElevation = 8.dp) {
                        // Each opened form gets fresh view-models, ended when the form closes.
                        key(overlay) {
                            val owner = remember { OverlayOwner() }
                            DisposableEffect(Unit) { onDispose { owner.viewModelStore.clear() } }
                            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { OverlayContent(overlay, nav) }
                        }
                    }
                }
            }
        }
        state.hint?.let { hint ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(24.dp), tonalElevation = 8.dp, color = MaterialTheme.colorScheme.inverseSurface) {
                Text(s(hint), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

private class OverlayOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(s("shell.logoutTitle")) },
        text = { Text(s("shell.logoutText")) },
        // Cancel is the safe default; Enter does not log out (06 rule P9).
        confirmButton = { TextButton(onClick = onConfirm) { Text(s("shell.logout")) } },
        dismissButton = { Button(onClick = onCancel) { Text(s("common.cancel")) } },
    )
}
