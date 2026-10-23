package com.nexora.desktop.ui.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.nexora.desktop.api.ApiJson
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.ui.theme.AppTheme
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DashboardUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("NFR-USE-03 key figures at least20sp")
    fun keyFigures_atLeast20sp() = runComposeUiTest {
        val server = MockServer()
        server.onJson("GET /dashboard/summary", Fixtures.DASHBOARD)
        val koin = testKoin(server)
        val nav = NavigationViewModel(testSettings())
        nav.start(ApiJson.decodeFromString(MeResponse.serializer(), OWNER_ME))
        setContent {
            WithKoin(koin) {
                AppTheme {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides nav.state.value.current!!) { DashboardScreen(nav, 0) }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("₹3,30,000.00")).fetchSemanticsNodes().isNotEmpty() }

        // The server's total is shown, formatted with Indian grouping.
        assertTrue(onAllNodes(hasText("55.500 kg")).fetchSemanticsNodes().isNotEmpty())
        val figures = onAllNodes(hasTestTag("figure"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(figures.size >= 20)
        figures.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val size = layouts.single().layoutInput.style.fontSize.value
            assertTrue(size >= 20f, "figure drawn at $size sp")
        }
        koin.close()
    }
}
