package com.nexora.desktop.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.auth.SessionState

/**
 * S-AUTH-03: shown on top of the current screen when the session could not be refreshed. The screen underneath
 * keeps everything that was typed; after logging in again the user simply continues.
 */
@Composable
fun SessionExpiredDialog(
    username: String,
    state: SessionState,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {}, // cannot be dismissed without choosing
        title = { Text(s("session.expiredTitle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s("session.expiredText"))
                OutlinedTextField(value = username, onValueChange = {}, readOnly = true, label = { Text(s("login.username")) })
                OutlinedTextField(
                    value = state.reLoginPassword,
                    onValueChange = onPasswordChange,
                    label = { Text(s("login.password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { onLogin(); true } else false
                    },
                )
                state.reLoginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onLogin, enabled = !state.reLoggingIn) { Text(s("login.submit")) } },
        dismissButton = { TextButton(onClick = onLogout) { Text(s("shell.logout")) } },
    )
}
