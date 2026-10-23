package com.nexora.desktop.viewmodel.loans

import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.LoanRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoanDetailViewModelTest {

    private val loan = """{"id":"l1","loanNumber":"LON-2026-0001","lender":{"id":"a9c1","name":"Canara Bank"},"loanType":"BANK",
        "principalAmount":500000.00,"principalRepaid":150000.00,"outstandingPrincipal":350000.00,"interestPaid":6000.00,
        "startDate":"2026-04-01","status":"ACTIVE","payments":[]}"""

    @Test
    @DisplayName("FR-LOAN-03 repayment above outstanding is blocked")
    fun repaymentAboveOutstanding_isBlocked() = vmTest { server ->
        server.onJson("GET /loans/l1", loan)
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("POST /loans/l1/principal-repayments", loan.replace("\"outstandingPrincipal\":350000.00", "\"outstandingPrincipal\":250000.00"), 201)
        val vm = LoanDetailViewModel(LoanRepository(server.client()), FinanceRepository(server.client()), TEST_CLOCK, "l1")
        testScheduler.advanceUntilIdle()

        vm.openEntry("principal")
        vm.editEntry { it.copy(amount = "350000.01", method = "BANK_TRANSFER", accountId = "b7e1") }
        vm.submitEntry()
        testScheduler.advanceUntilIdle()
        assertEquals("More than the outstanding principal.", vm.entry.value.fieldErrors["amount"])
        assertEquals(0, server.count("POST /loans/l1/principal-repayments"))

        vm.editEntry { it.copy(amount = "100000.00") }
        vm.submitEntry()
        testScheduler.advanceUntilIdle()
        assertEquals(
            """{"amount":100000.00,"financialAccountId":"b7e1","paymentMethod":"BANK_TRANSFER","paymentDate":"2026-10-08"}""",
            server.last("POST /loans/l1/principal-repayments").body,
        )
        assertNotNull(server.last("POST /loans/l1/principal-repayments").headers["Idempotency-Key"])
        // The new outstanding is the server's figure.
        assertEquals("250000.00", vm.data.value.value!!.outstandingPrincipal.toPlainString())
        assertEquals(null, vm.entry.value.kind)
    }

    @Test
    @DisplayName("FR-LOAN-10 loadAdvice fetches loan repayment options")
    fun loadAdvice_fetchesRepaymentOptions() = vmTest { server ->
        server.onJson("GET /loans/l1", loan)
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson(
            "GET /loans/l1/advice",
            """{"loanId":"l1","loanNumber":"LON-2026-0001","outstandingPrincipal":350000.00,"interestRatePercent":1.500,
                "interestDueSuggestion":5250.00,"totalAvailableFunds":100000.00,
                "options":[{"action":"PAY_INTEREST","amount":5250.00,"availableAfter":94750.00,"description":"Pay monthly interest"}]}""",
        )
        val vm = LoanDetailViewModel(LoanRepository(server.client()), FinanceRepository(server.client()), TEST_CLOCK, "l1")
        testScheduler.advanceUntilIdle()

        vm.loadAdvice()
        testScheduler.advanceUntilIdle()

        val advice = vm.advice.value
        assertNotNull(advice)
        assertEquals("5250.00", advice?.interestDueSuggestion?.toPlainString())
        assertEquals(1, advice?.options?.size)
        assertEquals("PAY_INTEREST", advice?.options?.first()?.action)
    }
}
