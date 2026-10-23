package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.util.s

/**
 * Error banner at the top of a screen or form (06 section 4 common states): the message for the code,
 * the API detail underneath in small text, "Retry" for reads (F5 does the same) and an optional dismiss.
 */
@Composable
fun ErrorBanner(error: ApiError, onRetry: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        color = if (isDark) Color(0xFF450A0A) else Color(0xFFF9DEDC),
        contentColor = if (isDark) Color(0xFFFECACA) else Color(0xFF5F1410),
        // Screen readers announce the banner when it appears.
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(ErrorMessages.message(error), style = MaterialTheme.typography.bodyLarge)
                ErrorMessages.detail(error)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(s("common.retry")) }
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text(s("common.close")) }
        }
    }
}
