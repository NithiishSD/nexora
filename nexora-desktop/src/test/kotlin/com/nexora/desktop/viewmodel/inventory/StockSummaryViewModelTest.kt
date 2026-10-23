package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StockSummaryViewModelTest {

    @Test
    @DisplayName("FR-INV-07 NFR-USE-02 balances shown as returned")
    fun balancesShownAsReturned() = vmTest { server ->
        // E-INV-01 example. internalWipKg is deliberately not the sum of the WIP rows: the tile shows the server value.
        server.onJson(
            "GET /inventory/summary",
            """{"balances":[{"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":20.000},
                            {"productType":"KORA_WARP","location":"INTERNAL_WIP","quantityKg":40.000},
                            {"productType":"SILK_WARP","location":"INTERNAL_WIP","quantityKg":0.000}],
                "rawSilkAvailableKg":20.000,"internalWipKg":41.500,"externalWipKg":45.000,"finishedSilkWarpKg":0.000,
                "finishedKoraWarpKg":55.500,"vudaWarpKg":0.000,"wastageToDateKg":[{"productType":"RAW_SILK","quantityKg":1.500}],
                "unit":"kg","asOf":"2026-10-08T05:00:00Z"}""",
        )
        val vm = StockSummaryViewModel(InventoryRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val d = vm.data.value.value!!
        assertEquals(BigDecimal("41.500"), d.internalWipKg)
        assertEquals(BigDecimal("20.000"), d.rawSilkAvailableKg)
        // Zero rows are kept, in the server's order and with scale 3.
        assertEquals(listOf("20.000", "40.000", "0.000"), d.balances.map { it.quantityKg.toPlainString() })
        assertEquals(BigDecimal("1.500"), d.wastageToDateKg.single().quantityKg)
    }

    @Test
    @DisplayName("FR-INV-18 stock summary balances include min threshold and low stock flag")
    fun stockSummaryBalances_includeThresholdAndLowStockFlag() = vmTest { server ->
        server.onJson(
            "GET /inventory/summary",
            """{"balances":[{"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":20.000,"minThresholdKg":50.000,"isLowStock":true},
                            {"productType":"SILK_WARP","location":"FINISHED_STOCK","quantityKg":15.000,"minThresholdKg":10.000,"isLowStock":false}],
                "rawSilkAvailableKg":20.000,"internalWipKg":0.000,"externalWipKg":0.000,"finishedSilkWarpKg":15.000,
                "finishedKoraWarpKg":0.000,"vudaWarpKg":0.000,"wastageToDateKg":[],"unit":"kg","asOf":"2026-10-18T10:00:00Z"}""",
        )
        val vm = StockSummaryViewModel(InventoryRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val d = vm.data.value.value!!
        val rawSilk = d.balances[0]
        assertEquals(BigDecimal("50.000"), rawSilk.minThresholdKg)
        org.junit.jupiter.api.Assertions.assertTrue(rawSilk.isLowStock)

        val finishedSilk = d.balances[1]
        assertEquals(BigDecimal("10.000"), finishedSilk.minThresholdKg)
        org.junit.jupiter.api.Assertions.assertFalse(finishedSilk.isLowStock)
    }
}
