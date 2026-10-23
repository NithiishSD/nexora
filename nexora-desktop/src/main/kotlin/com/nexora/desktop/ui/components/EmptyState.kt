package com.nexora.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Empty state container (06 section 3.5): displays a clear message, contextual hint,
 * and optional primary action button when a table or list has no records.
 */
@Composable
fun EmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Decorative empty-tray vector glyph inside a subtle surface circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            EmptyTrayGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }

        Spacer(Modifier.height(4.dp))

        Text(
            message,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            if (actionLabel != null) "No records found in this section. Click below to add your first entry."
            else "There are currently no items to display matching this criteria.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Vector glyph of an inbox / tray representing an empty collection. */
@Composable
private fun EmptyTrayGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height

        // Tray body
        val tray = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.15f, h * 0.75f)
            cubicTo(w * 0.15f, h * 0.85f, w * 0.25f, h * 0.85f, w * 0.25f, h * 0.85f)
            lineTo(w * 0.75f, h * 0.85f)
            cubicTo(w * 0.75f, h * 0.85f, w * 0.85f, h * 0.85f, w * 0.85f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.35f)
        }
        drawPath(tray, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Tray front cutout
        val cutout = Path().apply {
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.35f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.65f)
            lineTo(w * 0.58f, h * 0.65f)
            lineTo(w * 0.65f, h * 0.55f)
            lineTo(w * 0.85f, h * 0.55f)
        }
        drawPath(cutout, color = tint, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

        // Document peeking out from tray
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.32f, h * 0.20f),
            size = Size(w * 0.36f, h * 0.30f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
