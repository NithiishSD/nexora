package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StockAdjustmentViewModelTest {

    @Test
    @DisplayName("FR-INV-12 reason is required")
    fun reasonIsRequired() = vmTest { server ->
        server.onJson(
            "POST /inventory/adjustments",
            """{"id":"m9","movementNumber":"MOV-2026-0031","movementType":"ADJUSTMENT","productType":"RAW_SILK","quantityKg":3.000,
                "fromLocation":"SUPPLIER","toLocation":"RAW_STOCK","movementDate":"2026-10-01","reason":"stock count 2026-10-01"}""",
            201,
        )
        val vm = StockAdjustmentViewModel(InventoryRepository(server.client()), TEST_CLOCK, wastage = false)
        vm.edit { it.copy(fromLocation = "SUPPLIER", toLocation = "RAW_STOCK", weight = "3.000", date = "01-10-2026") }

        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.status.value.fieldErrors["reason"])
        assertEquals(0, server.count("POST /inventory/adjustments"))

        vm.edit { it.copy(reason = "stock count 2026-10-01") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(
            """{"productType":"RAW_SILK","fromLocation":"SUPPLIER","toLocation":"RAW_STOCK","quantityKg":3.000,"movementDate":"2026-10-01","reason":"stock count 2026-10-01"}""",
            server.last("POST /inventory/adjustments").body,
        )
        assertEquals("MOV-2026-0031", vm.status.value.result?.movementNumber)
    }

    @Test
    @DisplayName("FR-INV-12 same from and to place is blocked")
    fun sameLocations_blocked() = vmTest { server ->
        val vm = StockAdjustmentViewModel(InventoryRepository(server.client()), TEST_CLOCK, wastage = false)
        vm.edit { it.copy(fromLocation = "RAW_STOCK", toLocation = "RAW_STOCK", weight = "1.000", reason = "count") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("From and To must be different places.", vm.status.value.fieldErrors["toLocation"])
    }
}
