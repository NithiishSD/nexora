package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiError

/**
 * Every repository call returns either the decoded value or an [ApiError]; nothing throws to the screen.
 * Screens switch on the result instead of wrapping calls in try/catch.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): ApiError? = (this as? Failure)?.error
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
