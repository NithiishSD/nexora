package com.nexora.desktop.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BigDecimalSerializerTest {

    @Serializable
    private data class Amount(val amount: Decimal, val weightKg: Decimal?)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    @DisplayName("NFR-USE-02 NFR-DATA-01 round trip 12500000 50 stays exact")
    fun roundTrip_12500000_50_staysExact() {
        val parsed = json.decodeFromString<Amount>("""{"amount":12500000.50,"weightKg":1250.500}""")

        // Exact text and scale are kept: 12500000.50, not 1.25000005E7 and not 12500000.5.
        assertEquals(BigDecimal("12500000.50"), parsed.amount)
        assertEquals(2, parsed.amount.scale())
        assertEquals(BigDecimal("1250.500"), parsed.weightKg)
        // Written back as JSON numbers with the same digits.
        assertEquals("""{"amount":12500000.50,"weightKg":1250.500}""", json.encodeToString(Amount.serializer(), parsed))
    }

    @Test
    @DisplayName("NFR-DATA-01 a value a double cannot hold exactly stays exact")
    fun valueBeyondDoublePrecision_staysExact() {
        val parsed = json.decodeFromString<Amount>("""{"amount":999999999999.99}""")
        assertEquals("999999999999.99", parsed.amount.toPlainString())
        assertEquals(null, parsed.weightKg)
    }
}
