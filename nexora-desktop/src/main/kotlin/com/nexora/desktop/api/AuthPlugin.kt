package com.nexora.desktop.api

import com.nexora.desktop.api.dto.RefreshRequest
import com.nexora.desktop.api.dto.TokenResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.util.CredentialStore
import com.nexora.desktop.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The logged-in session and the "refresh once, retry once" rule of 06 section 3.2 (FR-AUTH-03, FR-AUTH-04).
 *
 * - The access token (15 minutes) lives only in this object's memory.
 * - The refresh token (7 days, rotated on every use) is kept in memory and, when the OS has a credential store,
 *   written there so the next start can skip the password.
 * - When several requests get 401 at the same moment, the [mutex] lets only the first one call /auth/refresh;
 *   the others wait, see that the token already changed, and simply retry with the new one.
 */
class AuthSession(private val credentials: CredentialStore, private val settings: SettingsStore) {

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    private var refreshToken: String? = null

    @Volatile
    var username: String? = null
        private set

    private val mutex = Mutex()

    private val _expired = MutableStateFlow(false)

    /** True after a refresh failed: the app shows the session-expired dialog (S-AUTH-03) and keeps the screens' input. */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    /** Stores a fresh token pair after login or refresh. */
    fun signIn(tokens: TokenResponse, username: String) {
        this.username = username
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        // The server rotates the refresh token on every use, so the stored copy is replaced each time.
        settings.current.serverUrl?.let { credentials.saveRefreshToken(it, username, tokens.refreshToken) }
        _expired.value = false
    }

    /** Loads the refresh token saved at the last run, so start-up can call /auth/refresh without a password. */
    fun restore(username: String): Boolean {
        val stored = settings.current.serverUrl?.let { credentials.refreshToken(it, username) } ?: return false
        this.username = username
        refreshToken = stored
        return true
    }

    /** The refresh token, for logout only. */
    fun currentRefreshToken(): String? = refreshToken

    /** Forgets every token, in memory and in the credential store (logout, or a refresh the server refused). */
    fun clear() {
        val user = username
        val server = settings.current.serverUrl
        if (user != null && server != null) credentials.deleteRefreshToken(server, user)
        accessToken = null
        refreshToken = null
    }

    /** Closes the session-expired dialog after the user chose to log out instead. */
    fun endSession() {
        clear()
        _expired.value = false
    }

    /**
     * Called by [ApiClient] after a 401 for a request sent with [failedToken].
     * @return true when a newer access token is available and the request should be repeated once.
     */
    suspend fun refreshAfter(failedToken: String?, api: ApiClient): Boolean = mutex.withLock {
        // Another request refreshed while we waited for the lock: reuse its result instead of refreshing again.
        val current = accessToken
        if (current != null && current != failedToken) return@withLock true
        val token = refreshToken ?: return@withLock expire()
        val result: ApiResult<TokenResponse> = api.post("/auth/refresh", RefreshRequest(token), authenticated = false)
        when (result) {
            is ApiResult.Success -> {
                signIn(result.value, username.orEmpty())
                true
            }
            // Offline: keep the tokens; the caller shows "Cannot reach the server" and a later retry may work.
            is ApiResult.Failure -> if (result.error.isNetwork) false else expire()
        }
    }

    private fun expire(): Boolean {
        // The stored token is useless now (expired, revoked or reused); delete it so the next start asks for login.
        clear()
        _expired.value = true
        return false
    }
}
