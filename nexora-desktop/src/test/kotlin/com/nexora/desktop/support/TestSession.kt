package com.nexora.desktop.support

import com.nexora.desktop.api.AuthSession
import com.nexora.desktop.util.CredentialStore
import com.nexora.desktop.util.Keyring
import com.nexora.desktop.util.SettingsStore
import org.koin.dsl.module
import java.nio.file.Files

/** Keyring kept in a map: what the OS credential store would hold, visible to the test. */
class InMemoryKeyring : Keyring {
    val entries = mutableMapOf<Pair<String, String>, String>()
    override fun read(service: String, account: String): String? = entries[service to account]
    override fun write(service: String, account: String, secret: String) { entries[service to account] = secret }
    override fun delete(service: String, account: String) { entries.remove(service to account) }
}

/** A settings file in a fresh temporary folder, already pointing at the mock server. */
fun testSettings(): SettingsStore =
    SettingsStore(Files.createTempDirectory("smartsilk-test").resolve("settings.json")).apply {
        update { it.copy(serverUrl = "https://shop.example.com") }
    }

fun testSession(
    keyring: InMemoryKeyring = InMemoryKeyring(),
    settings: SettingsStore = testSettings(),
): AuthSession = AuthSession(CredentialStore(keyring), settings)

/** Replaces the parts of the app module that touch this computer (settings file, OS keyring). */
val testOverrides = module {
    single { testSettings() }
    single { CredentialStore(InMemoryKeyring()) }
}

/** Token pair as returned by POST /auth/login and /auth/refresh (05 E-AUTH-01). */
fun tokenJson(access: String, refresh: String) =
    """{"accessToken":"$access","refreshToken":"$refresh","tokenType":"Bearer","expiresIn":900}"""

const val OWNER_ME =
    """{"id":"7c1e","username":"owner","displayName":"Owner","roles":["OWNER"],"permissions":["USER_MANAGE","PARTY_VIEW","PARTY_MANAGE","PURCHASE_VIEW","PURCHASE_CREATE","INVENTORY_VIEW","INVENTORY_ADJUST","ORDER_VIEW","ORDER_MANAGE","PRODUCTION_VIEW","PRODUCTION_MANAGE","OUTSOURCING_MANAGE","WORK_RECORD_MANAGE","PAYMENT_VIEW","PAYMENT_CREATE","PAYMENT_REVERSE","FINANCE_VIEW","FINANCE_MANAGE","EXPENSE_CREATE","LOAN_MANAGE","CHIT_MANAGE","REPORT_VIEW","DOCUMENT_MANAGE"],"lastLoginAt":"2026-10-07T03:40:00Z"}"""

const val STAFF_ME =
    """{"id":"9a1","username":"ravi","displayName":"Ravi","roles":["STAFF"],"permissions":["PARTY_VIEW","INVENTORY_VIEW","ORDER_VIEW","PRODUCTION_VIEW"],"lastLoginAt":null}"""
