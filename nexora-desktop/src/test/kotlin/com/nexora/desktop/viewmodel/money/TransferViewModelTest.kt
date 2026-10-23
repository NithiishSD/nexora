package com.nexora.desktop.viewmodel.money

import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TransferViewModelTest {

    @Test
    @DisplayName("FR-FIN-09 same account is blocked")
    fun sameAccount_isBlocked() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson(
            "POST /finance/transfers",
            """{"id":"t1","transferNumber":"TRF-2026-0001","sourceAccount":{"id":"b7e1","accountName":"SBI Current"},"destinationAccount":{"id":"c1","accountName":"Cash"},"amount":20000.00,"transferDate":"2026-10-08"}""",
            201,
        )
        val vm = TransferViewModel(FinanceRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()
        // Closed accounts are not offered.
        assertEquals(listOf("c1", "c2", "b7e1"), vm.accounts.value.map { it.id })

        vm.edit { it.copy(fromId = "b7e1", toId = "b7e1", amount = "20000.00") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Choose two different accounts.", vm.status.value.fieldErrors["destinationAccountId"])
        assertEquals(0, server.count("POST /finance/transfers"))

        vm.edit { it.copy(toId = "c1") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(
            """{"sourceAccountId":"b7e1","destinationAccountId":"c1","amount":20000.00,"transferDate":"2026-10-08"}""",
            server.last("POST /finance/transfers").body,
        )
        assertNotNull(server.last("POST /finance/transfers").headers["Idempotency-Key"])
    }
}
