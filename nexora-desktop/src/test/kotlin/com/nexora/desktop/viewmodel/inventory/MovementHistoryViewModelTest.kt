package com.nexora.desktop.viewmodel.inventory

import com.nexora.desktop.repository.InventoryRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MovementHistoryViewModelTest {

    @Test
    @DisplayName("FR-INV-01 offers no create action")
    fun offersNoCreateAction() = vmTest { server ->
        server.onJson(
            "GET /inventory/movements",
            Fixtures.page("""{"id":"m1","movementNumber":"MOV-2026-0012","movementType":"RECEIPT","productType":"RAW_SILK","quantityKg":95.000,
                "fromLocation":"SUPPLIER","toLocation":"RAW_STOCK","movementDate":"2026-09-12","referenceType":"MATERIAL_RECEIPT","referenceNumber":"RCT-2026-0004"}"""),
        )
        val vm = MovementHistoryViewModel(InventoryRepository(server.client()), TEST_CLOCK, mapOf("productType" to "RAW_SILK"))
        testScheduler.advanceUntilIdle()
        vm.setFilter("movementType", "RECEIPT")
        testScheduler.advanceUntilIdle()

        // Only reads were sent, with the filters as query parameters.
        assertTrue(server.requests.all { it.method == "GET" })
        assertEquals("RECEIPT", server.last("GET /inventory/movements").query["movementType"])
        assertEquals("RAW_SILK", server.last("GET /inventory/movements").query["productType"])
        // Neither the view-model nor the repository has anything that writes a movement.
        val writers = Regex("create|record|add|edit|update|delete|save", RegexOption.IGNORE_CASE)
        val vmMethods = MovementHistoryViewModel::class.java.declaredMethods.map { it.name }
        assertTrue(vmMethods.none { writers.containsMatchIn(it) }, "write method on view-model: $vmMethods")
        val repoMethods = InventoryRepository::class.java.declaredMethods.map { it.name }.filter { "ovement" in it }
        assertEquals(setOf("movements", "movementsCsv"), repoMethods.filter { !it.contains('$') }.toSet())
    }
}
