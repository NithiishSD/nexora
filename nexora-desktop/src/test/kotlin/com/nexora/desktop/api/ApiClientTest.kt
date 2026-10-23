package com.nexora.desktop.api

import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.json
import com.nexora.desktop.support.problem
import io.ktor.client.plugins.HttpTimeoutCapability
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ApiClientTest {

    @Serializable
    private data class NewParty(val name: String, val roles: List<String>)

    @Serializable
    private data class Party(val id: String, val partyCode: String, val name: String)

    @Test
    @DisplayName("NFR-USE-04 problem detail is mapped to api error with field errors")
    fun problemDetail_isMappedToApiErrorWithFieldErrors() = runBlocking {
        val server = MockServer()
        // The validation example of 05 section 1.5, with two messages for one field.
        server.on("POST /parties") {
            problem(400, "VALIDATION_FAILED", "Request validation failed.",
                """[{"field":"roles","message":"must not be empty"},{"field":"name","message":"must not be blank"},{"field":"name","message":"size must be at most 200"}]""")
        }

        val result: ApiResult<Party> = server.client().post("/parties", NewParty("", emptyList()))

        val error = (result as ApiResult.Failure).error
        assertEquals(400, error.status)
        assertEquals("VALIDATION_FAILED", error.code)
        assertEquals("Request validation failed.", error.detail)
        assertEquals("must not be empty", error.fieldErrors["roles"])
        assertEquals("must not be blank; size must be at most 200", error.fieldErrors["name"])
    }

    @Test
    @DisplayName("NFR-USE-04 success body is decoded and conflict codes are kept")
    fun successAndConflict_areDecoded() = runBlocking {
        val server = MockServer()
        server.on("GET /parties/5d0c") { json("""{"id":"5d0c","partyCode":"PTY-2026-0001","name":"Kumar Silks","extra":"ignored"}""") }
        server.on("POST /production-batches/1b6a/start") { problem(409, "INSUFFICIENT_INVENTORY", "Only 42.500 kg of RAW_SILK available in RAW_STOCK.") }
        val api = server.client()

        val party: ApiResult<Party> = api.get("/parties/5d0c")
        val start: ApiResult<Unit> = api.postEmpty("/production-batches/1b6a/start")

        assertEquals(Party("5d0c", "PTY-2026-0001", "Kumar Silks"), party.getOrNull())
        assertEquals("INSUFFICIENT_INVENTORY", start.errorOrNull()?.code)
        assertEquals(false, start.errorOrNull()?.isRetryable)
        assertEquals("application/json", server.last("GET /parties/5d0c").headers["Accept"])
    }

    @Test
    @DisplayName("NFR-USE-05 request timeout is30 seconds")
    fun requestTimeout_is30Seconds() = runBlocking {
        val server = MockServer()
        var configured: Long? = null
        server.on("GET /auth/me") { request ->
            configured = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            json("{}")
        }
        server.client().get<Unit>("/auth/me")
        assertEquals(30_000L, configured)

        // And a server that does not answer in time becomes the retryable NETWORK error, not a crash.
        val slow = MockServer()
        slow.on("GET /auth/me") { delay(2_000); json("{}") }
        val result = slow.client(timeoutMillis = 100).get<Unit>("/auth/me")
        val error = result.errorOrNull()!!
        assertEquals(ApiError.NETWORK, error.code)
        assertTrue(error.isRetryable)
    }
}
