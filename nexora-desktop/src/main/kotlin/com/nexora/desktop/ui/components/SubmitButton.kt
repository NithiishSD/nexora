package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Save button: spinner and disabled while the request runs, so a second press does nothing. The idempotency key
 * for retries lives in the view-model's SubmitController, not in the button.
 */
@Composable
fun SubmitButton(label: String, submitting: Boolean, onClick: () -> Unit, enabled: Boolean = true, tag: String? = null) {
    Button(
        onClick = onClick,
        enabled = enabled && !submitting,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(label)
        }
    }
}
