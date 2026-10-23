package com.nexora.desktop.viewmodel.production

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.BatchLinkRequest
import com.nexora.desktop.api.dto.BatchRequest
import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
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

/** S-PRD-01 Production batches (FR-PROD-06). */
class BatchListViewModel(repository: ProductionRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<BatchResponse>(ListQuery(filters = filters), clock, { repository.list(it) })

/** One link of a batch or job to an order item, with the kg set aside for it. */
data class LinkRow(val item: PickOption? = null, val weight: String = "")

data class BatchForm(
    val productType: String = "SILK_WARP",
    val date: String = "",
    val input: String = "",
    val notes: String = "",
    val links: List<LinkRow> = emptyList(),
)

/**
 * S-PRD-02 Batch form for a new batch or a Planned one (FR-PROD-01, -07, -11). "Raw silk available" is shown from the
 * inventory summary as information only.
 */
class BatchFormViewModel(
    private val repository: ProductionRepository,
    inventory: InventoryRepository,
    private val clock: Clock,
    private val batchId: String?,
) : FormViewModel<BatchForm, BatchResponse>(BatchForm(date = Dates.format(Dates.today(clock)))) {

    val isEdit: Boolean get() = batchId != null

    private val _rawSilkAvailable = MutableStateFlow<BigDecimal?>(null)
    val rawSilkAvailable: StateFlow<BigDecimal?> = _rawSilkAvailable.asStateFlow()

    init {
        viewModelScope.launch { _rawSilkAvailable.value = inventory.summary().getOrNull()?.rawSilkAvailableKg }
        if (batchId != null) {
            viewModelScope.launch {
                repository.get(batchId).getOrNull()?.let { b ->
                    preset {
                        BatchForm(
                            b.productType, Dates.formatIso(b.productionDate), b.inputWeightKg.toPlainString(), b.notes.orEmpty(),
                            b.orderItems.orEmpty().map { LinkRow(PickOption(it.orderItemId, s("payment.orderLine", it.orderNumber.orEmpty(), it.lineNo ?: 0)), it.allocatedWeightKg.toPlainString()) },
                        )
                    }
                }
            }
        }
    }

    fun addLink() = edit { it.copy(links = it.links + LinkRow()) }
    fun removeLink(index: Int) = edit { f -> f.copy(links = f.links.filterIndexed { i, _ -> i != index }) }
    fun updateLink(index: Int, change: (LinkRow) -> LinkRow) = edit { f -> f.copy(links = f.links.mapIndexed { i, r -> if (i == index) change(r) else r }) }

    override fun validate(form: BatchForm): Map<String, String> {
        val errors = Checks.collect("productionDate" to Checks.date(form.date, clock, notAfterToday = false), "inputWeightKg" to Checks.positive(form.input)).toMutableMap()
        form.links.forEachIndexed { i, link ->
            if (link.item == null) errors["orderItems[$i].orderItemId"] = s("validation.required")
            Checks.positive(link.weight)?.let { errors["orderItems[$i].allocatedWeightKg"] = it }
        }
        return errors
    }

    override suspend fun send(form: BatchForm, idempotencyKey: String): ApiResult<BatchResponse> {
        val request = BatchRequest(
            productType = if (batchId == null) form.productType else null,
            productionDate = Dates.toIso(form.date, clock)!!,
            inputWeightKg = form.input.decimal(),
            notes = form.notes.trim().ifEmpty { null },
            orderItems = form.links.map { BatchLinkRequest(it.item!!.id, it.weight.decimal()) },
        )
        return if (batchId == null) repository.create(request) else repository.update(batchId, request)
    }
}
