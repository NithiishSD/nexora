package com.nexora.desktop.util

import com.github.javakeyring.Keyring as JavaKeyring
import com.github.javakeyring.PasswordAccessException

/** The operating system's secret store, reduced to the three calls we need. */
interface Keyring {
    fun read(service: String, account: String): String?
    fun write(service: String, account: String, secret: String)
    fun delete(service: String, account: String)
}

/**
 * Windows Credential Manager or the Linux Secret Service through java-keyring.
 * [open] returns null when the platform has no usable store (for example Linux without a Secret Service).
 */
class OsKeyring private constructor(private val keyring: JavaKeyring) : Keyring {
    override fun read(service: String, account: String): String? = try {
        keyring.getPassword(service, account)
    } catch (_: PasswordAccessException) {
        null // nothing stored yet
    }

    override fun write(service: String, account: String, secret: String) = keyring.setPassword(service, account, secret)

    override fun delete(service: String, account: String) {
        try {
            keyring.deletePassword(service, account)
        } catch (_: PasswordAccessException) {
            // Already gone: deleting is the goal anyway.
        }
    }

    companion object {
        fun open(): Keyring? = try {
            OsKeyring(JavaKeyring.create())
        } catch (_: Exception) {
            // BackendNotSupportedException (no store on this OS) or a store that cannot be reached
            // (for example no D-Bus session on Linux): both mean "no credential store".
            null
        } catch (_: LinkageError) {
            // Native library missing on this platform.
            null
        }
    }
}

/**
 * Keeps ONLY the refresh token, in the OS credential store (06 section 5.2, NFR-SEC-06). The access token never
 * leaves memory and passwords are never stored. One entry per server host + username, service name `SmartSilk`.
 */
class CredentialStore(private val keyring: Keyring?) {

    /** False when no OS store exists: the user then logs in at every start (06 section 3.2). */
    val available: Boolean get() = keyring != null

    fun saveRefreshToken(serverUrl: String, username: String, token: String) {
        keyring?.write(SERVICE, account(serverUrl, username), token)
    }

    fun refreshToken(serverUrl: String, username: String): String? = keyring?.read(SERVICE, account(serverUrl, username))

    fun deleteRefreshToken(serverUrl: String, username: String) {
        keyring?.delete(SERVICE, account(serverUrl, username))
    }

    private fun account(serverUrl: String, username: String): String {
        val host = runCatching { java.net.URI(serverUrl).host }.getOrNull() ?: serverUrl
        return "$host/${username.lowercase()}"
    }

    companion object {
        const val SERVICE = "SmartSilk"
    }
}
