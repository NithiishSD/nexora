package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.PickOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PurchaseFormViewModelTest {

    @Test
    @DisplayName("FR-PROC-04 cash purchase forces credit days to zero")
    fun cashPurchase_forcesCreditDaysToZero() = vmTest { server ->
        server.onJson("GET /parties/1f2e", Fixtures.party(id = "1f2e", roles = "\"SUPPLIER\"").replace("\"defaultCreditDays\":null", "\"defaultCreditDays\":10"))
        server.onJson("POST /purchases", Fixtures.purchase(status = "ORDERED"), 201)
        val vm = PurchaseFormViewModel(PurchaseRepository(server.client()), PartyRepository(server.client()), TEST_CLOCK)

        vm.pickSupplier(PickOption("1f2e", "Ramesh Silks"))
        testScheduler.advanceUntilIdle()
        // Credit purchase: the supplier's default credit days are filled in.
        assertEquals("10", vm.form.value.creditDays)

        vm.setPaymentType("CASH")
        assertEquals("0", vm.form.value.creditDays)
        vm.edit { it.copy(orderedWeightKg = "250.000", ratePerKg = "800.00") }
        vm.save()
        testScheduler.advanceUntilIdle()

        // Date defaults to today (Asia/Kolkata); credit days 0 for cash; no value or due date is sent.
        assertEquals(
            """{"supplierId":"1f2e","productType":"RAW_SILK","purchaseDate":"2026-10-08","orderedWeightKg":250.000,"ratePerKg":800.00,"paymentType":"CASH","creditDays":0}""",
            server.last("POST /purchases").body,
        )
        assertEquals("PUR-2026-0001", vm.status.value.result?.purchaseNumber)
    }

    @Test
    @DisplayName("FR-PROC-20 raw silk low stock is detected when inventory has deficit")
    fun rawSilkLowStock_isDetectedWhenInventoryHasDeficit() = vmTest { server ->
        server.onJson(
            "GET /inventory/low-stock",
            """[{"productType":"RAW_SILK","location":"RAW_STOCK","currentQuantityKg":15.000,"minThresholdKg":50.000,"deficitKg":35.000}]""",
        )
        val vm = PurchaseFormViewModel(
            PurchaseRepository(server.client()),
            PartyRepository(server.client()),
            TEST_CLOCK,
            com.nexora.desktop.repository.InventoryRepository(server.client()),
        )
        testScheduler.advanceUntilIdle()

        val alert = vm.rawSilkLowStock.value
        org.junit.jupiter.api.Assertions.assertNotNull(alert)
        assertEquals("RAW_SILK", alert?.productType)
        assertEquals(java.math.BigDecimal("35.000"), alert?.deficitKg)
    }
}
