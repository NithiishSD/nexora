package com.nexora.desktop.viewmodel.loans

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.LoanResponse
import com.nexora.desktop.api.dto.NewLoanRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.LoanRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock

/** S-LON-01 Loans list (FR-LOAN-05). */
class LoanListViewModel(repository: LoanRepository, clock: Clock) :
    PagedListViewModel<LoanResponse>(ListQuery(), clock, { repository.list(it) })

data class LoanForm(
    val lender: PickOption? = null,
    val loanType: String = "BANK",
    val principal: String = "",
    val startDate: String = "",
    val accountId: String? = null,
    val method: String? = null,
    val interestRate: String = "",
    val interestNotes: String = "",
)

/** S-LON-02 New loan (FR-LOAN-01); idempotent. The loan received is not revenue (the server books it as such). */
class NewLoanViewModel(
    private val loans: LoanRepository,
    private val finance: FinanceRepository,
    private val clock: Clock,
) : FormViewModel<LoanForm, LoanResponse>(LoanForm(startDate = Dates.format(Dates.today(clock)))) {

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    init {
        viewModelScope.launch { _accounts.value = finance.activeAccounts().getOrNull().orEmpty() }
    }

    fun offeredAccounts(method: String?) = AccountRules.accountsFor(method, _accounts.value)

    override fun validate(form: LoanForm): Map<String, String> = Checks.collect(
        "lenderId" to if (form.lender == null) s("validation.required") else null,
        "principalAmount" to Checks.positive(form.principal),
        "startDate" to Checks.date(form.startDate, clock),
        "financialAccountId" to if (form.accountId == null) s("validation.required") else null,
        "paymentMethod" to if (form.method == null) s("validation.required") else null,
        "interestRatePercent" to form.interestRate.takeIf { it.isNotBlank() }?.let {
            val rate = it.toDecimalOrNull()
            if (rate == null || rate < BigDecimal.ZERO || rate > BigDecimal(100)) s("validation.range", 0, 100) else null
        },
    )

    override suspend fun send(form: LoanForm, idempotencyKey: String): ApiResult<LoanResponse> = loans.create(
        NewLoanRequest(
            lenderId = form.lender!!.id,
            loanType = form.loanType,
            principalAmount = form.principal.decimal(),
            startDate = Dates.toIso(form.startDate, clock)!!,
            financialAccountId = form.accountId!!,
            paymentMethod = form.method!!,
            interestRatePercent = form.interestRate.toDecimalOrNull(),
            interestNotes = form.interestNotes.trim().ifEmpty { null },
        ),
        idempotencyKey,
    )
}
