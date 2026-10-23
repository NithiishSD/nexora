package com.nexora.desktop.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.nexora.desktop.api.ApiJson
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.ui.theme.AppTheme
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ShellUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("NFR-USE-01 global shortcuts open record forms")
    fun globalShortcuts_openRecordForms() = runComposeUiTest {
        val koin = testKoin(MockServer())
        val nav = NavigationViewModel(testSettings())
        nav.start(ApiJson.decodeFromString(MeResponse.serializer(), OWNER_ME))
        setContent { WithKoin(koin) { AppTheme { AppShell(nav, onLogout = {}) } } }

        // Ctrl+Shift+<letter> opens the six frequent entry forms (06 section 1.1).
        val expected = mapOf(
            Key.P to "Record payment",
            Key.E to "Record expense",
            Key.W to "Record work",
            Key.O to "New order",
            Key.U to "New purchase",
            Key.R to "Receive silk",
        )
        expected.forEach { (letter, title) ->
            nav.closeOverlay()
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.ShiftLeft)
                pressKey(letter)
                keyUp(Key.ShiftLeft)
                keyUp(Key.CtrlLeft)
            }
            waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty() }
        }

        // Alt+3 jumps to the third entry (Purchases).
        nav.closeOverlay()
        onRoot().performKeyInput { keyDown(Key.AltLeft); pressKey(Key.Three); keyUp(Key.AltLeft) }
        waitForIdle()
        assertEquals("PURCHASES", nav.state.value.section?.name)
        koin.close()
    }
}
