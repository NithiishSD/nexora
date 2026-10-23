package com.nexora.desktop.viewmodel.production

import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BatchDetailViewModelTest {

    private fun batch(status: String) = """{"id":"b1","batchNumber":"BAT-2026-0001","productType":"KORA_WARP","productionDate":"2026-10-08",
        "inputWeightKg":100.000,"status":"$status","orderItems":[{"orderItemId":"e1f2","orderId":"4c2d","orderNumber":"ORD-2026-0001","lineNo":1,"allocatedWeightKg":60.000}]}"""

    @Test
    @DisplayName("FR-PROD-02 start requires confirmation")
    fun startRequiresConfirmation() = vmTest { server ->
        server.onJson("GET /production-batches/b1", batch("PLANNED"))
        server.onJson("POST /production-batches/b1/start", batch("IN_PROGRESS").replace("\"orderItems\"", "\"consumptionMovementNumber\":\"MOV-2026-0040\",\"orderItems\""))
        val vm = BatchDetailViewModel(ProductionRepository(server.client()), TEST_CLOCK, "b1")
        testScheduler.advanceUntilIdle()

        // Without the confirmation dialog open, start does nothing.
        vm.start()
        testScheduler.advanceUntilIdle()
        assertEquals(0, server.count("POST /production-batches/b1/start"))

        vm.ask("start")
        vm.start()
        testScheduler.advanceUntilIdle()
        assertEquals("""{"startDate":"2026-10-08"}""", server.last("POST /production-batches/b1/start").body)
        assertEquals("IN_PROGRESS", vm.data.value.value!!.status)
        assertEquals("MOV-2026-0040", vm.data.value.value!!.consumptionMovementNumber)
    }
}
