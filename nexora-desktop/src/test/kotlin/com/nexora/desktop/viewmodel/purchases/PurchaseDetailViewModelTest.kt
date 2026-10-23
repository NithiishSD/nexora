package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.api.ApiJson
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PurchaseDetailViewModelTest {

    private fun purchase(json: String) = ApiJson.decodeFromString(PurchaseResponse.serializer(), json)

    @Test
    @DisplayName("FR-PROC-12 actions enabled by status")
    fun actionsEnabledByStatus() = vmTest { server ->
        server.onJson("GET /purchases/8a1b", Fixtures.purchase())
        val vm = PurchaseDetailViewModel(PurchaseRepository(server.client()), TEST_CLOCK, "8a1b")
        testScheduler.advanceUntilIdle()

        // Partly received, accepted 80 kg, nothing paid: receive, return and pay; no cancel (it has a receipt).
        assertEquals(PurchaseActions(receive = true, returnToSupplier = true, cancel = false, pay = true), vm.actionsFor(vm.data.value.value!!))

        // Fresh order: can be received and cancelled, nothing to return yet.
        val ordered = purchase(Fixtures.purchase(status = "ORDERED", received = "0.000", accepted = "0.000", remaining = "250.000", outstanding = "0.00"))
        assertEquals(PurchaseActions(receive = true, returnToSupplier = false, cancel = true, pay = false), vm.actionsFor(ordered))

        // Fully received and everything returned already: no receive, no return.
        val done = purchase(Fixtures.purchase(status = "FULLY_RECEIVED", received = "250.000", accepted = "250.000", returned = "250.000", remaining = "0.000"))
        assertEquals(PurchaseActions(receive = false, returnToSupplier = false, cancel = false, pay = true), vm.actionsFor(done))

        // Ordered but with a payment allocated: cancel is not offered (FR-PROC-12).
        val paid = purchase(Fixtures.purchase(status = "ORDERED", received = "0.000", accepted = "0.000", allocated = "5000.00"))
        assertEquals(false, vm.actionsFor(paid).cancel)

        // Cancelled: nothing at all except what is already there.
        val cancelled = purchase(Fixtures.purchase(status = "CANCELLED", received = "0.000", accepted = "0.000", outstanding = "0.00"))
        assertEquals(PurchaseActions(receive = false, returnToSupplier = false, cancel = false, pay = false), vm.actionsFor(cancelled))
    }
}
