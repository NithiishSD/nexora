package com.nexora.desktop.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.util.Strings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Draws any report response (05 section 16): plain values become the totals band, arrays of objects become tables,
 * nested objects become sections. Values are only FORMATTED here, never added up or derived (NFR-USE-02):
 * - a key ending in `Kg` is a weight (`1,250.500 kg`), a key containing `Percent` a percentage,
 * - any other decimal number is money (`₹12,50,000.50`), whole numbers are counts,
 * - `yyyy-MM-dd` strings are dates, enum values get their business wording.
 */
@Composable
fun JsonReport(element: JsonElement) {
    when (element) {
        is JsonArray -> ArrayTable(null, element)
        is JsonObject -> ObjectView(element)
        else -> LabeledValue("", cell(null, element))
    }
}

@Composable
fun KpiMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.padding(end = 12.dp, bottom = 12.dp).widthIn(min = 180.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ObjectView(obj: JsonObject) {
    val scalars = obj.filterValues { it is JsonPrimitive || it is JsonNull || isRef(it) }
    if (scalars.isNotEmpty()) {
        FlowRow(
            Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            scalars.forEach { (key, value) ->
                KpiMetricCard(label(key), cell(key, value))
            }
        }
    }
    obj.filterValues { it is JsonArray }.forEach { (key, value) -> ArrayTable(key, value as JsonArray) }
    obj.filterValues { it is JsonObject && !isRef(it) }.forEach { (key, value) ->
        Section(label(key)) { ObjectView(value as JsonObject) }
    }
}

@Composable
private fun ArrayTable(key: String?, array: JsonArray) {
    val rows = array.filterIsInstance<JsonObject>()
    val body: @Composable () -> Unit = {
        if (rows.isEmpty()) {
            Column { array.forEach { LabeledValue("", cell(key, it)) } }
        } else {
            // Columns: plain fields of the rows; nested lists (e.g. a supplier's purchases) are drawn below each row.
            val columns = rows.flatMap { r -> r.filterValues { it !is JsonArray && (it !is JsonObject || isRef(it)) }.keys }.distinct()
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                StaticTable(
                    columns.map(::label),
                    rows.map { r -> columns.map { c -> r[c]?.let { cell(c, it) }.orEmpty() } },
                    numeric = columns.indices.filter { i -> rows.any { (it[columns[i]] as? JsonPrimitive)?.isNumberLike() == true } }.toSet(),
                )
            }
            rows.forEach { r -> r.filterValues { it is JsonArray && it.isNotEmpty() }.forEach { (k, v) -> ArrayTable(k, v as JsonArray) } }
        }
    }
    if (key != null) Section(label(key)) { body() } else body()
}

/** `{ id, name, ... }` objects (PartyRef, AccountRef) are shown as their name in one cell. */
private fun isRef(e: JsonElement): Boolean =
    e is JsonObject && e.values.none { it is JsonArray || it is JsonObject } && ("name" in e || "accountName" in e)

private fun JsonPrimitive.isNumberLike() = !isString && content.toBigDecimalOrNull() != null

/** Header text: `report.field.<key>` from strings.properties, else the key split into words. */
fun label(key: String): String = Strings.getOrNull("report.field.$key")
    ?: key.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2").replaceFirstChar { it.uppercase() }

private val ENUM_FIELDS = mapOf(
    "productType" to "ProductType", "location" to "StockLocation", "movementType" to "MovementType", "kind" to "StatementKind",
    "category" to "ExpenseCategory", "bucket" to "PayableBucket", "source" to "WastageSource", "loanType" to "LoanType",
)

private val STATUS_TYPES = listOf("PaymentStatusView", "OrderStatus", "PurchaseStatus", "LoanStatus", "ChitStatus", "BatchStatus", "JobStatus", "RecordStatus")

/** Formats one value for display; never combines values. */
fun cell(key: String?, value: JsonElement): String = when {
    value is JsonNull -> ""
    value is JsonObject -> ((value["name"] ?: value["accountName"]) as? JsonPrimitive)?.content.orEmpty()
    value !is JsonPrimitive -> ""
    value.isString -> {
        val text = value.content
        when {
            Regex("""\d{4}-\d{2}-\d{2}""").matches(text) -> Dates.formatIso(text)
            key != null && key in ENUM_FIELDS -> enumLabel(ENUM_FIELDS.getValue(key), text)
            key == "status" || key == "paymentStatus" -> STATUS_TYPES.firstNotNullOfOrNull { Strings.getOrNull("enum.$it.$text") } ?: text
            else -> text
        }
    }
    else -> {
        val number = value.content.toBigDecimalOrNull()
        when {
            number == null -> value.content
            key != null && key.endsWith("Kg") -> IndianFormat.weight(number)
            key != null && key.contains("Percent", ignoreCase = true) -> Strings.get("loan.percent", number.toPlainString())
            // A decimal point means a money figure (money has 2 decimals in every API response).
            '.' in value.content -> IndianFormat.money(number)
            else -> number.toPlainString()
        }
    }
}
