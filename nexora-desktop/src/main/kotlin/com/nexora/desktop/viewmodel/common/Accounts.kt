package com.nexora.desktop.viewmodel.common

import com.nexora.desktop.api.dto.AccountResponse

/** Payment method and account rules shared by every money form (FR-PAY-12, 05 E-PAY-01). */
object AccountRules {
    val METHODS = listOf("CASH", "UPI", "BANK_TRANSFER", "CHEQUE")

    /** Cash is paid from / into a Cash account; UPI, bank transfer and cheque use a Bank account. */
    fun accountsFor(method: String?, accounts: List<AccountResponse>): List<AccountResponse> = when (method) {
        null -> accounts
        "CASH" -> accounts.filter { it.accountType == "CASH" }
        else -> accounts.filter { it.accountType == "BANK" }
    }

    /** Methods allowed for an account type (expenses choose the account first). */
    fun methodsFor(accountType: String?): List<String> = when (accountType) {
        "CASH" -> listOf("CASH")
        "BANK" -> listOf("UPI", "BANK_TRANSFER", "CHEQUE")
        else -> METHODS
    }

    /**
     * Default account: the last one used on this computer when it is still offered, otherwise the only one offered
     * (06 rule P3); null when the user has to choose.
     */
    fun defaultAccount(offered: List<AccountResponse>, lastUsedId: String?): String? =
        offered.firstOrNull { it.id == lastUsedId }?.id ?: offered.singleOrNull()?.id
}
