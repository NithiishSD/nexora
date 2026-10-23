package com.nexora.desktop.util

import com.nexora.desktop.repository.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** A fresh Idempotency-Key: a random UUID v4 (36 characters, within the API's 8-64 rule). */
fun newIdempotencyKey(): String = UUID.randomUUID().toString()

/**
 * Runs one form submit with the retry rules of 06 section 3.3 (NFR-USE-05):
 * - pressing Save creates an Idempotency-Key;
 * - when the request fails without a final answer (no connection, timeout, 5xx) the key is KEPT, so pressing
 *   Save again sends the same key and the server replays instead of saving a second payment;
 * - after a 2xx or a 4xx the key is discarded, and editing the form discards it too (the body changed);
 * - while a request is running a second press does nothing.
 */
class SubmitController(private val newKey: () -> String = ::newIdempotencyKey) {
    private var key: String? = null

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** The key the next submit will send (null when a new one will be made). */
    val pendingKey: String? get() = key

    /** Call on every edit of the form. */
    fun edited() {
        if (!_submitting.value) key = null
    }

    /** @return the result, or null when a submit was already running. */
    suspend fun <T> submit(block: suspend (idempotencyKey: String) -> ApiResult<T>): ApiResult<T>? {
        if (_submitting.value) return null
        val current = key ?: newKey().also { key = it }
        _submitting.value = true
        try {
            val result = block(current)
            val keepForRetry = result is ApiResult.Failure && result.error.isRetryable
            if (!keepForRetry) key = null
            return result
        } finally {
            _submitting.value = false
        }
    }
}
