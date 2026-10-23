package com.nexora.desktop.util

import com.nexora.desktop.repository.AuthRepository
import com.nexora.desktop.support.InMemoryKeyring
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.testSession
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.tokenJson
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CredentialStoreTest {

    @Test
    @DisplayName("NFR-SEC-06 only refresh token is stored")
    fun onlyRefreshTokenIsStored() = runBlocking {
        val keyring = InMemoryKeyring()
        val settings = testSettings()
        val session = testSession(keyring, settings)
        val server = MockServer()
        server.onJson("POST /auth/login", tokenJson("ACCESS-TOKEN-123", "REFRESH-TOKEN-456"))
        server.onJson("GET /auth/me", OWNER_ME)

        AuthRepository(server.client(session), session, settings).login("owner", "Correct-Horse-9")

        // Exactly one secret in the OS store: the refresh token, under service SmartSilk and host/username.
        assertEquals(mapOf(("SmartSilk" to "shop.example.com/owner") to "REFRESH-TOKEN-456"), keyring.entries)
        // The settings file holds neither token nor the password.
        val written = Files.readString(settings.file)
        listOf("ACCESS-TOKEN-123", "REFRESH-TOKEN-456", "Correct-Horse-9").forEach { secret ->
            assertFalse(written.contains(secret), "settings.json contains $secret")
        }
        assertEquals("ACCESS-TOKEN-123", session.accessToken) // memory only
    }

    @Test
    @DisplayName("NFR-SEC-06 without a credential store nothing is written")
    fun noStore_nothingWritten() {
        val store = CredentialStore(null)
        store.saveRefreshToken("https://shop.example.com", "owner", "R1")
        assertFalse(store.available)
        assertEquals(null, store.refreshToken("https://shop.example.com", "owner"))
    }
}
