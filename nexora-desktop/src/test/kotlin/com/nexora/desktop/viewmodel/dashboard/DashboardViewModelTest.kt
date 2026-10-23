package com.nexora.desktop.viewmodel.dashboard

import com.nexora.desktop.repository.DashboardRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DashboardViewModelTest {

    @Test
    @DisplayName("FR-RPT-01 FR-RPT-02 NFR-USE-02 shows api figures without computing")
    fun showsApiFiguresWithoutComputing() = vmTest { server ->
        server.onJson("GET /dashboard/summary", Fixtures.DASHBOARD)
        val vm = DashboardViewModel(DashboardRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val d = vm.data.value.value!!
        // Total available is the server's 330000.00, not cash + listed banks (280000.00).
        assertEquals(BigDecimal("330000.00"), d.totalAvailable)
        assertEquals(BigDecimal("80000.00"), d.cash)
        // Obligations stay separate figures; none is subtracted from money available (FR-RPT-02).
        assertEquals(BigDecimal("500000.00"), d.customerReceivables)
        assertEquals(BigDecimal("310000.00"), d.supplierPayables)
        assertEquals(BigDecimal("55.500"), d.stock.finishedKoraWarpKg)
        assertEquals(3, d.upcomingSupplierDues.single().overdueDays)
        assertEquals(1, server.count("GET /dashboard/summary"))

        // F5 / auto refresh just asks the server again.
        vm.reload()
        testScheduler.advanceUntilIdle()
        assertEquals(2, server.count("GET /dashboard/summary"))
    }
}
