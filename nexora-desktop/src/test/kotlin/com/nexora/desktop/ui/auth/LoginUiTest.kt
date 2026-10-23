package com.nexora.desktop.ui.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.input.key.Key
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import com.nexora.desktop.support.tokenJson
import com.nexora.desktop.ui.AppRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoginUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("FR-AUTH-01 FR-AUTH-11 login with valid credentials opens landing screen")
    fun login_withValidCredentials_opensLandingScreen() = runComposeUiTest {
        val server = MockServer()
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        server.onJson("GET /auth/me", OWNER_ME)
        val koin = testKoin(server)
        setContent { WithKoin(koin) { AppRoot() } }

        // No stored refresh token -> the login screen; keyboard only: type, Enter.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Log in to SmartSilk")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("login.username").performTextInput("owner")
        onNodeWithTag("login.password").performTextInput("Correct-Horse-9")
        onNodeWithTag("login.password").performKeyInput { pressKey(Key.Enter) }

        // OWNER has REPORT_VIEW, so the landing screen is the dashboard (06 section 2).
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Dashboard")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("""{"username":"owner","password":"Correct-Horse-9"}""", server.last("POST /auth/login").body)
        koin.close()
    }
}
