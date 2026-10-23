package com.nexora.desktop.viewmodel.money

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.AdjustmentRequest
import com.nexora.desktop.api.dto.TransactionResponse
import com.nexora.desktop.api.dto.TransferRequest
import com.nexora.desktop.api.dto.TransferResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

data class TransferForm(val fromId: String? = null, val toId: String? = null, val amount: String = "", val date: String = "", val notes: String = "")

/**
 * S-FIN-05 Transfer between own accounts (FR-FIN-09, -10, -18); idempotent. The From account's balance is shown
 * as information only; the server checks it (INSUFFICIENT_FUNDS).
 */
class TransferViewModel(private val repository: FinanceRepository, private val clock: Clock) :
    FormViewModel<TransferForm, TransferResponse>(TransferForm(date = Dates.format(Dates.today(clock)))) {

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    init {
        viewModelScope.launch { _accounts.value = repository.activeAccounts().getOrNull().orEmpty() }
    }

    override fun validate(form: TransferForm): Map<String, String> = Checks.collect(
        "sourceAccountId" to if (form.fromId == null) s("validation.required") else null,
        "destinationAccountId" to when {
            form.toId == null -> s("validation.required")
            form.toId == form.fromId -> s("transfer.sameAccount")
            else -> null
        },
        "amount" to Checks.positive(form.amount),
        "transferDate" to Checks.date(form.date, clock),
    )

    override suspend fun send(form: TransferForm, idempotencyKey: String): ApiResult<TransferResponse> = repository.transfer(
        TransferRequest(form.fromId!!, form.toId!!, form.amount.decimal(), Dates.toIso(form.date, clock)!!, form.notes.trim().ifEmpty { null }),
        idempotencyKey,
    )
}

data class AdjustmentForm(val direction: String = "IN", val amount: String = "", val date: String = "", val reason: String = "")

/** S-FIN-06 Balance adjustment with a reason (FR-FIN-15); idempotent. */
class BalanceAdjustmentViewModel(private val repository: FinanceRepository, private val clock: Clock, private val accountId: String) :
    FormViewModel<AdjustmentForm, TransactionResponse>(AdjustmentForm(date = Dates.format(Dates.today(clock)))) {

    override fun validate(form: AdjustmentForm): Map<String, String> = Checks.collect(
        "amount" to Checks.positive(form.amount),
        "transactionDate" to Checks.date(form.date, clock),
        "reason" to Checks.required(form.reason),
    )

    override suspend fun send(form: AdjustmentForm, idempotencyKey: String): ApiResult<TransactionResponse> = repository.adjust(
        accountId,
        AdjustmentRequest(form.direction, form.amount.decimal(), Dates.toIso(form.date, clock)!!, form.reason.trim()),
        idempotencyKey,
    )
}
