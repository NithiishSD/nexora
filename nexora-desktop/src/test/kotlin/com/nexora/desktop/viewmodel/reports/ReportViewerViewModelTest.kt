package com.nexora.desktop.viewmodel.reports

import com.nexora.desktop.repository.ReportRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.json
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.reports.cell
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReportViewerViewModelTest {

    @Test
    @DisplayName("FR-RPT-16 export csv uses current filters")
    fun exportCsv_usesCurrentFilters() = vmTest { server ->
        server.on("GET /reports/purchases") { request ->
            if (request.url.parameters["format"] == "csv") {
                respond("supplier,orderedKg\nRamesh Silks,250.000\n", headers = headersOf(HttpHeaders.ContentType, "text/csv"))
            } else {
                json("""{"rows":[{"supplier":{"id":"1f2e","name":"Ramesh Silks"},"productType":"RAW_SILK","orderedKg":250.000,"orderedValue":200000.00}],"totals":{"orderedValue":200000.00}}""")
            }
        }
        val vm = ReportViewerViewModel(ReportRepository(server.client()), TEST_CLOCK, "purchases")
        testScheduler.advanceUntilIdle()
        // Current month by default.
        assertEquals(mapOf("from" to "2026-10-01", "to" to "2026-10-08"), server.last("GET /reports/purchases").query)

        vm.setFilter("supplierId", "1f2e")
        vm.setFilter("productType", "RAW_SILK")
        testScheduler.advanceUntilIdle()
        val csv = vm.csv().getOrNull()!!

        // The CSV request carries exactly the filters on screen plus format=csv.
        assertEquals(
            mapOf("from" to "2026-10-01", "to" to "2026-10-08", "supplierId" to "1f2e", "productType" to "RAW_SILK", "format" to "csv"),
            server.last("GET /reports/purchases").query,
        )
        assertEquals("supplier,orderedKg\nRamesh Silks,250.000\n", csv.decodeToString())
        assertEquals("purchases-2026-10-01-2026-10-08.csv", vm.csvName())
    }

    @Test
    @DisplayName("NFR-USE-03 report values are formatted not recomputed")
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun reportCells_areFormattedOnly() {
        assertEquals("₹2,00,000.00", cell("orderedValue", JsonUnquotedLiteral("200000.00")))
        assertEquals("1,250.500 kg", cell("orderedKg", JsonUnquotedLiteral("1250.500")))
        assertEquals("12", cell("unpaidOrders", JsonUnquotedLiteral("12")))
        assertEquals("08-10-2026", cell("dueDate", JsonPrimitive("2026-10-08")))
        assertEquals("Raw silk", cell("productType", JsonPrimitive("RAW_SILK")))
    }
}
