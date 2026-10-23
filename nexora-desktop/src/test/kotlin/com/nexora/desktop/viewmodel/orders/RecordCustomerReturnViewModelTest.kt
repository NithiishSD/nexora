package com.nexora.desktop.viewmodel.orders

import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.ErrorMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordCustomerReturnViewModelTest {

    @Test
    @DisplayName("FR-ORD-20 validation requires item, positive weight, and reason")
    fun validation_requiresFields() = vmTest { server ->
        val vm = RecordCustomerReturnViewModel(OrderRepository(server.client()), TEST_CLOCK, "order-123")

        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals("Required", vm.status.value.fieldErrors["orderItemId"])
        assertEquals("Required", vm.status.value.fieldErrors["returnedWeightKg"])
        assertEquals("Required", vm.status.value.fieldErrors["reason"])
        assertEquals(0, server.count("POST /orders/order-123/returns"))

        vm.edit { it.copy(orderItemId = "item-1", weight = "0.000", reason = "Defect") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Enter an amount greater than zero.", vm.status.value.fieldErrors["returnedWeightKg"])
    }

    @Test
    @DisplayName("FR-ORD-20 sends idempotent return request and handles 409 conflict")
    fun sendsReturn_andHandlesConflict() = vmTest { server ->
        server.on("POST /orders/order-123/returns") {
            problem(409, "INSUFFICIENT_INVENTORY", "Item item-456 has only 2.000 kg still returnable.")
        }
        val vm = RecordCustomerReturnViewModel(OrderRepository(server.client()), TEST_CLOCK, "order-123")

        vm.edit {
            it.copy(
                orderItemId = "item-456",
                weight = "5.000",
                usable = true,
                reason = "Reed marks defect",
                notes = "Customer credit",
            )
        }
        vm.save()
        testScheduler.advanceUntilIdle()

        val req = server.last("POST /orders/order-123/returns")
        assertNotNull(req.headers["Idempotency-Key"])
        assertEquals(
            """{"orderItemId":"item-456","returnDate":"2026-10-08","returnedWeightKg":5.000,"usable":true,"reason":"Reed marks defect","notes":"Customer credit"}""",
            req.body,
        )

        val error = vm.status.value.error!!
        assertEquals("INSUFFICIENT_INVENTORY", error.code)
        assertEquals("Item item-456 has only 2.000 kg still returnable.", ErrorMessages.detail(error))
    }
}
