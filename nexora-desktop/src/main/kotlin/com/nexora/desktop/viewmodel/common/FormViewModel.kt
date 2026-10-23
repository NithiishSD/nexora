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

/**
 * Everything around a form except its fields: submitting flag, the banner error, errors per field and the
 * server's answer after a successful save.
 *
 * Field errors are keyed by the API's field name (`receivedWeightKg`, `amount`, ...), so a client check and a
 * `VALIDATION_FAILED errors[]` entry for the same field land under the same input (NFR-USE-04).
 */
data class FormStatus<R>(
    val submitting: Boolean = false,
    val dirty: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val result: R? = null,
)

/**
 * Base for create/action forms. Subclasses give the client-side checks ([validate], mirroring the API so mistakes
 * show before submitting) and the request ([send]). The API stays the authority: its errors are shown as they come.
 */
abstract class FormViewModel<F, R>(initial: F) : ViewModel() {

    private val _form = MutableStateFlow(initial)
    val form: StateFlow<F> = _form.asStateFlow()

    private val _status = MutableStateFlow(FormStatus<R>())
    val status: StateFlow<FormStatus<R>> = _status.asStateFlow()

    /** Keeps the Idempotency-Key across retries of the same input (06 section 3.3). */
    protected val submitter = SubmitController()

    /** Client checks; returns field name -> message for every problem found. */
    protected abstract fun validate(form: F): Map<String, String>

    /** Sends the request; [idempotencyKey] is passed to endpoints that require one. */
    protected abstract suspend fun send(form: F, idempotencyKey: String): ApiResult<R>

    /** Hook after a successful save (for example "record another" keeps some fields). */
    protected open fun afterSuccess(result: R) {}

    /** Every edit goes through here: a changed body must not reuse the previous key. */
    fun edit(change: (F) -> F) {
        _form.update(change)
        submitter.edited()
        _status.update { it.copy(dirty = true, fieldErrors = emptyMap()) }
    }

    /** Puts a value in a field without marking the form as edited (defaults loaded from the API). */
    protected fun preset(change: (F) -> F) = _form.update(change)

    fun save() {
        if (_status.value.submitting) return
        val problems = validate(_form.value)
        if (problems.isNotEmpty()) {
            _status.update { it.copy(fieldErrors = problems, error = null) }
            return
        }
        _status.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = submitter.submit { key -> send(_form.value, key) }) {
                is ApiResult.Success -> {
                    _status.update { FormStatus(result = result.value) }
                    afterSuccess(result.value)
                }
                // The input stays exactly as typed after a failure (06 section 3.4).
                is ApiResult.Failure -> _status.update {
                    it.copy(submitting = false, error = result.error, fieldErrors = result.error.fieldErrors)
                }
                null -> Unit
            }
        }
    }

    /** Clears the result view ("Record another"). */
    fun clearResult() = _status.update { FormStatus() }
}
