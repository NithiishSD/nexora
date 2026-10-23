package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.api.dto.MovementResponse
import com.nexora.desktop.api.dto.StockAdjustmentRequest
import com.nexora.desktop.api.dto.WastageRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.time.Clock

data class StockChangeForm(
    val productType: String = "RAW_SILK",
    val fromLocation: String = "RAW_STOCK",
    val toLocation: String = "",
    val weight: String = "",
    val date: String = "",
    val reason: String = "",
    val notes: String = "",
)

/**
 * S-INV-04 Record wastage ([wastage] = true) and S-INV-05 Stock adjustment (FR-INV-11, FR-INV-12).
 * A reason is always required; the server refuses anything that would make stock negative (ADR-026).
 */
class StockAdjustmentViewModel(
    private val repository: InventoryRepository,
    private val clock: Clock,
    val wastage: Boolean = false,
) : FormViewModel<StockChangeForm, MovementResponse>(StockChangeForm(date = Dates.format(Dates.today(clock)))) {

    /** Locations a product can be held at (plan decision R2), used to offer only legal choices. */
    fun sourcesFor(product: String): List<String> = if (wastage) {
        if (product == "RAW_SILK" || product == "VUDA_WARP") listOf("RAW_STOCK") else listOf("INTERNAL_WIP", "EXTERNAL_WIP")
    } else {
        ALL_LOCATIONS
    }

    fun setProduct(product: String) = edit { it.copy(productType = product, fromLocation = sourcesFor(product).first()) }

    override fun validate(form: StockChangeForm): Map<String, String> = Checks.collect(
        "quantityKg" to Checks.positive(form.weight),
        "movementDate" to Checks.date(form.date, clock),
        "reason" to Checks.required(form.reason),
        "toLocation" to if (wastage) null else when {
            form.toLocation.isEmpty() -> s("validation.required")
            form.toLocation == form.fromLocation -> s("inventory.sameLocation")
            form.fromLocation !in INTERNAL && form.toLocation !in INTERNAL -> s("inventory.oneInternal")
            else -> null
        },
    )

    override suspend fun send(form: StockChangeForm, idempotencyKey: String): ApiResult<MovementResponse> {
        val date = Dates.toIso(form.date, clock)!!
        return if (wastage) {
            repository.recordWastage(WastageRequest(form.productType, form.fromLocation, form.weight.decimal(), date, form.reason.trim()), idempotencyKey)
        } else {
            repository.adjust(
                StockAdjustmentRequest(form.productType, form.fromLocation, form.toLocation, form.weight.decimal(), date, form.reason.trim(), form.notes.trim().ifEmpty { null }),
                idempotencyKey,
            )
        }
    }

    companion object {
        val INTERNAL = listOf("RAW_STOCK", "INTERNAL_WIP", "EXTERNAL_WIP", "FINISHED_STOCK")
        val ALL_LOCATIONS = INTERNAL + listOf("SUPPLIER", "CUSTOMER", "WASTAGE", "RETURNED")
    }
}
