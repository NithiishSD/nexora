package com.nexora.desktop.viewmodel.orders

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.OrderItemRequest
import com.nexora.desktop.api.dto.OrderRequest
import com.nexora.desktop.api.dto.OrderResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock

data class ItemRow(
    val productType: String = "SILK_WARP",
    val weight: String = "",
    val rate: String = "",
    val warpSpecification: String = "",
    /** The server's amount for this line when it was loaded and not edited since; null = "calculated on save". */
    val serverAmount: BigDecimal? = null,
)

data class OrderForm(
    val customer: PickOption? = null,
    val orderDate: String = "",
    val requiredDate: String = "",
    val notes: String = "",
    val items: List<ItemRow> = listOf(ItemRow()),
    /** Set when the order may no longer be changed; the form is then shown read-only with this reason. */
    val readOnlyReason: String? = null,
    /** The server's order total, only for a loaded order that was not edited. */
    val serverTotal: BigDecimal? = null,
)

/**
 * S-ORD-02 Order form (FR-ORD-01, -02, -06, -15). Item amounts and the total are never computed here: unsaved lines
 * read "calculated on save" and the figures come from the API response (NFR-USE-02, Q5 rounding is the server's).
 */
class OrderFormViewModel(
    private val repository: OrderRepository,
    private val clock: Clock,
    private val orderId: String?,
) : FormViewModel<OrderForm, OrderResponse>(OrderForm(orderDate = Dates.format(Dates.today(clock)))) {

    val isEdit: Boolean get() = orderId != null

    init {
        if (orderId != null) {
            viewModelScope.launch {
                repository.get(orderId).getOrNull()?.let { order -> preset { fromOrder(order) } }
            }
        }
    }

    private fun fromOrder(o: OrderResponse) = OrderForm(
        customer = PickOption(o.customer.id, o.customer.name),
        orderDate = Dates.formatIso(o.orderDate),
        requiredDate = o.requiredDate?.let(Dates::formatIso).orEmpty(),
        notes = o.notes.orEmpty(),
        items = o.items.sortedBy { it.lineNo }.map {
            ItemRow(it.productType, it.requiredWeightKg.toPlainString(), it.ratePerKg.toPlainString(), it.warpSpecification.orEmpty(), it.amount)
        },
        // Edit only while Placed/Confirmed, not locked, no allocations: the server's `modifiable` (FR-ORD-08).
        readOnlyReason = if (o.modifiable) null else if (o.locked) s("order.lockedReason") else s("order.notModifiableReason", o.orderNumber),
        serverTotal = o.totalAmount,
    )

    /** What the Amount column shows for a line. */
    fun amountText(row: ItemRow): String = row.serverAmount?.let(IndianFormat::money) ?: s("common.calculatedOnSave")

    fun totalText(form: OrderForm): String = form.serverTotal?.let(IndianFormat::money) ?: s("common.calculatedOnSave")

    /** Editing a line drops its server amount and the server total: they are only known again after saving. */
    fun updateItem(index: Int, change: (ItemRow) -> ItemRow) = edit { f ->
        f.copy(items = f.items.mapIndexed { i, row -> if (i == index) change(row).copy(serverAmount = null) else row }, serverTotal = null)
    }

    /** Ctrl+Enter adds a line. */
    fun addItem() = edit { it.copy(items = it.items + ItemRow(), serverTotal = null) }

    /** Ctrl+Delete removes the focused line; at least one line stays. */
    fun removeItem(index: Int) = edit { f ->
        if (f.items.size <= 1) f else f.copy(items = f.items.filterIndexed { i, _ -> i != index }, serverTotal = null)
    }

    override fun validate(form: OrderForm): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        form.readOnlyReason?.let { errors["form"] = it }
        if (form.customer == null) errors["customerId"] = s("validation.required")
        Checks.date(form.orderDate, clock, notAfterToday = false)?.let { errors["orderDate"] = it }
        if (form.requiredDate.isNotBlank()) {
            val orderDate = Dates.parseInput(form.orderDate, clock)
            Checks.date(form.requiredDate, clock, notAfterToday = false, notBefore = orderDate)?.let { errors["requiredDate"] = it }
        }
        form.items.forEachIndexed { i, row ->
            Checks.positive(row.weight)?.let { errors["items[$i].requiredWeightKg"] = it }
            Checks.zeroOrMore(row.rate)?.let { errors["items[$i].ratePerKg"] = it }
        }
        return errors
    }

    override suspend fun send(form: OrderForm, idempotencyKey: String): ApiResult<OrderResponse> {
        val items = form.items.map {
            OrderItemRequest(it.productType, it.weight.decimal(), it.rate.decimal(), it.warpSpecification.trim().ifEmpty { null })
        }
        val required = form.requiredDate.takeIf { it.isNotBlank() }?.let { Dates.toIso(it, clock) }
        val notes = form.notes.trim().ifEmpty { null }
        return if (orderId == null) {
            repository.create(OrderRequest(form.customer!!.id, Dates.toIso(form.orderDate, clock), required, notes, items))
        } else {
            repository.update(orderId, OrderRequest(requiredDate = required, notes = notes, items = items))
        }
    }
}
