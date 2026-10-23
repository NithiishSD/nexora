package com.nexora.desktop.viewmodel.chits

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.CashEntryRequest
import com.nexora.desktop.api.dto.ChitResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ChitRepository
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.MoneyEntry
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * S-CHT-03 Chit detail (FR-CHIT-02..-06, -09). Chit money is physical cash only: every account selector lists
 * CASH accounts only and the method is always CASH (DOMAIN_RULES; the server answers INVALID_PAYMENT_METHOD otherwise).
 */
class ChitDetailViewModel(
    private val chits: ChitRepository,
    private val finance: FinanceRepository,
    private val clock: Clock,
    val chitId: String,
) : DetailViewModel<ChitResponse>(clock, { chits.get(chitId) }) {

    private val _entry = MutableStateFlow(MoneyEntry())
    val entry: StateFlow<MoneyEntry> = _entry.asStateFlow()

    private val _cashAccounts = MutableStateFlow<List<AccountResponse>>(emptyList())

    /** The only accounts offered for chit entries. */
    val cashAccounts: StateFlow<List<AccountResponse>> = _cashAccounts.asStateFlow()

    private val _bidResult = MutableStateFlow<com.nexora.desktop.api.dto.ChitBidResponse?>(null)
    val bidResult: StateFlow<com.nexora.desktop.api.dto.ChitBidResponse?> = _bidResult.asStateFlow()

    init {
        viewModelScope.launch {
            _cashAccounts.value = finance.activeAccounts().getOrNull().orEmpty().filter { it.accountType == "CASH" }
        }
    }

    fun calculateBid(month: String, bidAmount: String) {
        viewModelScope.launch {
            val amount = bidAmount.decimal()
            val result = chits.calculateBid(chitId, month, amount)
            _bidResult.value = result.getOrNull()
        }
    }

    fun clearBidResult() {
        _bidResult.value = null
    }

    /**
     * [kind] = `contributions` (amount defaults to the standard monthly amount), `payout` or `organiser-payments`.
     */
    fun openEntry(kind: String) {
        val chit = data.value.value ?: return
        val defaultAccount = _cashAccounts.value.firstOrNull()?.id
        _entry.update {
            MoneyEntry(
                kind = kind,
                amount = if (kind == "contributions") chit.monthlyAmount.toPlainString() else "",
                accountId = defaultAccount,
                method = "CASH",
                date = Dates.format(Dates.today(clock)),
            )
        }
        if (_cashAccounts.value.isEmpty()) {
            viewModelScope.launch {
                val accounts = finance.activeAccounts().getOrNull().orEmpty().filter { it.accountType == "CASH" }
                _cashAccounts.value = accounts
                if (_entry.value.accountId == null && accounts.isNotEmpty()) {
                    _entry.update { it.copy(accountId = accounts.first().id) }
                }
            }
        }
    }

    fun closeEntry() = _entry.update { MoneyEntry() }

    fun editEntry(change: (MoneyEntry) -> MoneyEntry) {
        submit.edited()
        _entry.update { change(it).copy(fieldErrors = emptyMap(), method = "CASH") }
    }

    fun submitEntry() {
        val e = _entry.value
        val kind = e.kind ?: return
        val errors = Checks.collect(
            "amount" to Checks.positive(e.amount),
            "financialAccountId" to when {
                e.accountId == null -> s("validation.required")
                _cashAccounts.value.none { it.id == e.accountId } -> s("chit.cashOnly")
                else -> null
            },
            "entryDate" to Checks.date(e.date, clock),
        )
        if (errors.isNotEmpty()) {
            _entry.update { it.copy(fieldErrors = errors) }
            return
        }
        _entry.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val request = CashEntryRequest(e.amount.decimal(), e.accountId!!, "CASH", Dates.toIso(e.date, clock)!!, e.note.trim().ifEmpty { null })
            when (val result = submit.submit { key -> chits.entry(chitId, kind, request, key) }) {
                is ApiResult.Success -> { showUpdated(result.value); _entry.value = MoneyEntry() }
                is ApiResult.Failure -> _entry.update { it.copy(submitting = false, error = result.error, fieldErrors = result.error.fieldErrors) }
                null -> Unit
            }
        }
    }

    fun reverseEntry(transactionId: String, reason: String) = runAction({ key -> chits.reverseEntry(chitId, transactionId, reason, key) })

    fun close() = runAction({ chits.close(chitId) })
}
