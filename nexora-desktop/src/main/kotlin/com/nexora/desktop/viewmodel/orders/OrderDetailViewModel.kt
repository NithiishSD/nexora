package com.nexora.desktop.viewmodel.orders

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.CustomerReturnResponse
import com.nexora.desktop.api.dto.DeliverRequest
import com.nexora.desktop.api.dto.OrderResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.DetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** The order actions of 06 S-ORD-03, in the order of the action bar. */
enum class OrderAction { EDIT, CONFIRM, READY, DELIVER, COMPLETE, CANCEL, PAY, RETURN, BILL }

/** S-ORD-03 Order detail (FR-ORD-04, -07..-13, -16, -20). */
class OrderDetailViewModel(
    private val repository: OrderRepository,
    private val clock: Clock,
    val orderId: String,
) : DetailViewModel<OrderResponse>(clock, { repository.get(orderId) }) {

    private val _returns = MutableStateFlow<List<CustomerReturnResponse>>(emptyList())
    val returns: StateFlow<List<CustomerReturnResponse>> = _returns.asStateFlow()

    init {
        loadReturns()
    }

    fun loadReturns() {
        viewModelScope.launch {
            when (val res = repository.getReturns(orderId)) {
                is ApiResult.Success -> _returns.value = res.value
                is ApiResult.Failure -> Unit
            }
        }
    }

    /**
     * Only the legal next steps for the current status are offered (06 S-ORD-03 table). Edit and cancel follow the
     * server's `modifiable` flag (not locked, no payment allocated); the server still refuses anything illegal.
     */
    fun actionsFor(o: OrderResponse): List<OrderAction> = buildList {
        if (o.modifiable) add(OrderAction.EDIT)
        when (o.status) {
            "PLACED" -> add(OrderAction.CONFIRM)
            "CONFIRMED", "IN_PROGRESS" -> add(OrderAction.READY)
            "READY" -> add(OrderAction.DELIVER)
            "DELIVERED" -> add(OrderAction.COMPLETE)
        }
        if (o.status in listOf("PLACED", "CONFIRMED") && o.modifiable) add(OrderAction.CANCEL)
        if (o.status != "CANCELLED") add(OrderAction.PAY)
        if (o.status in listOf("DELIVERED", "COMPLETED")) {
            add(OrderAction.RETURN)
            add(OrderAction.BILL)
        }
    }

    fun confirm() = runAction({ repository.confirm(orderId) })

    fun ready() = runAction({ repository.ready(orderId) })

    /** Delivery moves stock for every item; idempotent so a retry after a timeout never delivers twice. */
    fun deliver(date: String) = runAction({ key ->
        repository.deliver(orderId, DeliverRequest(Dates.toIso(date, clock) ?: Dates.today(clock).toString()), key)
    })

    fun complete() = runAction({ repository.complete(orderId) })

    fun cancel(reason: String) = runAction({ repository.cancel(orderId, reason) })

    fun generateBill() = runAndReload({ repository.generateBill(orderId) })
}
