package com.nexora.desktop.repository

import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.support.InMemoryKeyring
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.json
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.testSession
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.tokenJson
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AuthRepositoryTest {

    private val keyring = InMemoryKeyring()
    private val settings = testSettings()
    private val session = testSession(keyring, settings)

    /** Server where access token "A1" is expired and "A2" (from refresh) is valid. */
    private fun serverWithExpiredToken(): MockServer {
        val server = MockServer()
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        server.on("POST /auth/refresh") { delay(50); json(tokenJson("A2", "R2")) }
        server.on("GET /auth/me") { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer A2") json(OWNER_ME)
            else problem(401, "TOKEN_EXPIRED", "Access token expired.")
        }
        return server
    }

    @Test
    @DisplayName("FR-AUTH-04 expired access token refreshes once and retries")
    fun expiredAccessToken_refreshesOnceAndRetries() = runBlocking {
        val server = serverWithExpiredToken()
        val repository = AuthRepository(server.client(session), session, settings)

        val me = repository.login("owner", "Correct-Horse-9")

        assertEquals("owner", me.getOrNull()?.username)
        assertEquals(1, server.count("POST /auth/refresh"))
        assertEquals(2, server.count("GET /auth/me")) // the 401 and the one retry
        assertEquals("""{"refreshToken":"R1"}""", server.last("POST /auth/refresh").body)
        // The rotated refresh token replaces the old one in the credential store.
        assertEquals(listOf("R2"), keyring.entries.values.toList())
    }

    @Test
    @DisplayName("FR-AUTH-04 concurrent requests share one refresh")
    fun concurrentRequests_shareOneRefresh() = runBlocking {
        val server = serverWithExpiredToken()
        val api = server.client(session)
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        AuthRepository(api, session, settings).login("owner", "x") // refreshes once inside login
        // Make the current token expired again and fire five requests at the same moment.
        server.on("POST /auth/refresh") { delay(50); json(tokenJson("A3", "R3")) }
        server.on("GET /auth/me") { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer A3") json(OWNER_ME)
            else problem(401, "TOKEN_EXPIRED")
        }
        val refreshesBefore = server.count("POST /auth/refresh")

        val results = (1..5).map { async { api.get<MeResponse>("/auth/me") } }.awaitAll()

        assertTrue(results.all { it.getOrNull()?.username == "owner" })
        assertEquals(1, server.count("POST /auth/refresh") - refreshesBefore)
    }

    @Test
    @DisplayName("FR-AUTH-03 refresh failure clears stored token and asks for login")
    fun refreshFailure_clearsStoredTokenAndAsksForLogin() = runBlocking {
        val server = MockServer()
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        server.on("GET /auth/me") { problem(401, "TOKEN_EXPIRED") }
        // The refresh token was revoked (for example reused): 401 UNAUTHENTICATED.
        server.on("POST /auth/refresh") { problem(401, "UNAUTHENTICATED", "Refresh token is not valid.") }
        val repository = AuthRepository(server.client(session), session, settings)

        val me = repository.login("owner", "x")

        assertEquals("TOKEN_EXPIRED", me.errorOrNull()?.code)
        assertEquals(1, server.count("POST /auth/refresh"))
        assertTrue(keyring.entries.isEmpty())
        assertNull(session.accessToken)
        assertTrue(session.expired.value)
    }

    @Test
    @DisplayName("FR-AUTH-06 logout revokes the token and works offline")
    fun logout_deletesLocalTokensEvenOffline() = runBlocking {
        val server = MockServer()
        server.onJson("POST /auth/login", tokenJson("A1", "R1"))
        server.onJson("GET /auth/me", OWNER_ME)
        server.on("POST /auth/logout") { throw java.io.IOException("offline") }
        val repository = AuthRepository(server.client(session), session, settings)
        repository.login("owner", "x")

        repository.logout()

        assertEquals("""{"refreshToken":"R1"}""", server.last("POST /auth/logout").body)
        assertTrue(keyring.entries.isEmpty())
        assertNull(session.accessToken)
    }
}
