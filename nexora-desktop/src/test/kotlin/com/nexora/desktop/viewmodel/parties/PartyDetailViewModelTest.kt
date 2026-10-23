package com.nexora.desktop.viewmodel.parties

import com.nexora.desktop.repository.LoanRepository
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PartyDetailViewModelTest {

    @Test
    @DisplayName("FR-PARTY-06 tabs shown per role")
    fun tabsShownPerRole() = vmTest { server ->
        server.onJson("GET /parties/5d0c", Fixtures.party(roles = "\"CUSTOMER\",\"SUPPLIER\""))
        server.onJson("GET /parties/w1", Fixtures.party(id = "w1", roles = "\"WORKER\""))
        val vm = PartyDetailViewModel(PartyRepository(server.client()), LoanRepository(server.client()), TEST_CLOCK, "5d0c")
        val worker = PartyDetailViewModel(PartyRepository(server.client()), LoanRepository(server.client()), TEST_CLOCK, "w1")
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(PartyTab.OUTSTANDING, PartyTab.STATEMENT, PartyTab.PAYABLES, PartyTab.ACTIVITY),
            vm.visibleTabs(vm.data.value.value!!),
        )
        assertEquals(listOf(PartyTab.WORKER_PAYABLE, PartyTab.ACTIVITY), worker.visibleTabs(worker.data.value.value!!))

        // Opening the worker tab asks for the current payroll week (Monday-Sunday) by default.
        server.onJson("GET /workers/w1/payable", """{"worker":{"id":"w1","name":"Selvi"},"workValue":8700.00,"paid":5500.00,"pending":3200.00,"records":[]}""")
        worker.select(PartyTab.WORKER_PAYABLE)
        testScheduler.advanceUntilIdle()
        assertEquals(mapOf("from" to "2026-10-05", "to" to "2026-10-11"), server.last("GET /workers/w1/payable").query)
        assertEquals("3200.00", worker.tabs.value.worker.value!!.pending.toPlainString())
    }
}
