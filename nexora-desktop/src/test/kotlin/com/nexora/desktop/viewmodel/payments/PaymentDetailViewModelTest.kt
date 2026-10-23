package com.nexora.desktop.viewmodel.payments

import com.nexora.desktop.repository.PaymentRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PaymentDetailViewModelTest {

    @Test
    @DisplayName("FR-PAY-15 reverse requires reason")
    fun reverse_requiresReason() = vmTest { server ->
        server.onJson("GET /payments/c1d2", Fixtures.PAYMENT)
        server.onJson(
            "POST /payments/c1d2/reverse",
            Fixtures.PAYMENT.replace("\"status\":\"RECORDED\"", "\"status\":\"REVERSED\"").replace("\"reversalReason\":null", "\"reversalReason\":\"Entered twice\""),
        )
        val vm = PaymentDetailViewModel(PaymentRepository(server.client()), TEST_CLOCK, "c1d2")
        testScheduler.advanceUntilIdle()

        vm.reverse("   ")
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.action.value.error?.fieldErrors?.get("reason"))
        assertEquals(0, server.count("POST /payments/c1d2/reverse"))

        vm.reverse("Entered twice")
        testScheduler.advanceUntilIdle()
        assertEquals("""{"reason":"Entered twice"}""", server.last("POST /payments/c1d2/reverse").body)
        assertNotNull(server.last("POST /payments/c1d2/reverse").headers["Idempotency-Key"])
        assertEquals("REVERSED", vm.data.value.value!!.status)
        assertEquals(false, vm.canReverse(vm.data.value.value!!))
    }

    @Test
    @DisplayName("FR-PAY-25 clear cheque sends clearance request")
    fun clearCheque_sendsClearanceRequest() = vmTest { server ->
        val pendingCheque = Fixtures.PAYMENT
            .replace("\"paymentMethod\":\"UPI\"", "\"paymentMethod\":\"CHEQUE\"")
            .replace("\"status\":\"RECORDED\"", "\"status\":\"PENDING\"")
        val clearedCheque = pendingCheque
            .replace("\"status\":\"PENDING\"", "\"status\":\"RECORDED\"")

        server.onJson("GET /payments/chq1", pendingCheque)
        server.onJson("POST /payments/chq1/cheque-clearance", clearedCheque)

        val vm = PaymentDetailViewModel(PaymentRepository(server.client()), TEST_CLOCK, "chq1")
        testScheduler.advanceUntilIdle()

        val initial = vm.data.value.value!!
        assertEquals(true, vm.canClearCheque(initial))
        assertEquals(true, vm.canBounceCheque(initial))

        vm.clearCheque()
        testScheduler.advanceUntilIdle()

        assertEquals(1, server.count("POST /payments/chq1/cheque-clearance"))
        assertEquals("RECORDED", vm.data.value.value!!.status)
        assertEquals(false, vm.canClearCheque(vm.data.value.value!!))
    }

    @Test
    @DisplayName("FR-PAY-25 bounce cheque requires notes")
    fun bounceCheque_requiresNotes() = vmTest { server ->
        val pendingCheque = Fixtures.PAYMENT
            .replace("\"paymentMethod\":\"UPI\"", "\"paymentMethod\":\"CHEQUE\"")
            .replace("\"status\":\"RECORDED\"", "\"status\":\"PENDING\"")
        val bouncedCheque = pendingCheque
            .replace("\"status\":\"PENDING\"", "\"status\":\"BOUNCED\"")
            .replace("\"reversalReason\":null", "\"reversalReason\":\"Signature mismatch\"")

        server.onJson("GET /payments/chq2", pendingCheque)
        server.onJson("POST /payments/chq2/cheque-bounce", bouncedCheque)

        val vm = PaymentDetailViewModel(PaymentRepository(server.client()), TEST_CLOCK, "chq2")
        testScheduler.advanceUntilIdle()

        vm.bounceCheque(notes = "   ")
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.action.value.error?.fieldErrors?.get("notes"))
        assertEquals(0, server.count("POST /payments/chq2/cheque-bounce"))

        vm.bounceCheque(notes = "Signature mismatch")
        testScheduler.advanceUntilIdle()
        assertEquals(1, server.count("POST /payments/chq2/cheque-bounce"))
        assertEquals("BOUNCED", vm.data.value.value!!.status)
        assertEquals(false, vm.canBounceCheque(vm.data.value.value!!))
    }

    @Test
    @DisplayName("E-PAY-13 refund customer advance sends refund request with idempotency key")
    fun refundAdvance_sendsRefundWithIdempotencyKey() = vmTest { server ->
        val advancePayment = Fixtures.PAYMENT
            .replace("\"unallocatedAmount\":0.00", "\"unallocatedAmount\":15000.00")

        server.onJson("GET /payments/adv1", advancePayment)
        server.onJson("POST /payments/adv1/refunds", advancePayment.replace("\"unallocatedAmount\":15000.00", "\"unallocatedAmount\":0.00"))

        val vm = PaymentDetailViewModel(PaymentRepository(server.client()), TEST_CLOCK, "adv1")
        testScheduler.advanceUntilIdle()

        assertEquals(true, vm.canRefund(vm.data.value.value!!))

        vm.refundAdvance(notes = "Excess advance refunded")
        testScheduler.advanceUntilIdle()

        assertEquals(1, server.count("POST /payments/adv1/refunds"))
        assertNotNull(server.last("POST /payments/adv1/refunds").headers["Idempotency-Key"])
    }
}
