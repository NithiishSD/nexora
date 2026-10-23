package com.nexora.desktop.viewmodel.reports

import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ReportRepository
import com.nexora.desktop.util.Dates
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.viewmodel.common.Loaded
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import java.time.Clock

/** Which filters a report offers (06 S-RPT-02 table). */
enum class ReportFilter { PERIOD, SUPPLIER, CUSTOMER, MANUFACTURER, WORKER, ACCOUNT, PRODUCT, PRODUCT_REQUIRED, CATEGORY, KIND, MIN_OUTSTANDING }

/** One report of S-RPT-01: its endpoint and filters. `{customerId}` in the path is filled from the customer filter. */
data class ReportDef(val key: String, val path: String, val filters: Set<ReportFilter>, val weekly: Boolean = false)

val REPORTS = listOf(
    ReportDef("customer-outstanding", "/reports/customer-outstanding", setOf(ReportFilter.MIN_OUTSTANDING)),
    ReportDef("supplier-payables", "/reports/supplier-payables", setOf(ReportFilter.SUPPLIER)),
    ReportDef("purchases", "/reports/purchases", setOf(ReportFilter.PERIOD, ReportFilter.SUPPLIER, ReportFilter.PRODUCT)),
    ReportDef("sales", "/reports/sales", setOf(ReportFilter.PERIOD, ReportFilter.CUSTOMER, ReportFilter.PRODUCT)),
    ReportDef("expenses", "/reports/expenses", setOf(ReportFilter.PERIOD)),
    ReportDef("inventory-movements", "/reports/inventory-movements", setOf(ReportFilter.PERIOD, ReportFilter.PRODUCT_REQUIRED)),
    ReportDef("wastage", "/reports/wastage", setOf(ReportFilter.PERIOD, ReportFilter.PRODUCT)),
    ReportDef("production", "/reports/production", setOf(ReportFilter.PERIOD, ReportFilter.PRODUCT)),
    ReportDef("outsourcing", "/reports/outsourcing", setOf(ReportFilter.PERIOD, ReportFilter.MANUFACTURER)),
    ReportDef("cash-bank", "/reports/cash-bank", setOf(ReportFilter.PERIOD, ReportFilter.ACCOUNT)),
    ReportDef("worker-payable", "/reports/worker-payable", setOf(ReportFilter.PERIOD, ReportFilter.WORKER), weekly = true),
    ReportDef("loans-and-chits", "/reports/loans-and-chits", emptySet()),
    ReportDef("customer-statement", "/customers/{customerId}/statement", setOf(ReportFilter.PERIOD, ReportFilter.CUSTOMER)),
    ReportDef("worker-efficiency", "/reports/worker-efficiency", setOf(ReportFilter.PERIOD)),
    ReportDef("profit-and-loss", "/reports/profit-and-loss", setOf(ReportFilter.PERIOD)),
    ReportDef("profitability", "/reports/profitability", setOf(ReportFilter.PERIOD)),
)

/**
 * S-RPT-02 generic report viewer (FR-RPT-03..-16). The date range defaults to the current month (the current week for
 * the payroll report); Export CSV asks the same endpoint with exactly the filters on screen.
 */
class ReportViewerViewModel(
    private val repository: ReportRepository,
    private val clock: Clock,
    reportKey: String,
) : ViewModel() {

    private val _data = MutableStateFlow(Loaded<JsonElement>())

    /** The report exactly as the server returned it. */
    val data: StateFlow<Loaded<JsonElement>> = _data.asStateFlow()

    private var loadJob: Job? = null

    val report: ReportDef = REPORTS.first { it.key == reportKey }

    private val _filters = MutableStateFlow(
        if (ReportFilter.PERIOD in report.filters) {
            if (report.weekly) mapOf("from" to Dates.weekStart(clock).toString(), "to" to Dates.weekEnd(clock).toString())
            else mapOf("from" to Dates.monthStart(clock).toString(), "to" to Dates.today(clock).toString())
        } else {
            emptyMap()
        },
    )
    val filters: StateFlow<Map<String, String>> = _filters.asStateFlow()

    init {
        load()
    }

    fun setFilter(name: String, value: String?) {
        _filters.update { if (value.isNullOrEmpty()) it - name else it + (name to value) }
        load()
    }

    fun setDateRange(from: String?, to: String?) {
        _filters.update {
            val next = it.toMutableMap()
            if (from.isNullOrEmpty()) next.remove("from") else next["from"] = from
            if (to.isNullOrEmpty()) next.remove("to") else next["to"] = to
            next
        }
        load()
    }

    /** Ready when every required filter is set (product for inventory movements, customer for the statement). */
    fun ready(): Boolean = when (report.key) {
        "customer-statement" -> _filters.value["customerId"] != null
        "inventory-movements" -> _filters.value["productType"] != null
        else -> true
    }

    private fun path(): String = report.path.replace("{customerId}", _filters.value["customerId"].orEmpty())

    /** Query parameters: the customer of the statement is in the path, not the query. */
    private fun query(): Map<String, String> =
        if (report.key == "customer-statement") _filters.value - "customerId" else _filters.value

    fun load() {
        if (!ready()) return
        loadJob?.cancel() // a newer filter wins over a load still running
        _data.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            when (val r = repository.load(path(), query())) {
                is ApiResult.Success -> _data.value = Loaded(value = r.value, lastUpdated = clock.instant())
                is ApiResult.Failure -> _data.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    suspend fun csv(): ApiResult<ByteArray> = repository.csv(path(), query())

    /** File name of 06 section 3.7: `<report>-<from>-<to>.csv`. */
    fun csvName(): String = listOfNotNull(report.key, _filters.value["from"], _filters.value["to"]).joinToString("-") + ".csv"
}
