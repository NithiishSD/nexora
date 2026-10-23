package com.nexora.desktop.viewmodel.orders

import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.PickOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OrderFormViewModelTest {

    @Test
    @DisplayName("FR-ORD-02 NFR-USE-02 amount shown only from api response")
    fun amountShownOnlyFromApiResponse() = vmTest { server ->
        server.onJson("POST /orders", Fixtures.order(), 201)
        val vm = OrderFormViewModel(OrderRepository(server.client()), TEST_CLOCK, null)

        vm.edit { it.copy(customer = PickOption("5d0c", "Kumar Silks"), requiredDate = "20-10-2026") }
        vm.updateItem(0) { it.copy(productType = "KORA_WARP", weight = "100.000", rate = "850.00") }

        // 100 x 850 is never multiplied on the client: the line and the total wait for the server.
        assertEquals("calculated on save", vm.amountText(vm.form.value.items[0]))
        assertEquals("calculated on save", vm.totalText(vm.form.value))

        vm.save()
        testScheduler.advanceUntilIdle()

        val body = server.last("POST /orders").body!!
        assertEquals(
            """{"customerId":"5d0c","orderDate":"2026-10-08","requiredDate":"2026-10-20","items":[{"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00}]}""",
            body,
        )
        assertFalse(body.contains("amount"))
        val saved = vm.status.value.result!!
        assertEquals("85000.00", saved.items.single().amount.toPlainString())
        assertEquals("85000.00", saved.totalAmount.toPlainString())
    }

    @Test
    @DisplayName("FR-ORD-06 editing a loaded line drops its server amount until saved")
    fun editedLine_showsCalculatedOnSave() = vmTest { server ->
        server.onJson("GET /orders/4c2d", Fixtures.order())
        val vm = OrderFormViewModel(OrderRepository(server.client()), TEST_CLOCK, "4c2d")
        testScheduler.advanceUntilIdle()
        assertEquals("₹85,000.00", vm.amountText(vm.form.value.items[0]))
        vm.updateItem(0) { it.copy(weight = "110.000") }
        assertEquals("calculated on save", vm.amountText(vm.form.value.items[0]))
    }
}
