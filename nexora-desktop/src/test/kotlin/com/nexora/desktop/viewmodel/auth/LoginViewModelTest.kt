package com.nexora.desktop.viewmodel.auth

import com.nexora.desktop.repository.AuthRepository
import com.nexora.desktop.support.InMemoryKeyring
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.testSession
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.tokenJson
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.util.CredentialStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoginViewModelTest {

    @Test
    @DisplayName("FR-AUTH-01 FR-AUTH-07 invalid credentials shows generic message")
    fun invalidCredentials_showsGenericMessage() = vmTest { server ->
        // The server says the account is locked, but the detail must never reach the screen (FR-AUTH-07).
        server.on("POST /auth/login") { problem(401, "INVALID_CREDENTIALS", "Account locked until 10:30.") }
        val settings = testSettings()
        val session = testSession(settings = settings)
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(InMemoryKeyring()))
        var loggedIn = false

        vm.onUsernameChange("owner")
        vm.onPasswordChange("wrong")
        vm.login { loggedIn = true }
        testScheduler.advanceUntilIdle()

        assertFalse(loggedIn)
        assertEquals("Wrong username or password.", vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("locked"))
        assertFalse(vm.state.value.submitting)
        // The typed password stays so the user can correct it.
        assertEquals("wrong", vm.state.value.password)
    }

    @Test
    @DisplayName("FR-AUTH-01 valid login remembers the username and clears the password")
    fun validLogin_remembersUsername() = vmTest { server ->
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        server.onJson("GET /auth/me", OWNER_ME)
        val settings = testSettings()
        val session = testSession(settings = settings)
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(null))
        var user: String? = null

        vm.onUsernameChange(" owner ")
        vm.onPasswordChange("Correct-Horse-9")
        vm.login { user = it.displayName }
        testScheduler.advanceUntilIdle()

        assertEquals("Owner", user)
        assertEquals("owner", settings.current.lastUsername)
        assertEquals("", vm.state.value.password)
        // No OS credential store: the login screen shows the one-line notice.
        assertTrue(vm.state.value.credentialStoreMissing)
    }

    @Test
    @DisplayName("FR-AUTH-01 empty fields are rejected without a request")
    fun emptyFields_rejectedLocally() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(InMemoryKeyring()))
        vm.login { }
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.state.value.usernameError)
        assertEquals("Required", vm.state.value.passwordError)
        assertTrue(server.requests.isEmpty())
        assertNull(vm.state.value.error)
    }

    @Test
    @DisplayName("forgot password dialog opens and closes")
    fun forgotPassword_togglesDialog() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(InMemoryKeyring()))
        assertFalse(vm.state.value.showForgotPasswordDialog)
        vm.onForgotPasswordClick()
        assertTrue(vm.state.value.showForgotPasswordDialog)
        vm.dismissForgotPasswordDialog()
        assertFalse(vm.state.value.showForgotPasswordDialog)
    }

    @Test
    @DisplayName("create account navigates to setup when system is not initialized")
    fun createAccount_navigatesToSetupWhenNotInitialized() = vmTest { server ->
        server.onJson("GET /setup/status", """{"initialized":false,"businessName":"SmartSilk"}""")
        val settings = testSettings()
        val session = testSession(settings = settings)
        val repo = com.nexora.desktop.repository.SystemSettingRepository(server.client(session))
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(InMemoryKeyring()), repo)

        var navigated = false
        vm.onCreateAccountClick { navigated = true }
        testScheduler.advanceUntilIdle()

        assertTrue(navigated)
        assertFalse(vm.state.value.showAlreadyRegisteredDialog)
    }

    @Test
    @DisplayName("create account shows dialog when system is already initialized")
    fun createAccount_showsAlreadyRegisteredDialogWhenInitialized() = vmTest { server ->
        server.onJson("GET /setup/status", """{"initialized":true,"businessName":"Sri Balaji Silks"}""")
        val settings = testSettings()
        val session = testSession(settings = settings)
        val repo = com.nexora.desktop.repository.SystemSettingRepository(server.client(session))
        val vm = LoginViewModel(AuthRepository(server.client(session), session, settings), settings, CredentialStore(InMemoryKeyring()), repo)

        var navigated = false
        vm.onCreateAccountClick { navigated = true }
        testScheduler.advanceUntilIdle()

        assertFalse(navigated)
        assertTrue(vm.state.value.showAlreadyRegisteredDialog)
        vm.dismissAlreadyRegisteredDialog()
        assertFalse(vm.state.value.showAlreadyRegisteredDialog)
    }
}
