package com.nexora.desktop.viewmodel.parties

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.CustomerOutstandingResponse
import com.nexora.desktop.api.dto.LoanResponse
import com.nexora.desktop.api.dto.ManufacturerPayableResponse
import com.nexora.desktop.api.dto.PartyResponse
import com.nexora.desktop.api.dto.StatementResponse
import com.nexora.desktop.api.dto.SupplierPayablesResponse
import com.nexora.desktop.api.dto.WorkerPayableResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.LoanRepository
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.Loaded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/** Tabs of the party detail; which ones appear depends on the party's roles (06 S-PTY-03). */
enum class PartyTab(val role: String?) {
    OUTSTANDING("CUSTOMER"),
    STATEMENT("CUSTOMER"),
    PAYABLES("SUPPLIER"),
    MANUFACTURER_PAYABLE("MANUFACTURER"),
    WORKER_PAYABLE("WORKER"),
    LOANS("LENDER"),
    ACTIVITY(null),
}

/** Data of the money tabs, each loaded when its tab is opened. */
data class PartyTabsState(
    val selected: PartyTab? = null,
    val outstanding: Loaded<CustomerOutstandingResponse> = Loaded(),
    val statement: Loaded<StatementResponse> = Loaded(),
    val statementFrom: String = "",
    val statementTo: String = "",
    val payables: Loaded<SupplierPayablesResponse> = Loaded(),
    val manufacturer: Loaded<ManufacturerPayableResponse> = Loaded(),
    val worker: Loaded<WorkerPayableResponse> = Loaded(),
    val workerFrom: String = "",
    val workerTo: String = "",
    val loans: Loaded<List<LoanResponse>> = Loaded(),
)

/** S-PTY-03 Party detail (FR-PARTY-06, -10, FR-PAY-17..21, FR-RPT-04). */
class PartyDetailViewModel(
    private val repository: PartyRepository,
    private val loans: LoanRepository,
    private val clock: Clock,
    val partyId: String,
) : DetailViewModel<PartyResponse>(clock, { repository.get(partyId) }) {

    private val _tabs = MutableStateFlow(
        PartyTabsState(
            // Statement defaults to the current financial year (April-March); payroll to the current week.
            statementFrom = Dates.financialYearStart(clock).toString(),
            statementTo = Dates.today(clock).toString(),
            workerFrom = Dates.weekStart(clock).toString(),
            workerTo = Dates.weekEnd(clock).toString(),
        ),
    )
    val tabs: StateFlow<PartyTabsState> = _tabs.asStateFlow()

    /** Tabs for the roles this party holds, in the order of 06; Activity always. */
    fun visibleTabs(party: PartyResponse): List<PartyTab> = PartyTab.entries.filter { it.role == null || it.role in party.roles }

    fun select(tab: PartyTab) {
        _tabs.update { it.copy(selected = tab) }
        when (tab) {
            PartyTab.OUTSTANDING -> load({ repository.customerOutstanding(partyId) }) { s, v -> s.copy(outstanding = v) }
            PartyTab.STATEMENT -> loadStatement()
            PartyTab.PAYABLES -> load({ repository.supplierPayables(partyId) }) { s, v -> s.copy(payables = v) }
            PartyTab.MANUFACTURER_PAYABLE -> load({ repository.manufacturerPayable(partyId) }) { s, v -> s.copy(manufacturer = v) }
            PartyTab.WORKER_PAYABLE -> loadWorker()
            PartyTab.LOANS -> load({
                loans.list(ListQuery(size = 100, filters = mapOf("lenderId" to partyId))).let { r ->
                    if (r is ApiResult.Success) ApiResult.Success(r.value.content) else r as ApiResult.Failure
                }
            }) { s, v -> s.copy(loans = v) }
            PartyTab.ACTIVITY -> Unit
        }
    }

    fun setStatementRange(from: String, to: String) {
        _tabs.update { it.copy(statementFrom = from, statementTo = to) }
        loadStatement()
    }

    fun setWorkerRange(from: String, to: String) {
        _tabs.update { it.copy(workerFrom = from, workerTo = to) }
        loadWorker()
    }

    suspend fun statementCsv(): ApiResult<ByteArray> =
        repository.customerStatementCsv(partyId, _tabs.value.statementFrom, _tabs.value.statementTo)

    private fun loadStatement() {
        val t = _tabs.value
        load({ repository.customerStatement(partyId, t.statementFrom, t.statementTo) }) { s, v -> s.copy(statement = v) }
    }

    private fun loadWorker() {
        val t = _tabs.value
        load({ repository.workerPayable(partyId, t.workerFrom, t.workerTo) }) { s, v -> s.copy(worker = v) }
    }

    private fun <T> load(fetch: suspend () -> ApiResult<T>, put: (PartyTabsState, Loaded<T>) -> PartyTabsState) {
        viewModelScope.launch {
            _tabs.update { put(it, Loaded(loading = true)) }
            val loaded = when (val r = fetch()) {
                is ApiResult.Success -> Loaded(value = r.value, lastUpdated = clock.instant())
                is ApiResult.Failure -> Loaded(error = r.error)
            }
            _tabs.update { put(it, loaded) }
        }
    }

    fun deactivate() = runAction({ repository.deactivate(partyId) })

    fun activate() = runAction({ repository.activate(partyId) })
}
