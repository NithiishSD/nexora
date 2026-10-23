package com.nexora.desktop.viewmodel.reports

import com.nexora.desktop.repository.ReportRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.json
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProfitAndLossViewModelTest {

    @Test
    @DisplayName("FR-RPT-17 loads P&L and profitability and worker efficiency")
    fun loadsPnlAndProfitabilityAndEfficiency() = vmTest { server ->
        server.on("GET /reports/profit-and-loss") {
            json("""[{"period":"2026-10","revenue":100000.00,"businessExpenses":10000.00,"interestCost":2000.00,"productionCost":60000.00,"profit":28000.00}]""")
        }
        server.on("GET /reports/profitability") {
            json("""[{"key":"SILK_WARP","id":null,"revenue":100000.00,"cost":60000.00,"margin":40000.00,"marginPercent":40.00}]""")
        }
        server.on("GET /reports/worker-efficiency") {
            json("""[{"worker":{"id":"w1","name":"Anitha"},"kgPerHour":5.00,"hours":8.00,"kg":40.000}]""")
        }

        val vm = ProfitAndLossViewModel(ReportRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val pnl = vm.pnl.value.value
        assertNotNull(pnl)
        assertFalse(pnl!!.isEmpty())
        assertEquals("2026-10", pnl[0].period)
        assertEquals(28000.00, pnl[0].profit)

        vm.loadProfitability("2026-10-01", "2026-10-31", "PRODUCT")
        testScheduler.advanceUntilIdle()
        val prof = vm.profitability.value.value
        assertNotNull(prof)
        assertEquals("SILK_WARP", prof!![0].key)
        assertEquals(40.00, prof[0].marginPercent)

        vm.loadEfficiency("2026-10-01", "2026-10-31")
        testScheduler.advanceUntilIdle()
        val eff = vm.efficiency.value.value
        assertNotNull(eff)
        assertEquals("Anitha", eff!![0].worker.name)
        assertEquals(5.00, eff[0].kgPerHour)
    }
}
