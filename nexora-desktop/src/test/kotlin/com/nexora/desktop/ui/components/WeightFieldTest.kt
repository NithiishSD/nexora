package com.nexora.desktop.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WeightFieldTest {

    @Test
    @DisplayName("FR-INV-15 fourth decimal is rejected while typing")
    fun fourthDecimal_isRejectedWhileTyping() {
        val typed = listOf("4", "42", "42.", "42.5", "42.50", "42.500", "42.5001")
            .fold("") { current, next -> acceptDecimalInput(next, 3, 9) ?: current }
        assertEquals("42.500", typed)
        assertNull(acceptDecimalInput("0.0001", 3, 9))
        // 9 integer digits is the NUMERIC(12,3) limit.
        assertEquals("999999999.999", acceptDecimalInput("999999999.999", 3, 9))
        assertNull(acceptDecimalInput("1000000000", 3, 9))
    }
}
