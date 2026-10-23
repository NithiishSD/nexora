package com.nexora.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nexora.desktop.util.Strings

/** Chip colours of 06 section 8: dark text on a light tint, each pair at least 4.5:1. */
enum class ChipColour(val background: Color, val text: Color) {
    GREY(Color(0xFFE6E7EA), Color(0xFF3B3F46)),
    BLUE(Color(0xFFDCE8F7), Color(0xFF123F73)),
    AMBER(Color(0xFFFCEFD6), Color(0xFF6B4200)),
    GREEN(Color(0xFFDDF1E3), Color(0xFF145A2B)),
    DARK_GREEN(Color(0xFFCFE6D6), Color(0xFF0B3D1C)),
    RED(Color(0xFFF9DEDC), Color(0xFF8C1D18)),
    PURPLE(Color(0xFFEBE0F5), Color(0xFF4A2370)),
    TEAL(Color(0xFFD7EFEF), Color(0xFF0E4F4F)),
}

/** Every status the API returns, by enum type, with its colour (06 section 8). */
val STATUS_COLOURS: Map<String, Map<String, ChipColour>> = mapOf(
    "PartyStatus" to mapOf("ACTIVE" to ChipColour.GREEN, "INACTIVE" to ChipColour.GREY),
    "PurchaseStatus" to mapOf(
        "ORDERED" to ChipColour.BLUE, "PARTIALLY_RECEIVED" to ChipColour.AMBER,
        "FULLY_RECEIVED" to ChipColour.GREEN, "CANCELLED" to ChipColour.GREY,
    ),
    "PaymentStatusView" to mapOf("UNPAID" to ChipColour.RED, "PARTIALLY_PAID" to ChipColour.AMBER, "PAID" to ChipColour.GREEN),
    "OrderStatus" to mapOf(
        "PLACED" to ChipColour.GREY, "CONFIRMED" to ChipColour.BLUE, "IN_PROGRESS" to ChipColour.PURPLE, "READY" to ChipColour.TEAL,
        "DELIVERED" to ChipColour.GREEN, "COMPLETED" to ChipColour.DARK_GREEN, "CANCELLED" to ChipColour.GREY,
    ),
    "OrderItemStatus" to mapOf(
        "PENDING" to ChipColour.GREY, "IN_PROGRESS" to ChipColour.PURPLE, "READY" to ChipColour.TEAL,
        "DELIVERED" to ChipColour.GREEN, "CANCELLED" to ChipColour.GREY,
    ),
    "BatchStatus" to mapOf(
        "PLANNED" to ChipColour.BLUE, "IN_PROGRESS" to ChipColour.PURPLE, "COMPLETED" to ChipColour.GREEN, "CANCELLED" to ChipColour.GREY,
    ),
    "JobStatus" to mapOf(
        "CREATED" to ChipColour.BLUE, "MATERIAL_ISSUED" to ChipColour.PURPLE, "PARTIALLY_RECEIVED" to ChipColour.AMBER,
        "COMPLETED" to ChipColour.GREEN, "CANCELLED" to ChipColour.GREY,
    ),
    "RecordStatus" to mapOf("RECORDED" to ChipColour.GREEN, "REVERSED" to ChipColour.GREY, "PENDING" to ChipColour.AMBER, "BOUNCED" to ChipColour.RED),
    "AccountStatus" to mapOf("ACTIVE" to ChipColour.GREEN, "CLOSED" to ChipColour.GREY),
    "LoanStatus" to mapOf("ACTIVE" to ChipColour.GREEN, "CLOSED" to ChipColour.GREY),
    "ChitStatus" to mapOf("ACTIVE" to ChipColour.GREEN, "MATURED" to ChipColour.TEAL, "CLOSED" to ChipColour.GREY),
    "NotificationStatus" to mapOf(
        "PENDING" to ChipColour.AMBER, "SENT" to ChipColour.BLUE, "RESOLVED" to ChipColour.GREEN, "FAILED" to ChipColour.RED,
    ),
    "NotificationType" to mapOf(
        "SUPPLIER_DUE" to ChipColour.BLUE, "CUSTOMER_OUTSTANDING" to ChipColour.AMBER,
        "LOAN_DUE" to ChipColour.PURPLE, "CHIT_DUE" to ChipColour.TEAL,
        "LOW_STOCK" to ChipColour.RED, "PENDING_OUTSOURCING" to ChipColour.AMBER,
    ),
    "ReminderKind" to mapOf(
        "DUE_SOON" to ChipColour.BLUE, "DUE_TODAY" to ChipColour.AMBER, "OVERDUE" to ChipColour.RED,
    ),
)

