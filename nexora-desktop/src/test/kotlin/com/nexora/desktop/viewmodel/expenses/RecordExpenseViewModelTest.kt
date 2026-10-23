package com.nexora.desktop.viewmodel.expenses

import com.nexora.desktop.repository.ExpenseRepository
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordExpenseViewModelTest {

    @Test
    @DisplayName("NFR-USE-01 FR-FIN-11 default path needs at most five inputs")
    fun defaultPath_needsAtMostFiveInputs() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson(
            "POST /expenses",
            """{"id":"e1","expenseNumber":"EXP-2026-0001","category":"TRANSPORT","expenseKind":"BUSINESS","recurrence":"ONE_TIME","amount":1500.00,
                "expenseDate":"2026-10-08","account":{"id":"c1","accountName":"Cash","accountType":"CASH"},"paymentMethod":"CASH","description":"Lorry hire","status":"RECORDED"}""",
            201,
        )
        // Last time this computer recorded a transport expense from the Cash account.
        val settings = testSettings().apply { remember("expense.category", "TRANSPORT"); remember("expense.account", "c1") }
        val vm = RecordExpenseViewModel(ExpenseRepository(server.client()), FinanceRepository(server.client()), settings, TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        // Category, account, method (Cash for a cash account), date, kind and recurrence are already filled.
        var inputs = 0
        vm.edit { it.copy(amount = "1500.00") }; inputs++
        vm.edit { it.copy(description = "Lorry hire") }; inputs++
        vm.save()
        testScheduler.advanceUntilIdle()

        assertTrue(inputs <= 5)
        assertEquals(
            """{"category":"TRANSPORT","expenseKind":"BUSINESS","recurrence":"ONE_TIME","amount":1500.00,"expenseDate":"2026-10-08","financialAccountId":"c1","paymentMethod":"CASH","description":"Lorry hire"}""",
            server.last("POST /expenses").body,
        )
        assertNotNull(server.last("POST /expenses").headers["Idempotency-Key"])
        assertEquals("EXP-2026-0001", vm.status.value.result?.expenseNumber)
    }

    @Test
    @DisplayName("FR-PAY-12 a bank account offers only bank methods")
    fun bankAccount_offersBankMethods() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        val vm = RecordExpenseViewModel(ExpenseRepository(server.client()), FinanceRepository(server.client()), testSettings(), TEST_CLOCK)
        testScheduler.advanceUntilIdle()
        vm.setAccount("b7e1")
        assertEquals(listOf("UPI", "BANK_TRANSFER", "CHEQUE"), vm.methodsForAccount("b7e1"))
        assertEquals("UPI", vm.form.value.method)
        vm.setAccount("c1")
        assertEquals("CASH", vm.form.value.method)
    }

    @Test
    @DisplayName("FR-EXP-08 loads dynamic expense categories")
    fun categories_loadsDynamicCategories() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson(
            "GET /expense-categories",
            """[{"id":"cat1","name":"TRANSPORT","active":true,"createdAt":"2026-10-01T00:00:00Z"},
               {"id":"cat2","name":"MARKETING","active":true,"createdAt":"2026-10-01T00:00:00Z"}]""",
        )
        val vm = RecordExpenseViewModel(ExpenseRepository(server.client()), FinanceRepository(server.client()), testSettings(), TEST_CLOCK)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("TRANSPORT", "MARKETING"), vm.categories.value)
    }
}
