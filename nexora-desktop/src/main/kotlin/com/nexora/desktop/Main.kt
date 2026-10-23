package com.nexora.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nexora.desktop.di.appModules
import com.nexora.desktop.ui.AppRoot
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.util.WindowSettings
import com.nexora.desktop.util.s
import org.koin.core.context.startKoin
import java.awt.Dimension

fun main() {
    // Dependency injection starts before the first window so every screen can resolve its view-model.
    val koin = startKoin { modules(appModules) }.koin
    val settings = koin.get<SettingsStore>()
    val saved = settings.current.window
    application {
        // Size, position and maximised state come back from the last run (06 section 2).
        val windowState = rememberWindowState(
            placement = if (saved.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (saved.x != null && saved.y != null) WindowPosition(saved.x.dp, saved.y.dp) else WindowPosition.PlatformDefault,
            size = DpSize(saved.width.dp, saved.height.dp),
        )
        Window(
            onCloseRequest = {
                settings.update {
                    it.copy(
                        window = WindowSettings(
                            x = windowState.position.x.value.toInt(),
                            y = windowState.position.y.value.toInt(),
                            width = windowState.size.width.value.toInt(),
                            height = windowState.size.height.value.toInt(),
                            maximized = windowState.placement == WindowPlacement.Maximized,
                        ),
                    )
                }
                exitApplication()
            },
            state = windowState,
            title = s("app.title"),
        ) {
            // The window can never be made smaller than the 1280 x 720 layout minimum (06 section 2).
            window.minimumSize = Dimension(1280, 720)
            AppRoot()
        }
    }
}
