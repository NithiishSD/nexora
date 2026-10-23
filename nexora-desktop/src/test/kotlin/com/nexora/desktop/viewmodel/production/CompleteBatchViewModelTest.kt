package com.nexora.desktop.viewmodel.production

import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CompleteBatchViewModelTest {

    @Test
    @DisplayName("FR-PROD-03 save enabled only when output wastage discrepancy equal input")
    fun saveEnabledOnlyWhenOutputWastageDiscrepancyEqualInput() = vmTest { server ->
        server.onJson(
            "POST /production-batches/b1/complete",
            """{"id":"b1","batchNumber":"BAT-2026-0001","productType":"KORA_WARP","productionDate":"2026-10-08","inputWeightKg":100.000,
                "outputWeightKg":94.000,"wastageWeightKg":4.000,"discrepancyWeightKg":2.000,"wastagePercent":4.00,"status":"COMPLETED"}""",
        )
        val vm = CompleteBatchViewModel(ProductionRepository(server.client()), TEST_CLOCK, "b1", BigDecimal("100.000"))

        vm.edit { it.copy(output = "94.000", wastage = "4.000", discrepancy = "1.000") }
        assertFalse(vm.saveEnabled(vm.form.value)) // 99 kg accounted for
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(0, server.count("POST /production-batches/b1/complete"))

        vm.edit { it.copy(discrepancy = "2") } // 100 = 94 + 4 + 2, written without decimals
        assertTrue(vm.saveEnabled(vm.form.value))
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(
            """{"outputWeightKg":94.000,"wastageWeightKg":4.000,"discrepancyWeightKg":2,"completionDate":"2026-10-08"}""",
            server.last("POST /production-batches/b1/complete").body,
        )
        // Wastage % comes from the server.
        assertEquals("4.00", vm.status.value.result!!.wastagePercent!!.toPlainString())
    }
}
