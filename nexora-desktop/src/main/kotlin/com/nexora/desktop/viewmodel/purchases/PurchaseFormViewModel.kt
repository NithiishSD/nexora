package com.nexora.desktop.viewmodel.purchases

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.LowStockItemDto
import com.nexora.desktop.api.dto.NewPurchaseRequest
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.ui.components.PickOption
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

data class PurchaseForm(
    val supplier: PickOption? = null,
    val productType: String = "RAW_SILK",
    val purchaseDate: String = "",
    val orderedWeightKg: String = "",
    val ratePerKg: String = "",
    val paymentType: String = "CREDIT",
    val creditDays: String = "",
    val billReference: String = "",
    val notes: String = "",
)

/**
 * S-PUR-02 New purchase (FR-PROC-01, -04, -17). Value and due date are NOT computed here: the form says
 * "calculated on save" and the server's figures appear in the purchase detail after saving.
 */
class PurchaseFormViewModel(
    private val purchases: PurchaseRepository,
    private val parties: PartyRepository,
    private val clock: Clock,
    private val inventory: InventoryRepository? = null,
) : FormViewModel<PurchaseForm, PurchaseResponse>(PurchaseForm(purchaseDate = Dates.format(Dates.today(clock)))) {

    private val _rawSilkLowStock = MutableStateFlow<LowStockItemDto?>(null)
    val rawSilkLowStock: StateFlow<LowStockItemDto?> = _rawSilkLowStock.asStateFlow()

    init {
        if (inventory != null) {
            viewModelScope.launch {
                when (val res = inventory.getLowStock()) {
                    is ApiResult.Success -> {
                        _rawSilkLowStock.value = res.value.firstOrNull { it.productType == "RAW_SILK" && it.location == "RAW_STOCK" }
                    }
                    is ApiResult.Failure -> {}
                }
            }
        }
    }

    /** Picking a supplier pre-fills its default credit days (still editable). */
    fun pickSupplier(option: PickOption) {
        edit { it.copy(supplier = option) }
        viewModelScope.launch {
            val party = parties.get(option.id).getOrNull() ?: return@launch
            if (form.value.paymentType == "CREDIT") preset { it.copy(creditDays = party.defaultCreditDays?.toString().orEmpty()) }
        }
    }

    /** Cash purchases have no credit: days are forced to 0 and the field is disabled (FR-PROC-04). */
    fun setPaymentType(type: String) = edit { it.copy(paymentType = type, creditDays = if (type == "CASH") "0" else "") }

    override fun validate(form: PurchaseForm): Map<String, String> = Checks.collect(
        "supplierId" to if (form.supplier == null) s("validation.required") else null,
        "purchaseDate" to Checks.date(form.purchaseDate, clock),
        "orderedWeightKg" to Checks.positive(form.orderedWeightKg),
        "ratePerKg" to Checks.zeroOrMore(form.ratePerKg),
        "creditDays" to form.creditDays.takeIf { it.isNotBlank() }?.let { if ((it.toIntOrNull() ?: -1) !in 0..365) s("validation.range", 0, 365) else null },
    )

    override suspend fun send(form: PurchaseForm, idempotencyKey: String): ApiResult<PurchaseResponse> = purchases.create(
        NewPurchaseRequest(
            supplierId = form.supplier!!.id,
            productType = form.productType,
            purchaseDate = Dates.toIso(form.purchaseDate, clock)!!,
            orderedWeightKg = form.orderedWeightKg.decimal(),
            ratePerKg = form.ratePerKg.decimal(),
            paymentType = form.paymentType,
            // Empty credit days on a credit purchase = the server uses the supplier's default.
            creditDays = if (form.paymentType == "CASH") 0 else form.creditDays.toIntOrNull(),
            billReference = form.billReference.trim().ifEmpty { null },
            notes = form.notes.trim().ifEmpty { null },
        ),
    )
}
