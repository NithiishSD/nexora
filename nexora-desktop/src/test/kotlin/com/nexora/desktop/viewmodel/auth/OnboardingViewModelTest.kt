package com.nexora.desktop.viewmodel.auth

import com.nexora.desktop.repository.AuthRepository
import com.nexora.desktop.repository.SystemSettingRepository
import com.nexora.desktop.support.testSession
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OnboardingViewModelTest {

    @Test
    @DisplayName("validation fails when username is blank")
    fun validationFails_whenUsernameIsBlank() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val sysRepo = SystemSettingRepository(server.client(session))
        val authRepo = AuthRepository(server.client(session), session, settings)
        val vm = OnboardingViewModel(sysRepo, authRepo)

        vm.onUsernameChange("   ")
        vm.submit {}

        assertEquals("Username is required", vm.state.value.error)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    @DisplayName("validation fails when password is less than 8 chars")
    fun validationFails_whenPasswordShort() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val sysRepo = SystemSettingRepository(server.client(session))
        val authRepo = AuthRepository(server.client(session), session, settings)
        val vm = OnboardingViewModel(sysRepo, authRepo)

        vm.onUsernameChange("admin")
        vm.onPasswordChange("short")
        vm.onConfirmPasswordChange("short")
        vm.onBusinessNameChange("Kanchipuram Silk House")
        vm.submit {}

        assertEquals("Password must be at least 12 characters and contain both letters and numbers", vm.state.value.error)
    }

    @Test
    @DisplayName("validation fails when passwords do not match")
    fun validationFails_whenPasswordsDoNotMatch() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val sysRepo = SystemSettingRepository(server.client(session))
        val authRepo = AuthRepository(server.client(session), session, settings)
        val vm = OnboardingViewModel(sysRepo, authRepo)

        vm.onUsernameChange("admin")
        vm.onPasswordChange("validPassword123")
        vm.onConfirmPasswordChange("validPassword456")
        vm.onBusinessNameChange("Kanchipuram Silk House")
        vm.submit {}

        assertEquals("Passwords do not match", vm.state.value.error)
    }

    @Test
    @DisplayName("validation fails when business name is blank")
    fun validationFails_whenBusinessNameBlank() = vmTest { server ->
        val settings = testSettings()
        val session = testSession(settings = settings)
        val sysRepo = SystemSettingRepository(server.client(session))
        val authRepo = AuthRepository(server.client(session), session, settings)
        val vm = OnboardingViewModel(sysRepo, authRepo)

        vm.onUsernameChange("admin")
        vm.onPasswordChange("validPassword123")
        vm.onConfirmPasswordChange("validPassword123")
        vm.onBusinessNameChange("   ")
        vm.submit {}

        assertEquals("Business name is required", vm.state.value.error)
    }

    @Test
    @DisplayName("successful onboarding initializes setup, logs in and invokes onSuccess")
    fun successfulOnboarding_initializesAndLogsIn() = vmTest { server ->
        server.onJson("POST /setup/initialize", """{"status":"SUCCESS","message":"Setup completed successfully"}""")
        server.onJson(
            "POST /auth/login",
            """{"tokenType":"Bearer","accessToken":"access-token-1","refreshToken":"refresh-token-1","expiresIn":3600,"role":"OWNER"}"""
        )
        server.onJson("GET /auth/me", com.nexora.desktop.support.OWNER_ME)

        val settings = testSettings()
        val session = testSession(settings = settings)
        val sysRepo = SystemSettingRepository(server.client(session))
        val authRepo = AuthRepository(server.client(session), session, settings)
        val vm = OnboardingViewModel(sysRepo, authRepo)

        vm.onUsernameChange("admin")
        vm.onPasswordChange("securePass123")
        vm.onConfirmPasswordChange("securePass123")
        vm.onBusinessNameChange("Sri Lakshmi Silks")
        vm.onPhoneChange("9876543210")
        vm.onCityChange("Salem")

        var successCalled = false
        vm.submit { successCalled = true }
        testScheduler.advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(vm.state.value.submitting)
        assertNull(vm.state.value.error)
    }
}
