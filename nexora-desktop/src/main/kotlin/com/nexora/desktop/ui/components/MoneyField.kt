package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.util.s
import java.math.BigDecimal

/**
 * Filters what the user types into a money or weight field (06 section 3.6):
 * digits and one decimal point only, at most [maxDecimals] decimals and [maxIntegerDigits] digits before the point
 * (the NUMERIC(14,2) / NUMERIC(12,3) columns). Grouping commas pasted from elsewhere are ignored.
 *
 * @return the accepted text, or null to reject the keystroke (the field keeps its previous text).
 */
fun acceptDecimalInput(typed: String, maxDecimals: Int, maxIntegerDigits: Int): String? {
    val text = typed.replace(",", "").trim()
    if (text.isEmpty()) return ""
    if (!text.all { it.isDigit() || it == '.' } || text.count { it == '.' } > 1) return null
    val integerPart = text.substringBefore('.')
    val fraction = if ('.' in text) text.substringAfter('.') else ""
    if (integerPart.length > maxIntegerDigits || fraction.length > maxDecimals) return null
    return text
}

/** The typed text as an exact decimal, or null when empty or incomplete (such as "."). */
fun String.toDecimalOrNull(): BigDecimal? = replace(",", "").trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/** Shows Indian grouping while the field is not being edited: 1250000.5 -> 12,50,000.5. */
internal fun groupedForDisplay(text: String): String {
    if (text.isEmpty() || text.toDecimalOrNull() == null) return text
    val integerPart = text.substringBefore('.').ifEmpty { "0" }
    val rest = if ('.' in text) "." + text.substringAfter('.') else ""
    return IndianFormat.groupIndian(integerPart) + rest
}

/**
 * Text field for rupees: `₹` prefix, max 2 decimals and 12 integer digits, exposes the text; view-models parse it
 * with [toDecimalOrNull] so the value is a BigDecimal end to end.
 */
@Composable
fun MoneyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) = DecimalField(label, value, onValueChange, 2, 12, s("unit.rupee"), null, modifier, error, enabled, tag)

@Composable
internal fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxDecimals: Int,
    maxIntegerDigits: Int,
    prefix: String?,
    suffix: String?,
    modifier: Modifier,
    error: String?,
    enabled: Boolean,
    tag: String?,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = if (focused) value else groupedForDisplay(value),
        onValueChange = { typed -> acceptDecimalInput(typed, maxDecimals, maxIntegerDigits)?.let(onValueChange) },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { FieldError(it) } },
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.let { if (tag != null) it.testTag(tag) else it },
    )
}
