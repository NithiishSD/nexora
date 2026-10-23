package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What the server did after a submit (number, allocations, new status). Every value shown here is copied from
 * the API response; nothing is calculated on the client (NFR-USE-02).
 */
@Composable
fun ResultPanel(title: String, lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, Modifier.weight(1f))
                    Text(value, textAlign = TextAlign.End)
                }
            }
        }
    }
}
