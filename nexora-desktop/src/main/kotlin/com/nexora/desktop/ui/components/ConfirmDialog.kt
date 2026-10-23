package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.nexora.desktop.util.s

/**
 * Confirmation for irreversible actions (06 rule P9): names the record and the effect. Cancel has the focus, so
 * pressing Enter cancels; the action needs a deliberate Tab + Enter or a click. With [reasonRequired] the action
 * stays disabled until a reason (1-500 characters) is typed.
 */
@Composable
fun ConfirmDialog(
    title: String,
    recordName: String,
    effect: String,
    confirmLabel: String,
    onConfirm: (reason: String?) -> Unit,
    onCancel: () -> Unit,
    reasonRequired: Boolean = false,
    busy: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    var reason by remember { mutableStateOf("") }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    ModalBox(onDismiss = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(recordName, style = MaterialTheme.typography.titleMedium)
            Text(effect)
            extra?.invoke()
            if (reasonRequired) {
                TextInput(s("common.reason"), reason, { reason = it.take(500) }, singleLine = false, tag = "confirm.reason")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
                Button(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text(s("common.cancel")) }
                OutlinedButton(
                    onClick = { onConfirm(reason.trim().ifEmpty { null }) },
                    enabled = !busy && (!reasonRequired || reason.isNotBlank()),
                ) { Text(confirmLabel) }
            }
        }
    }
}
