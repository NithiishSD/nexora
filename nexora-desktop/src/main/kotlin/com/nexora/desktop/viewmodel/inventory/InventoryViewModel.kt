package com.nexora.desktop.viewmodel.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.Decimal
import com.nexora.desktop.api.dto.InventoryThresholdDto
import com.nexora.desktop.api.dto.LowStockItemDto
import com.nexora.desktop.api.dto.SetThresholdRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock

data class ThresholdItemState(
    val productType: String,
    val location: String,
    val currentQuantityKg: Decimal = BigDecimal.ZERO,
    val thresholdInput: String = "",
    val configuredThresholdKg: Decimal? = null,
    val isLowStock: Boolean = false,
    val deficitKg: Decimal = BigDecimal.ZERO,
    val error: String? = null,
)

data class InventoryThresholdsState(
    val loading: Boolean = false,
    val items: List<ThresholdItemState> = emptyList(),
    val lowStockItems: List<LowStockItemDto> = emptyList(),
    val error: ApiError? = null,
    val saving: Boolean = false,
)

/**
 * Manages inventory thresholds and low-stock warnings (FR-INV-18, FR-PROC-20, FR-NTF-07).
 */
class InventoryViewModel(
    private val repository: InventoryRepository,
    @Suppress("unused") private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryThresholdsState(loading = true))
    val state: StateFlow<InventoryThresholdsState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val thresholdsRes = repository.getThresholds()
            val summaryRes = repository.summary()
            val lowStockRes = repository.getLowStock()

            if (thresholdsRes is ApiResult.Failure) {
                _state.update { it.copy(loading = false, error = thresholdsRes.error) }
                return@launch
            }
            if (summaryRes is ApiResult.Failure) {
                _state.update { it.copy(loading = false, error = summaryRes.error) }
                return@launch
            }

            val thresholds = (thresholdsRes as ApiResult.Success).value
            val summary = (summaryRes as ApiResult.Success).value
            val lowStockItems = if (lowStockRes is ApiResult.Success) lowStockRes.value else emptyList()

            val items = LEGAL_PAIRS.map { (prod, loc) ->
                val thresh = thresholds.firstOrNull { it.productType == prod && it.location == loc }
                val balance = summary.balances.firstOrNull { it.productType == prod && it.location == loc }
                val lowStock = lowStockItems.firstOrNull { it.productType == prod && it.location == loc }

                val currentQty = balance?.quantityKg ?: BigDecimal.ZERO
                val thresholdKg = thresh?.minThresholdKg
                val isLow = (thresholdKg != null && thresholdKg > BigDecimal.ZERO && currentQty <= thresholdKg) ||
                    (balance?.isLowStock == true) || (lowStock != null)
                val deficit = lowStock?.deficitKg
                    ?: if (isLow && thresholdKg != null && thresholdKg > currentQty) thresholdKg - currentQty else BigDecimal.ZERO

                ThresholdItemState(
                    productType = prod,
                    location = loc,
                    currentQuantityKg = currentQty,
                    thresholdInput = thresholdKg?.stripTrailingZeros()?.toPlainString() ?: "",
                    configuredThresholdKg = thresholdKg,
                    isLowStock = isLow,
                    deficitKg = deficit,
                )
            }

            _state.update {
                it.copy(
                    loading = false,
                    items = items,
                    lowStockItems = lowStockItems,
                    error = null,
                )
            }
        }
    }

    fun updateThresholdInput(productType: String, location: String, input: String) {
        _state.update { s ->
            s.copy(
                items = s.items.map { item ->
                    if (item.productType == productType && item.location == location) {
                        item.copy(thresholdInput = input, error = null)
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun saveAll(onSuccess: () -> Unit = {}) {
        val currentItems = _state.value.items
        var hasError = false
        val validatedItems = currentItems.map { item ->
            val text = item.thresholdInput.trim()
            if (text.isEmpty()) {
                item.copy(error = null)
            } else {
                val err = Checks.zeroOrMore(text)
                if (err != null) {
                    hasError = true
                    item.copy(error = err)
                } else {
                    item.copy(error = null)
                }
            }
        }

        if (hasError) {
            _state.update { it.copy(items = validatedItems) }
            return
        }

        _state.update { it.copy(items = validatedItems, saving = true, error = null) }

        viewModelScope.launch {
            for (item in validatedItems) {
                val text = item.thresholdInput.trim()
                val targetKg = if (text.isEmpty()) BigDecimal.ZERO else text.decimal()
                val isChanged = if (item.configuredThresholdKg != null) {
                    targetKg.compareTo(item.configuredThresholdKg) != 0
                } else {
                    text.isNotEmpty()
                }

                if (isChanged) {
                    val res = repository.setThreshold(item.productType, item.location, SetThresholdRequest(targetKg))
                    if (res is ApiResult.Failure) {
                        _state.update { it.copy(saving = false, error = res.error) }
                        return@launch
                    }
                }
            }
            _state.update { it.copy(saving = false) }
            onSuccess()
            reload()
        }
    }

    companion object {
        val LEGAL_PAIRS = listOf(
            "RAW_SILK" to "RAW_STOCK",
            "VUDA_WARP" to "RAW_STOCK",
            "SILK_WARP" to "INTERNAL_WIP",
            "KORA_WARP" to "INTERNAL_WIP",
            "SILK_WARP" to "EXTERNAL_WIP",
            "KORA_WARP" to "EXTERNAL_WIP",
            "SILK_WARP" to "FINISHED_STOCK",
            "KORA_WARP" to "FINISHED_STOCK",
        )
    }
}
