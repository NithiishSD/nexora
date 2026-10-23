package com.nexora.desktop.viewmodel.money

import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AccountsViewModelTest {

    @Test
    @DisplayName("FR-FIN-16 close enabled only at zero balance")
    fun closeEnabledOnlyAtZeroBalance() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("GET /finance/cash-position", """{"cash":[],"banks":[],"totalCash":80000.00,"totalBank":200000.00,"totalAvailable":280000.00}""")
        val vm = AccountsViewModel(FinanceRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val closable = vm.data.value.value!!.accounts.filter(vm::canClose).map { it.accountName }
        // Cash (80000.00) and SBI (200000.00) have money; Old Canara is 0.00 but already closed.
        assertEquals(listOf("Shop cash"), closable)
        assertEquals("280000.00", vm.position.value!!.totalAvailable.toPlainString())
    }
}
