package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.ErrorMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReturnDialogViewModelTest {

    @Test
    @DisplayName("FR-PROC-13 insufficient inventory shows message")
    fun insufficientInventory_showsMessage() = vmTest { server ->
        server.on("POST /purchases/8a1b/returns") { problem(409, "INSUFFICIENT_INVENTORY", "Only 21.000 kg of RAW_SILK available in RAW_STOCK.") }
        val vm = ReturnDialogViewModel(PurchaseRepository(server.client()), TEST_CLOCK, "8a1b")

        // Reason is required before anything is sent.
        vm.edit { it.copy(weight = "59.000") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.status.value.fieldErrors["reason"])
        assertEquals(0, server.count("POST /purchases/8a1b/returns"))

        vm.edit { it.copy(reason = "Moisture damage") }
        vm.save()
        testScheduler.advanceUntilIdle()

        val error = vm.status.value.error!!
        assertEquals("Not enough stock for this.", ErrorMessages.message(error))
        assertEquals("Only 21.000 kg of RAW_SILK available in RAW_STOCK.", ErrorMessages.detail(error))
        assertEquals("""{"returnDate":"2026-10-08","returnedWeightKg":59.000,"reason":"Moisture damage"}""", server.last("POST /purchases/8a1b/returns").body)
    }
}
