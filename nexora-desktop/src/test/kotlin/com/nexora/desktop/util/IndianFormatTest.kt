package com.nexora.desktop.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class IndianFormatTest {

    @Test
    @DisplayName("NFR-USE-03 money uses rupee sign and indian grouping")
    fun money_usesRupeeSignAndIndianGrouping() {
        assertEquals("₹12,50,000.50", IndianFormat.money(BigDecimal("1250000.50")))
        assertEquals("₹999.00", IndianFormat.money(BigDecimal("999.00")))
        assertEquals("₹1,00,00,000.00", IndianFormat.money(BigDecimal("10000000.00")))
        assertEquals("-₹4,500.75", IndianFormat.money(BigDecimal("-4500.75")))
        assertEquals("₹850.00 /kg", IndianFormat.rate(BigDecimal("850.00"), "kg"))
    }

    @Test
    @DisplayName("NFR-USE-03 weight shows three decimals and kg")
    fun weight_showsThreeDecimalsAndKg() {
        assertEquals("1,250.500 kg", IndianFormat.weight(BigDecimal("1250.5")))
        assertEquals("0.000 kg", IndianFormat.weight(BigDecimal("0.000")))
        assertEquals("12,34,567.125 kg", IndianFormat.weight(BigDecimal("1234567.125")))
    }

    @Test
    @DisplayName("NFR-USE-03 dates show dd-MM-yyyy and timestamps in Asia/Kolkata")
    fun dates_showDayMonthYearAndShopTime() {
        val clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneOffset.UTC)
        assertEquals("08-10-2026", Dates.format(LocalDate.of(2026, 10, 8)))
        // 20:00 UTC on the 7th is already the 8th in India (UTC+05:30).
        assertEquals(LocalDate.of(2026, 10, 8), Dates.today(clock))
        assertEquals("08-10-2026 01:30", Dates.formatTimestamp(Instant.parse("2026-10-07T20:00:00Z")))
        assertEquals(LocalDate.of(2026, 10, 8), Dates.parseInput("t", clock))
        assertEquals(LocalDate.of(2026, 9, 1), Dates.parseInput("01092026", clock))
        assertEquals(null, Dates.parseInput("31-02-2026", clock))
    }
}
