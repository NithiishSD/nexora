package com.nexora.desktop.viewmodel.expenses

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.ExpenseRequest
import com.nexora.desktop.api.dto.ExpenseResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ExpenseRepository
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

data class ExpenseForm(
    val category: String? = null,
    val amount: String = "",
    val accountId: String? = null,
    val method: String? = null,
    val date: String = "",
    val description: String = "",
    val kind: String = "BUSINESS",
    val recurrence: String = "ONE_TIME",
)

/**
 * S-EXP-02 Record expense (FR-FIN-11, NFR-USE-01); idempotent. Default path = category, amount, account, date,
 * description, with category and account remembered and the date set to today.
 */
class RecordExpenseViewModel(
    private val expenses: ExpenseRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : FormViewModel<ExpenseForm, ExpenseResponse>(ExpenseForm()) {

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    init {
        preset { it.copy(category = settings.lastUsed("expense.category"), date = Dates.format(Dates.today(clock))) }
        viewModelScope.launch {
            _accounts.value = finance.activeAccounts().getOrNull().orEmpty()
            val accountId = AccountRules.defaultAccount(_accounts.value, settings.lastUsed("expense.account"))
            preset { it.copy(accountId = accountId, method = defaultMethod(accountId)) }
        }
        viewModelScope.launch {
            val list = expenses.categories().getOrNull()?.map { it.name }
            if (!list.isNullOrEmpty()) {
                _categories.value = list
            }
        }
    }

    /** Cash for a cash account; for a bank account the method last used on this computer. */
    private fun defaultMethod(accountId: String?): String? {
        val type = _accounts.value.firstOrNull { it.id == accountId }?.accountType ?: return null
        val allowed = AccountRules.methodsFor(type)
        return settings.lastUsed("expense.method").takeIf { it in allowed } ?: allowed.first()
    }

    fun methodsForAccount(accountId: String?): List<String> =
        AccountRules.methodsFor(_accounts.value.firstOrNull { it.id == accountId }?.accountType)

    fun setAccount(accountId: String) = edit { it.copy(accountId = accountId, method = defaultMethod(accountId)) }

    override fun validate(form: ExpenseForm): Map<String, String> = Checks.collect(
        "category" to if (form.category == null) s("validation.required") else null,
        "amount" to Checks.positive(form.amount),
        "financialAccountId" to if (form.accountId == null) s("validation.required") else null,
        "paymentMethod" to if (form.method == null) s("validation.required") else null,
        "expenseDate" to Checks.date(form.date, clock),
        "description" to Checks.required(form.description),
    )

    override suspend fun send(form: ExpenseForm, idempotencyKey: String): ApiResult<ExpenseResponse> = expenses.record(
        ExpenseRequest(
            category = form.category!!,
            expenseKind = form.kind,
            recurrence = form.recurrence,
            amount = form.amount.decimal(),
            expenseDate = Dates.toIso(form.date, clock)!!,
            financialAccountId = form.accountId!!,
            paymentMethod = form.method!!,
            description = form.description.trim(),
        ),
        idempotencyKey,
    )

    override fun afterSuccess(result: ExpenseResponse) {
        settings.remember("expense.category", result.category)
        settings.remember("expense.account", result.account.id)
        settings.remember("expense.method", result.paymentMethod)
    }
}
