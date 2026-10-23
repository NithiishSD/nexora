package com.nexora.desktop.viewmodel.money

import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.NewAccountRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import java.time.Clock

data class AccountForm(
    val name: String = "",
    val type: String = "BANK",
    val bankName: String = "",
    val last4: String = "",
    val openingBalance: String = "0.00",
    val openingDate: String = "",
)

/**
 * S-FIN-02 New account (FR-FIN-01, -02). Only the LAST FOUR digits of a bank account number are ever asked for or
 * sent: the full number is never typed into this app (NFR-SEC-06).
 */
class NewAccountViewModel(private val repository: FinanceRepository, private val clock: Clock) :
    FormViewModel<AccountForm, AccountResponse>(AccountForm(openingDate = Dates.format(Dates.today(clock)))) {

    /** Keeps digits only and at most four of them, whatever is typed or pasted. */
    fun setLast4(text: String) = edit { it.copy(last4 = text.filter(Char::isDigit).take(4)) }

    fun setType(type: String) = edit { it.copy(type = type, bankName = if (type == "CASH") "" else it.bankName, last4 = if (type == "CASH") "" else it.last4) }

    override fun validate(form: AccountForm): Map<String, String> {
        val bank = form.type == "BANK"
        val opening = form.openingBalance.toDecimalOrNull()
        return Checks.collect(
            "accountName" to Checks.required(form.name),
            "bankName" to if (bank) Checks.required(form.bankName) else null,
            "accountNumberLast4" to if (bank && form.last4.isNotEmpty() && !LAST4.matches(form.last4)) s("account.last4Invalid") else null,
            "openingBalance" to if (form.openingBalance.isBlank()) null else Checks.zeroOrMore(form.openingBalance),
            "openingDate" to if (opening != null && opening.signum() > 0) Checks.date(form.openingDate, clock) else null,
        )
    }

    override suspend fun send(form: AccountForm, idempotencyKey: String): ApiResult<AccountResponse> {
        val bank = form.type == "BANK"
        val opening = form.openingBalance.toDecimalOrNull()
        return repository.createAccount(
            NewAccountRequest(
                accountName = form.name.trim(),
                accountType = form.type,
                bankName = if (bank) form.bankName.trim() else null,
                accountNumberLast4 = if (bank) form.last4.ifEmpty { null } else null,
                openingBalance = opening,
                openingDate = if (opening != null && opening.signum() > 0) Dates.toIso(form.openingDate, clock) else null,
            ),
        )
    }

    companion object {
        val LAST4 = Regex("^[0-9]{4}$")
    }
}
