package com.nexora.desktop.viewmodel.money

import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NewAccountViewModelTest {

    @Test
    @DisplayName("FR-FIN-01 NFR-SEC-06 bank account accepts only last four digits")
    fun bankAccount_acceptsOnlyLastFourDigits() = vmTest { server ->
        server.onJson(
            "POST /finance/accounts",
            """{"id":"b7e1","accountName":"SBI Current","accountType":"BANK","bankName":"State Bank of India","maskedAccountNumber":"XXXXXX4821","status":"ACTIVE","balance":200000.00}""",
            201,
        )
        val vm = NewAccountViewModel(FinanceRepository(server.client()), TEST_CLOCK)

        // A full account number pasted in keeps only 4 digits; letters and spaces are dropped.
        vm.setLast4("4821 9933 1234")
        assertEquals("4821", vm.form.value.last4)
        vm.setLast4("48a")
        assertEquals("48", vm.form.value.last4)

        vm.edit { it.copy(name = "SBI Current", bankName = "State Bank of India", openingBalance = "200000.00", openingDate = "01-10-2026") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Enter exactly 4 digits.", vm.status.value.fieldErrors["accountNumberLast4"])
        assertEquals(0, server.count("POST /finance/accounts"))

        vm.setLast4("4821")
        vm.save()
        testScheduler.advanceUntilIdle()
        val body = server.last("POST /finance/accounts").body!!
        assertEquals(
            """{"accountName":"SBI Current","accountType":"BANK","bankName":"State Bank of India","accountNumberLast4":"4821","openingBalance":200000.00,"openingDate":"2026-10-01"}""",
            body,
        )
        assertEquals("XXXXXX4821", vm.status.value.result?.maskedAccountNumber)
    }
}