/** Business wording of any API enum value: `enum.<Type>.<VALUE>` in strings.properties, or the raw value. */
fun enumLabel(type: String, value: String?): String =
    if (value == null) "" else Strings.getOrNull("enum.$type.$value") ?: value

/**
 * A status as a coloured chip WITH its text (never colour alone, NFR-USE-06). Reversed records show the
 * text struck through (06 section 8).
 */
@Composable
fun StatusChip(type: String, value: String?, modifier: Modifier = Modifier) {
    if (value == null) return
    val colour = STATUS_COLOURS[type]?.get(value) ?: ChipColour.GREY
    Chip(enumLabel(type, value), colour, modifier, strike = value == "REVERSED")
}

/** A flag chip such as "Overdue (5 days)" or "Late": red outline, text always present. */
@Composable
fun FlagChip(text: String, modifier: Modifier = Modifier) =
    Chip(text, ChipColour.RED, modifier, outlined = true)

@Composable
private fun Chip(text: String, colour: ChipColour, modifier: Modifier, strike: Boolean = false, outlined: Boolean = false) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val chipBg = when {
        outlined -> MaterialTheme.colorScheme.surface
        isDark -> when (colour) {
            ChipColour.GREEN, ChipColour.DARK_GREEN -> Color(0xFF0F3820)
            ChipColour.GREY -> Color(0xFF262C36)
            ChipColour.BLUE -> Color(0xFF142C4F)
            ChipColour.AMBER -> Color(0xFF3B2A10)
            ChipColour.RED -> Color(0xFF3D1616)
            ChipColour.PURPLE -> Color(0xFF2F1D4A)
            ChipColour.TEAL -> Color(0xFF0F3636)
        }
        else -> colour.background
    }
    val chipText = when {
        isDark && !outlined -> when (colour) {
            ChipColour.GREEN, ChipColour.DARK_GREEN -> Color(0xFF4ADE80)
            ChipColour.GREY -> Color(0xFF94A3B8)
            ChipColour.BLUE -> Color(0xFF60A5FA)
            ChipColour.AMBER -> Color(0xFFFBBF24)
            ChipColour.RED -> Color(0xFFF87171)
            ChipColour.PURPLE -> Color(0xFFC084FC)
            ChipColour.TEAL -> Color(0xFF2DD4BF)
        }
        else -> colour.text
    }
    val chipBorder = when {
        outlined -> BorderStroke(1.dp, colour.text)
        isDark -> when (colour) {
            ChipColour.GREEN, ChipColour.DARK_GREEN -> BorderStroke(1.dp, Color(0xFF166534))
            ChipColour.GREY -> BorderStroke(1.dp, Color(0xFF334155))
            ChipColour.BLUE -> BorderStroke(1.dp, Color(0xFF1E40AF))
            ChipColour.AMBER -> BorderStroke(1.dp, Color(0xFF854D0E))
            ChipColour.RED -> BorderStroke(1.dp, Color(0xFF991B1B))
            ChipColour.PURPLE -> BorderStroke(1.dp, Color(0xFF6B21A8))
            ChipColour.TEAL -> BorderStroke(1.dp, Color(0xFF0F766E))
        }
        else -> null
    }

    Surface(
        color = chipBg,
        contentColor = chipText,
        shape = RoundedCornerShape(12.dp),
        border = chipBorder,
        modifier = modifier,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
    }
}
