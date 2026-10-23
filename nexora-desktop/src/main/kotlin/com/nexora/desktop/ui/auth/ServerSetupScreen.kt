package com.nexora.desktop.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.auth.ServerSetupViewModel

/** S-AUTH-01 Server setup screen supporting both Local Standalone and Remote/Cloud server options. */
@Composable
fun ServerSetupScreen(viewModel: ServerSetupViewModel, onContinue: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("SmartSilk Connection Setup", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose how you want to connect to SmartSilk:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // OPTION 1: LOCAL STANDALONE ENGINE
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Option 1: Run on this Computer (Local Engine)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Run SmartSilk 100% locally and privately on this PC without needing an external cloud server.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Button(onClick = viewModel::tryStartAndConnectLocal, enabled = !state.testing) {
                            Text("Use Local Engine (localhost)")
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(" OR ", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // OPTION 2: CONNECT TO REMOTE / CLOUD SERVER
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Option 2: Connect to Network or Cloud Server", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect to your central shop computer, local network server, or cloud instance.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = viewModel::onAddressChange,
                        label = { Text(s("setup.address")) },
                        placeholder = { Text(s("setup.addressExample")) },
                        isError = state.addressError != null,
                        supportingText = state.addressError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = viewModel::testConnection, enabled = !state.testing) {
                        Text(s("setup.test"))
                    }
                }
            }

            // Status and Results
            if (state.developmentWarning) {
                Text(s("setup.developmentServer"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.serverVersion?.let {
                Text(s("setup.serverVersion", it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Button(
                    onClick = { if (viewModel.saveAndContinue()) onContinue() },
                    enabled = state.canContinue
                ) {
                    Text("Continue to Login")
                }
                if (state.testing) CircularProgressIndicator()
            }
        }
    }
}
