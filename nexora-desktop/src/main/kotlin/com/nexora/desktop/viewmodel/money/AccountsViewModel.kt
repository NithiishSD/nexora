package com.nexora.desktop.viewmodel.money

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.AccountsResponse
import com.nexora.desktop.api.dto.CashPositionResponse
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.viewmodel.common.DetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** S-FIN-01 Accounts and cash position (FR-FIN-03, -08, -16). */
class AccountsViewModel(private val repository: FinanceRepository, clock: Clock) :
    DetailViewModel<AccountsResponse>(clock, { repository.accounts() }) {

    private val _position = MutableStateFlow<CashPositionResponse?>(null)

    /** Physical cash, each bank and total available, as `GET /finance/cash-position` returns them. */
    val position: StateFlow<CashPositionResponse?> = _position.asStateFlow()

    init {
        loadPosition()
    }

    private fun loadPosition() {
        viewModelScope.launch { _position.value = repository.cashPosition().getOrNull() }
    }

    fun refresh() {
        reload()
        loadPosition()
    }

    /** Close is offered only for an active account whose balance (from the API) is exactly 0.00 (FR-FIN-16). */
    fun canClose(account: AccountResponse): Boolean = account.status == "ACTIVE" && account.balance.signum() == 0

    fun close(accountId: String) = runAndReload({ repository.closeAccount(accountId) }, onSuccess = { loadPosition() })
}
