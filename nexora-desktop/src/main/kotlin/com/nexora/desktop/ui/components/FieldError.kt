package com.nexora.desktop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Red text under a field; the text comes from the client check or from the API's `errors[]` for that field. */
@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}
