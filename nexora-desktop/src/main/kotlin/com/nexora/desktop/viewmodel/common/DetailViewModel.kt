package com.nexora.desktop.viewmodel.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.util.SubmitController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** A record loaded from the API, with the last error and the time of the last good load. */
data class Loaded<T>(
    val loading: Boolean = false,
    val value: T? = null,
    val error: ApiError? = null,
    val lastUpdated: Instant? = null,
)

/** State of a status action (confirm, cancel, reverse, ...): which one is being confirmed, busy flag, last error. */
data class ActionState(
    /** The action whose confirmation dialog is open, by name (for example "cancel"). */
    val confirming: String? = null,
    val busy: Boolean = false,
    val error: ApiError? = null,
)

/**
 * Base for detail screens: loads one record, reloads it, and runs actions whose response is the updated record
 * (the API returns the changed resource), so the screen always shows the server's figures after an action.
 */
open class DetailViewModel<T>(
    private val clock: Clock,
    private val fetch: suspend () -> ApiResult<T>,
) : ViewModel() {

    private val _data = MutableStateFlow(Loaded<T>())
    val data: StateFlow<Loaded<T>> = _data.asStateFlow()

    private val _action = MutableStateFlow(ActionState())
    val action: StateFlow<ActionState> = _action.asStateFlow()

    /** One controller for the screen's retry-safe actions (idempotency key kept for retries). */
    protected val submit = SubmitController()

    init {
        reload()
    }

    fun reload() {
        _data.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = fetch()) {
                is ApiResult.Success -> _data.update { Loaded(value = result.value, lastUpdated = clock.instant()) }
                is ApiResult.Failure -> _data.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    /** Shows a newer copy of the record that another dialog already received from the server. */
    fun showUpdated(value: T) = _data.update { Loaded(value = value, lastUpdated = clock.instant()) }

    /** Opens the confirmation of an action (06 rule P9); nothing is sent yet. */
    fun ask(action: String) = _action.update { ActionState(confirming = action) }

    fun dismiss() = _action.update { ActionState() }

    /** A client check failed before anything was sent; shown like a server error. */
    protected fun showError(error: ApiError) = _action.update { it.copy(busy = false, error = error) }

    /**
     * Runs an action that returns the updated record. [call] gets the idempotency key; endpoints that do not
     * need one simply ignore it.
     */
    protected fun runAction(call: suspend (idempotencyKey: String) -> ApiResult<T>, onSuccess: (T) -> Unit = {}) {
        _action.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = submit.submit(call)) {
                is ApiResult.Success -> {
                    _data.update { Loaded(value = result.value, lastUpdated = clock.instant()) }
                    _action.update { ActionState() }
                    onSuccess(result.value)
                }
                is ApiResult.Failure -> _action.update { it.copy(busy = false, error = result.error) }
                null -> Unit // already running
            }
        }
    }

    /** Runs an action whose response is something else; the record is reloaded afterwards. */
    protected fun <R> runAndReload(call: suspend (idempotencyKey: String) -> ApiResult<R>, onSuccess: (R) -> Unit = {}) {
        _action.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = submit.submit(call)) {
                is ApiResult.Success -> {
                    _action.update { ActionState() }
                    onSuccess(result.value)
                    reload()
                }
                is ApiResult.Failure -> _action.update { it.copy(busy = false, error = result.error) }
                null -> Unit
            }
        }
    }
}
