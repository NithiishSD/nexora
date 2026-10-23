package com.nexora.desktop.viewmodel.common

import com.nexora.desktop.api.ApiError

/**
 * A small money-entry dialog on a detail screen (loan repayment, interest, chit contribution, payout ...).
 * [kind] says which dialog is open; null = closed.
 */
data class MoneyEntry(
    val kind: String? = null,
    val amount: String = "",
    val accountId: String? = null,
    val method: String? = null,
    val date: String = "",
    val note: String = "",
    val submitting: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
)
