package com.nexora.desktop.util

import com.nexora.desktop.di.BUSINESS_ZONE
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters

/** Date helpers: the screen shows `dd-MM-yyyy`, the API uses ISO `yyyy-MM-dd`. */
object Dates {
    // STRICT rejects impossible dates such as 31-02-2026 instead of quietly turning them into 28-02-2026.
    // (STRICT needs "uuuu" = proleptic year; "yyyy" would require an era.)
    private val display: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val compact: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
    private val timestamp: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(BUSINESS_ZONE)
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(BUSINESS_ZONE)

    /** Today in Asia/Kolkata, whatever time zone the computer is set to. */
    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(BUSINESS_ZONE))

    fun format(date: LocalDate?): String = date?.format(display) ?: "—"

    /** Timestamps arrive in UTC and are shown in shop time: `08-10-2026 10:42`. */
    fun formatTimestamp(instant: Instant?): String = instant?.let { timestamp.format(it) } ?: "—"

    fun formatTime(instant: Instant): String = time.format(instant)

    /** An API business date (`2026-10-08`) as `08-10-2026`. */
    fun formatIso(date: String?): String = date?.let { runCatching { format(LocalDate.parse(it)) }.getOrDefault(it) } ?: "—"

    /** An API timestamp (`2026-10-07T10:15:30Z`) in shop time. */
    fun formatIsoTimestamp(value: String?): String =
        value?.let { runCatching { formatTimestamp(Instant.parse(it)) }.getOrDefault(it) } ?: "—"

    /** A date typed by the user, as the API wants it (`yyyy-MM-dd`), or null when it is not a valid date. */
    fun toIso(text: String, clock: Clock): String? = parseInput(text, clock)?.toString()

    /**
     * Parses what the user typed in a date field: `08-10-2026`, `08102026` or `t` for today.
     * Returns null when the text is not a valid date.
     */
    fun parseInput(text: String, clock: Clock): LocalDate? {
        val trimmed = text.trim()
        if (trimmed.equals("t", ignoreCase = true)) return today(clock)
        return listOf(display, compact).firstNotNullOfOrNull { format ->
            try {
                LocalDate.parse(trimmed, format)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /** Monday of the current week: the payroll week runs Monday to Sunday (06 section 11 #12). */
    fun weekStart(clock: Clock): LocalDate = today(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekEnd(clock: Clock): LocalDate = weekStart(clock).plusDays(6)

    fun monthStart(clock: Clock): LocalDate = today(clock).withDayOfMonth(1)

    /** Indian financial year starts on 1 April. */
    fun financialYearStart(clock: Clock): LocalDate {
        val today = today(clock)
        val year = if (today.monthValue >= 4) today.year else today.year - 1
        return LocalDate.of(year, 4, 1)
    }

    fun lastMonthStart(clock: Clock): LocalDate = today(clock).minusMonths(1).withDayOfMonth(1)

    fun lastMonthEnd(clock: Clock): LocalDate = today(clock).withDayOfMonth(1).minusDays(1)
}
