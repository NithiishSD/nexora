package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReceiveSilkViewModelTest {

    @Test
    @DisplayName("FR-PROC-08 receipt exceeded shows error at received field")
    fun receiptExceeded_showsErrorAtReceivedField() = vmTest { server ->
        server.onJson("GET /purchases/8a1b", Fixtures.purchase())
        // Someone else received silk meanwhile; the server refuses the weight.
        server.on("POST /purchases/8a1b/receipts") { problem(409, "PURCHASE_RECEIPT_EXCEEDED", "Only 20.000 kg remain to be received.") }
        val vm = ReceiveSilkViewModel(PurchaseRepository(server.client()), TEST_CLOCK, "8a1b")
        testScheduler.advanceUntilIdle()

        // Defaults from the server's purchase: received = remaining 170 kg, accepted = received, date today.
        assertEquals("170.000", vm.form.value.received)
        assertEquals("170.000", vm.form.value.accepted)
        assertEquals("08-10-2026", vm.form.value.receiptDate)

        vm.setReceived("50.000")
        assertEquals("50.000", vm.form.value.accepted) // accepted follows until typed
        vm.save()
        testScheduler.advanceUntilIdle()

        val status = vm.status.value
        assertEquals("This is more than the remaining weight of the purchase.", status.fieldErrors["receivedWeightKg"])
        assertEquals("PURCHASE_RECEIPT_EXCEEDED", status.error?.code)
        assertNotNull(server.last("POST /purchases/8a1b/receipts").headers["Idempotency-Key"])
        assertEquals(
            """{"receiptDate":"2026-10-08","receivedWeightKg":50.000,"acceptedWeightKg":50.000,"rejectedWeightKg":0.000}""",
            server.last("POST /purchases/8a1b/receipts").body,
        )
    }

    @Test
    @DisplayName("FR-PROC-09 accepted plus rejected above received is caught before sending")
    fun splitAboveReceived_isBlockedLocally() = vmTest { server ->
        server.onJson("GET /purchases/8a1b", Fixtures.purchase())
        val vm = ReceiveSilkViewModel(PurchaseRepository(server.client()), TEST_CLOCK, "8a1b")
        testScheduler.advanceUntilIdle()
        vm.setReceived("10.000")
        vm.setAccepted("9.000")
        vm.setRejected("2.000")
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Accepted + rejected cannot be more than received.", vm.status.value.fieldErrors["acceptedWeightKg"])
        assertEquals(0, server.count("POST /purchases/8a1b/receipts"))
    }
}
