package com.nexora.desktop.viewmodel.chits

import com.nexora.desktop.repository.ChitRepository
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChitDetailViewModelTest {

    private val chit = """{"id":"ch1","chitNumber":"CHT-2026-0001","chitName":"Chit A","totalAmount":500000.00,"monthlyAmount":25000.00,
        "durationMonths":20,"startDate":"2026-04-05","status":"ACTIVE","contributionsCount":8,"totalContributed":200000.00,
        "remainingExpectedContributions":12,"organiserPayments":0.00,"payoutReceived":null,"entries":[]}"""

    @Test
    @DisplayName("FR-CHIT-02 account selector lists cash accounts only")
    fun accountSelector_listsCashAccountsOnly() = vmTest { server ->
        server.onJson("GET /chits/ch1", chit)
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("POST /chits/ch1/contributions", chit.replace("\"contributionsCount\":8", "\"contributionsCount\":9"), 201)
        val vm = ChitDetailViewModel(ChitRepository(server.client()), FinanceRepository(server.client()), TEST_CLOCK, "ch1")
        testScheduler.advanceUntilIdle()

        // Only the two cash accounts; the bank account (and the closed one) are never offered.
        assertEquals(listOf("Cash", "Shop cash"), vm.cashAccounts.value.map { it.accountName })

        // Contribution defaults to the standard monthly amount; the method is always Cash.
        vm.openEntry("contributions")
        assertEquals("25000.00", vm.entry.value.amount)
        assertEquals("CASH", vm.entry.value.method)

        // Even a bank account id put in by hand is refused before sending.
        vm.editEntry { it.copy(accountId = "b7e1", method = "UPI") }
        assertEquals("CASH", vm.entry.value.method)
        vm.submitEntry()
        testScheduler.advanceUntilIdle()
        assertEquals("Chits use cash accounts only.", vm.entry.value.fieldErrors["financialAccountId"])
        assertEquals(0, server.count("POST /chits/ch1/contributions"))

        vm.editEntry { it.copy(accountId = "c1") }
        vm.submitEntry()
        testScheduler.advanceUntilIdle()
        assertEquals(
            """{"amount":25000.00,"financialAccountId":"c1","paymentMethod":"CASH","entryDate":"2026-10-08"}""",
            server.last("POST /chits/ch1/contributions").body,
        )
        assertEquals(9, vm.data.value.value!!.contributionsCount)
    }

    @Test
    @DisplayName("FR-CHIT-10 calculateBid calculates dividend and net contribution")
    fun calculateBid_calculatesDividendAndContribution() = vmTest { server ->
        server.onJson("GET /chits/ch1", chit)
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson(
            "POST /chits/ch1/bids",
            """{"chitId":"ch1","chitNumber":"CHT-2026-0001","month":"2026-10","bidAmount":40000.00,"durationMonths":20,
                "dividendPerMember":2000.00,"standardMonthlyAmount":25000.00,"contributionDue":23000.00}""",
        )
        val vm = ChitDetailViewModel(ChitRepository(server.client()), FinanceRepository(server.client()), TEST_CLOCK, "ch1")
        testScheduler.advanceUntilIdle()

        vm.calculateBid("2026-10", "40000.00")
        testScheduler.advanceUntilIdle()

        val res = vm.bidResult.value
        org.junit.jupiter.api.Assertions.assertNotNull(res)
        assertEquals("2000.00", res?.dividendPerMember?.toPlainString())
        assertEquals("23000.00", res?.contributionDue?.toPlainString())

        vm.clearBidResult()
        assertEquals(null, vm.bidResult.value)
    }
}
