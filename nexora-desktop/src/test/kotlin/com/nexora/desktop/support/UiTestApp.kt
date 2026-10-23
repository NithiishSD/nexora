package com.nexora.desktop.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.nexora.desktop.api.AuthSession
import com.nexora.desktop.di.appModules
import com.nexora.desktop.ui.theme.AppTheme
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.time.Clock

/**
 * The real Koin graph with the computer-specific parts replaced (settings file, keyring) and the HTTP engine
 * pointed at [server]. UI tests render real screens and real view-models against the mock backend.
 */
fun testKoin(server: MockServer): KoinApplication = koinApplication {
    modules(
        appModules + testOverrides + module {
            single<Clock> { TEST_CLOCK }
            single { server.client(get<AuthSession>()) }
        },
    )
}

/** Koin, the app theme and a view-model store, as the shell provides them to a screen. */
@Composable
fun WithKoin(app: KoinApplication, content: @Composable () -> Unit) = KoinIsolatedContext(context = app) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AppTheme(content = content) }
}
