package com.nexora.desktop.ui.components

import com.nexora.desktop.api.ApiError
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.util.SubmitController
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SubmitControllerTest {

    private var counter = 0
    private val controller = SubmitController { "key-${++counter}" }
    private val sentKeys = mutableListOf<String>()

    private fun submit(result: ApiResult<String>) = runBlocking {
        controller.submit { key -> sentKeys += key; result }
    }

    @Test
    @DisplayName("NFR-USE-05 FR-PAY-07 retry after timeout reuses idempotency key")
    fun retryAfterTimeout_reusesIdempotencyKey() {
        submit(ApiResult.Failure(ApiError.network("Request timeout has expired")))
        submit(ApiResult.Failure(ApiError(503, "INTERNAL_ERROR")))
        submit(ApiResult.Success("PAY-2026-0001"))
        // Timeout, then 5xx, then success: all three requests carried the same key, so the server saves once.
        assertEquals(listOf("key-1", "key-1", "key-1"), sentKeys)

        // The next entry is a new payment and gets a new key.
        submit(ApiResult.Success("PAY-2026-0002"))
        assertEquals("key-2", sentKeys.last())
    }

    @Test
    @DisplayName("NFR-USE-05 key is discarded after a 4xx and after an edit")
    fun key_discardedAfter4xxAndEdit() {
        submit(ApiResult.Failure(ApiError(409, "INSUFFICIENT_FUNDS")))
        submit(ApiResult.Failure(ApiError.network(null)))
        controller.edited()
        submit(ApiResult.Success("ok"))
        assertEquals(listOf("key-1", "key-2", "key-3"), sentKeys)
        assertNotEquals(sentKeys[1], sentKeys[2])
    }
}
