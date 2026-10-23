package com.nexora.desktop.viewmodel.orders

import com.nexora.desktop.api.ApiJson
import com.nexora.desktop.api.dto.OrderResponse
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.viewmodel.orders.OrderAction.BILL
import com.nexora.desktop.viewmodel.orders.OrderAction.CANCEL
import com.nexora.desktop.viewmodel.orders.OrderAction.COMPLETE
import com.nexora.desktop.viewmodel.orders.OrderAction.CONFIRM
import com.nexora.desktop.viewmodel.orders.OrderAction.DELIVER
import com.nexora.desktop.viewmodel.orders.OrderAction.EDIT
import com.nexora.desktop.viewmodel.orders.OrderAction.PAY
import com.nexora.desktop.viewmodel.orders.OrderAction.READY
import com.nexora.desktop.viewmodel.orders.OrderAction.RETURN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OrderDetailViewModelTest {

    private fun order(status: String, locked: Boolean = false, modifiable: Boolean = true) =
        ApiJson.decodeFromString(OrderResponse.serializer(), Fixtures.order(status, locked, modifiable))

    @Test
    @DisplayName("FR-ORD-07 FR-ORD-11 only legal next actions are shown")
    fun onlyLegalNextActionsAreShown() = vmTest { server ->
        server.onJson("GET /orders/4c2d", Fixtures.order())
        server.onJson("POST /orders/4c2d/deliver", Fixtures.order(status = "DELIVERED", locked = true, modifiable = false))
        server.onJson("POST /orders/4c2d/bill", """{"id":"doc-1","documentType":"SALES_BILL","fileName":"bill.pdf"}""")
        val vm = OrderDetailViewModel(OrderRepository(server.client()), TEST_CLOCK, "4c2d")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(EDIT, CONFIRM, CANCEL, PAY), vm.actionsFor(order("PLACED")))
        assertEquals(listOf(EDIT, READY, CANCEL, PAY), vm.actionsFor(order("CONFIRMED")))
        // Confirmed but with a payment allocated: not modifiable -> no edit, no cancel (reverse the payment first).
        assertEquals(listOf(READY, PAY), vm.actionsFor(order("CONFIRMED", modifiable = false)))
        assertEquals(listOf(READY, PAY), vm.actionsFor(order("IN_PROGRESS", locked = true, modifiable = false)))
        assertEquals(listOf(DELIVER, PAY), vm.actionsFor(order("READY", locked = true, modifiable = false)))
        assertEquals(listOf(COMPLETE, PAY, RETURN, BILL), vm.actionsFor(order("DELIVERED", locked = true, modifiable = false)))
        assertEquals(listOf(PAY, RETURN, BILL), vm.actionsFor(order("COMPLETED", locked = true, modifiable = false)))
        assertEquals(emptyList<OrderAction>(), vm.actionsFor(order("CANCELLED", modifiable = false)))

        // Deliver sends the date and an Idempotency-Key; the screen then shows the server's order.
        vm.deliver("18-10-2026")
        testScheduler.advanceUntilIdle()
        assertEquals("""{"deliveryDate":"2026-10-18"}""", server.last("POST /orders/4c2d/deliver").body)
        assertNotNull(server.last("POST /orders/4c2d/deliver").headers["Idempotency-Key"])
        assertEquals("DELIVERED", vm.data.value.value!!.status)

        // Generate bill posts to /orders/4c2d/bill and reloads
        vm.generateBill()
        testScheduler.advanceUntilIdle()
        assertEquals(1, server.count("POST /orders/4c2d/bill"))
    }

    @Test
    @DisplayName("FR-ORD-08 locked order form is read only")
    fun lockedOrder_formIsReadOnly() = vmTest { server ->
        server.onJson("GET /orders/4c2d", Fixtures.order(status = "IN_PROGRESS", locked = true, modifiable = false))
        val detail = OrderDetailViewModel(OrderRepository(server.client()), TEST_CLOCK, "4c2d")
        val form = OrderFormViewModel(OrderRepository(server.client()), TEST_CLOCK, "4c2d")
        testScheduler.advanceUntilIdle()

        // No Edit button on the detail ...
        assertEquals(false, EDIT in detail.actionsFor(detail.data.value.value!!))
        // ... and the form, if opened anyway, is read-only with the reason, and never sends a PATCH.
        assertEquals("This order is already in production or outsourcing and cannot be changed.", form.form.value.readOnlyReason)
        form.save()
        testScheduler.advanceUntilIdle()
        assertEquals(0, server.count("PATCH /orders/4c2d"))
    }
}
