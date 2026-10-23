package com.nexora.desktop.viewmodel.payments

import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.PaymentRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.SettingsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordPaymentViewModelTest {

    private fun viewModel(server: MockServer, settings: SettingsStore = testSettings(), partyId: String? = null): RecordPaymentViewModel {
        val api = server.client()
        return RecordPaymentViewModel(PaymentRepository(api), PartyRepository(api), OrderRepository(api), FinanceRepository(api), settings, TEST_CLOCK, partyId, null)
    }

    @Test
    @DisplayName("NFR-USE-01 default path needs at most five inputs")
    fun defaultPath_needsAtMostFiveInputs() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("GET /customers/5d0c/outstanding", Fixtures.CUSTOMER_OUTSTANDING)
        server.onJson("GET /orders/4c2d", Fixtures.order(status = "CONFIRMED"))
        server.onJson("POST /payments", Fixtures.PAYMENT, 201)
        // This computer last recorded a UPI payment into SBI Current.
        val settings = testSettings().apply { remember("payment.method", "UPI"); remember("payment.account.UPI", "b7e1") }
        val vm = viewModel(server, settings)
        testScheduler.advanceUntilIdle()

        // Defaults fill direction, method, account and date: the user only picks the party and types the amount.
        var inputs = 0
        vm.pickParty(PickOption("5d0c", "Kumar Silks")); inputs++
        vm.edit { it.copy(amount = "45000.00") }; inputs++
        testScheduler.advanceUntilIdle()
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(true, inputs <= 5)
        assertEquals(
            """{"purpose":"CUSTOMER","partyId":"5d0c","amount":45000.00,"paymentMethod":"UPI","financialAccountId":"b7e1","paymentDate":"2026-10-08"}""",
            server.last("POST /payments").body,
        )
        assertNotNull(server.last("POST /payments").headers["Idempotency-Key"])
        assertEquals("PAY-2026-0001", vm.status.value.result?.paymentNumber)
    }

    @Test
    @DisplayName("FR-PAY-12 cash method lists only cash accounts")
    fun cashMethod_listsOnlyCashAccounts() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        val vm = viewModel(server)
        testScheduler.advanceUntilIdle()

        vm.setMethod("CASH")
        assertEquals(listOf("Cash", "Shop cash"), vm.offeredAccounts("CASH").map { it.accountName })
        vm.setMethod("UPI")
        // Bank accounts only, and the closed one is not offered at all.
        assertEquals(listOf("SBI Current"), vm.offeredAccounts("UPI").map { it.accountName })
        assertEquals("b7e1", vm.form.value.accountId) // the only bank account is chosen
        vm.setMethod("CASH")
        assertEquals(null, vm.form.value.accountId) // two cash accounts: the user chooses
    }

    @Test
    @DisplayName("FR-PAY-03 manual allocation above outstanding is blocked")
    fun manualAllocationAboveOutstanding_isBlocked() = vmTest { server ->
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("GET /parties/5d0c", Fixtures.party())
        server.onJson("GET /customers/5d0c/outstanding", Fixtures.CUSTOMER_OUTSTANDING)
        // The order's item still owes 20000.00 according to the server.
        server.onJson("GET /orders/4c2d", Fixtures.order(status = "CONFIRMED").replace("\"outstandingAmount\":0.00,\"paymentStatus\":\"UNPAID\"}", "\"outstandingAmount\":20000.00,\"paymentStatus\":\"PARTIALLY_PAID\"}"))
        val vm = viewModel(server, partyId = "5d0c")
        testScheduler.advanceUntilIdle()

        val item = vm.openItems.value.single()
        assertEquals("ORD-2026-0001 line 1", item.label)
        vm.edit { it.copy(amount = "30000.00", method = "UPI", accountId = "b7e1", manualAllocation = true) }
        vm.setAllocation(item.targetId, "25000.00")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals("More than what is still owed on this item.", vm.status.value.fieldErrors["allocations.${item.targetId}"])
        assertEquals(0, server.count("POST /payments"))
    }
}
