package com.nexora.desktop.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyFieldTest {

    /** Typing one character after another, as the field receives it. */
    private fun type(vararg steps: String): String = steps.fold("") { current, next -> acceptDecimalInput(next, 2, 12) ?: current }

    @Test
    @DisplayName("FR-PAY-13 third decimal is rejected while typing")
    fun thirdDecimal_isRejectedWhileTyping() {
        assertEquals("45000.50", type("4", "45", "450", "4500", "45000", "45000.", "45000.5", "45000.50", "45000.505"))
        assertNull(acceptDecimalInput("10.999", 2, 12))
        // Letters, minus signs and a second point are refused too.
        assertNull(acceptDecimalInput("12a", 2, 12))
        assertNull(acceptDecimalInput("-5", 2, 12))
        assertNull(acceptDecimalInput("1.2.3", 2, 12))
        // 12 integer digits is the NUMERIC(14,2) limit.
        assertEquals("999999999999.99", acceptDecimalInput("999999999999.99", 2, 12))
        assertNull(acceptDecimalInput("1000000000000", 2, 12))
    }

    @Test
    @DisplayName("NFR-USE-03 pasted grouping commas are ignored and the value is exact")
    fun pastedCommas_areIgnored() {
        assertEquals("1250000.50", acceptDecimalInput("12,50,000.50", 2, 12))
        assertEquals(BigDecimal("1250000.50"), "1250000.50".toDecimalOrNull())
        assertEquals("12,50,000.5", groupedForDisplay("1250000.5"))
        assertNull("".toDecimalOrNull())
    }
}
