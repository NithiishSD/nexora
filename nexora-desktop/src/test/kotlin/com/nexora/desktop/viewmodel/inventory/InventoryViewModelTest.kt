package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventoryViewModelTest {

    @Test
    @DisplayName("FR-INV-18 FR-PROC-20 loads thresholds and flags low stock")
    fun loadsThresholdsAndFlagsLowStock() = vmTest { server ->
        server.onJson(
            "GET /inventory/thresholds",
            """[{"id":"11111111-1111-1111-1111-111111111111","productType":"RAW_SILK","location":"RAW_STOCK","minThresholdKg":50.000},
                {"id":"22222222-2222-2222-2222-222222222222","productType":"SILK_WARP","location":"FINISHED_STOCK","minThresholdKg":10.000}]""",
        )
        server.onJson(
            "GET /inventory/summary",
            """{"balances":[{"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":20.000,"minThresholdKg":50.000,"isLowStock":true},
                            {"productType":"SILK_WARP","location":"FINISHED_STOCK","quantityKg":15.000,"minThresholdKg":10.000,"isLowStock":false}],
                "rawSilkAvailableKg":20.000,"internalWipKg":0.000,"externalWipKg":0.000,"finishedSilkWarpKg":15.000,
                "finishedKoraWarpKg":0.000,"vudaWarpKg":0.000,"wastageToDateKg":[],"unit":"kg","asOf":"2026-10-18T10:00:00Z"}""",
        )
        server.onJson(
            "GET /inventory/low-stock",
            """[{"productType":"RAW_SILK","location":"RAW_STOCK","currentQuantityKg":20.000,"minThresholdKg":50.000,"deficitKg":30.000}]""",
        )

        val vm = InventoryViewModel(InventoryRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(8, state.items.size)

        val rawSilk = state.items.first { it.productType == "RAW_SILK" && it.location == "RAW_STOCK" }
        assertEquals(BigDecimal("20.000"), rawSilk.currentQuantityKg)
        assertEquals(BigDecimal("50.000"), rawSilk.configuredThresholdKg)
        assertTrue(rawSilk.isLowStock)
        assertEquals(BigDecimal("30.000"), rawSilk.deficitKg)

        val silkWarp = state.items.first { it.productType == "SILK_WARP" && it.location == "FINISHED_STOCK" }
        assertEquals(BigDecimal("15.000"), silkWarp.currentQuantityKg)
        assertEquals(BigDecimal("10.000"), silkWarp.configuredThresholdKg)
        assertFalse(silkWarp.isLowStock)
        assertEquals(BigDecimal.ZERO, silkWarp.deficitKg)

        // Low-stock list in state
        assertEquals(1, state.lowStockItems.size)
        assertEquals("RAW_SILK", state.lowStockItems.first().productType)
        assertEquals(BigDecimal("30.000"), state.lowStockItems.first().deficitKg)
    }

    @Test
    @DisplayName("FR-INV-18 saveAll sends PUT request for modified threshold")
    fun saveAll_sendsPutRequestForModifiedThreshold() = vmTest { server ->
        server.onJson(
            "GET /inventory/thresholds",
            """[{"id":"11111111-1111-1111-1111-111111111111","productType":"RAW_SILK","location":"RAW_STOCK","minThresholdKg":50.000}]""",
        )
        server.onJson(
            "GET /inventory/summary",
            """{"balances":[{"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":20.000,"minThresholdKg":50.000,"isLowStock":true}],
                "rawSilkAvailableKg":20.000,"internalWipKg":0.000,"externalWipKg":0.000,"finishedSilkWarpKg":0.000,
                "finishedKoraWarpKg":0.000,"vudaWarpKg":0.000,"wastageToDateKg":[],"unit":"kg","asOf":"2026-10-18T10:00:00Z"}""",
        )
        server.onJson("GET /inventory/low-stock", "[]")
        server.onJson(
            "PUT /inventory/thresholds/RAW_SILK/RAW_STOCK",
            """{"id":"11111111-1111-1111-1111-111111111111","productType":"RAW_SILK","location":"RAW_STOCK","minThresholdKg":75.000}""",
        )

        val vm = InventoryViewModel(InventoryRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        vm.updateThresholdInput("RAW_SILK", "RAW_STOCK", "75.000")
        var saved = false
        vm.saveAll(onSuccess = { saved = true })
        testScheduler.advanceUntilIdle()

        assertTrue(saved)
        assertEquals("""{"minThresholdKg":75.000}""", server.last("PUT /inventory/thresholds/RAW_SILK/RAW_STOCK").body)
    }
}
