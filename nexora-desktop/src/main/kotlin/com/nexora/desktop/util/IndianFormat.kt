package com.nexora.desktop.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Display formatting only: the values come from the API and are never changed or combined here (NFR-USE-02).
 * Indian grouping puts a comma after the last three digits, then after every two: 12,50,000.
 */
object IndianFormat {

    /** `₹12,50,000.50`; negative values get a leading minus: `-₹500.00`. */
    fun money(value: BigDecimal?): String {
        if (value == null) return "—"
        // The server already sends scale 2; setScale only pads a value like 5 to 5.00 for display.
        val text = groupDecimal(value.abs().setScale(2, RoundingMode.HALF_UP))
        return if (value.signum() < 0) "-₹$text" else "₹$text"
    }

    /** `1,250.500 kg` (always three decimals). */
    fun weight(value: BigDecimal?): String {
        if (value == null) return "—"
        val text = groupDecimal(value.abs().setScale(3, RoundingMode.HALF_UP))
        return if (value.signum() < 0) "-$text kg" else "$text kg"
    }

    /** `₹850.00 /kg`, `₹120.00 /hour`. */
    fun rate(value: BigDecimal?, unit: String): String = if (value == null) "—" else "${money(value)} /$unit"

    /** Hours of rolling work, shown with two decimals: `8.00 h`. */
    fun hours(value: BigDecimal?): String = if (value == null) "—" else "${value.setScale(2, RoundingMode.HALF_UP).toPlainString()} h"

    private fun groupDecimal(value: BigDecimal): String {
        val plain = value.toPlainString()
        val dot = plain.indexOf('.')
        val integerPart = if (dot >= 0) plain.substring(0, dot) else plain
        val fraction = if (dot >= 0) plain.substring(dot) else ""
        return groupIndian(integerPart) + fraction
    }

    /** "1250000" -> "12,50,000". */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        // Walk the remaining digits from the right in pairs.
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$lastThree"
    }
}
