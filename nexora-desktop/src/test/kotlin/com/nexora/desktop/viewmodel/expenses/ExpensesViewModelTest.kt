package com.nexora.desktop.viewmodel.expenses

import com.nexora.desktop.repository.ExpenseRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExpensesViewModelTest {

    @Test
    @DisplayName("FR-FIN-12 personal drawings shown separately")
    fun personalDrawingsShownSeparately() = vmTest { server ->
        server.onJson("GET /expenses", Fixtures.page(""))
        server.onJson(
            "GET /expenses/summary",
            """{"from":"2026-10-01","to":"2026-10-08","businessTotal":5000.00,"personalDrawingsTotal":20000.00,
                "byCategory":[{"category":"TRANSPORT","total":3500.00},{"category":"RENT","total":1500.00}],
                "byMonth":[{"month":"2026-10","businessTotal":5000.00,"personalTotal":20000.00}]}""",
        )
        val vm = ExpensesViewModel(ExpenseRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        // Current month by default, for both the list and the summary.
        assertEquals(mapOf("from" to "2026-10-01", "to" to "2026-10-08"), server.last("GET /expenses/summary").query)
        assertEquals("2026-10-01", server.last("GET /expenses").query["from"])
        val summary = vm.summary.value!!
        // Two separate figures from the server; there is no combined "all spending" number anywhere.
        assertEquals("5000.00", summary.businessTotal.toPlainString())
        assertEquals("20000.00", summary.personalDrawingsTotal.toPlainString())
        assertEquals(listOf("TRANSPORT", "RENT"), summary.byCategory.map { it.category })
    }
}
