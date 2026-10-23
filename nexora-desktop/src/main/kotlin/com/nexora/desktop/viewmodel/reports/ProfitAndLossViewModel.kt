package com.nexora.desktop.viewmodel.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.ProfitAndLossReportLine
import com.nexora.desktop.api.dto.ProfitabilityReportLine
import com.nexora.desktop.api.dto.WorkerEfficiencyRow
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ReportRepository
import com.nexora.desktop.viewmodel.common.Loaded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

class ProfitAndLossViewModel(
    private val repository: ReportRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _pnl = MutableStateFlow(Loaded<List<ProfitAndLossReportLine>>())
    val pnl: StateFlow<Loaded<List<ProfitAndLossReportLine>>> = _pnl.asStateFlow()

    private val _profitability = MutableStateFlow(Loaded<List<ProfitabilityReportLine>>())
    val profitability: StateFlow<Loaded<List<ProfitabilityReportLine>>> = _profitability.asStateFlow()

    private val _efficiency = MutableStateFlow(Loaded<List<WorkerEfficiencyRow>>())
    val efficiency: StateFlow<Loaded<List<WorkerEfficiencyRow>>> = _efficiency.asStateFlow()

    init {
        val today = LocalDate.now(clock)
        val from = today.withDayOfMonth(1).toString()
        val to = today.toString()
        load(from, to)
    }

    fun load(from: String, to: String, groupBy: String = "MONTH") {
        viewModelScope.launch {
            _pnl.update { it.copy(loading = true, error = null) }
            when (val res = repository.profitAndLoss(from, to, groupBy)) {
                is ApiResult.Success -> _pnl.update { it.copy(loading = false, value = res.value, lastUpdated = clock.instant()) }
                is ApiResult.Failure -> _pnl.update { it.copy(loading = false, error = res.error) }
            }
        }
    }

    fun loadProfitability(from: String, to: String, dimension: String = "PRODUCT") {
        viewModelScope.launch {
            _profitability.update { it.copy(loading = true, error = null) }
            when (val res = repository.profitability(from, to, dimension)) {
                is ApiResult.Success -> _profitability.update { it.copy(loading = false, value = res.value, lastUpdated = clock.instant()) }
                is ApiResult.Failure -> _profitability.update { it.copy(loading = false, error = res.error) }
            }
        }
    }

    fun loadEfficiency(from: String, to: String) {
        viewModelScope.launch {
            _efficiency.update { it.copy(loading = true, error = null) }
            when (val res = repository.workerEfficiency(from, to)) {
                is ApiResult.Success -> _efficiency.update { it.copy(loading = false, value = res.value, lastUpdated = clock.instant()) }
                is ApiResult.Failure -> _efficiency.update { it.copy(loading = false, error = res.error) }
            }
        }
    }
}
