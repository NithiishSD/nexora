package com.nexora.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.ServerStatus
import com.nexora.desktop.ui.dashboard.WorkflowTutorialDialog
import com.nexora.desktop.ui.theme.AppColors
import com.nexora.desktop.util.s

/** Modern 48 px top bar: name, server status dot, theme toggle, reminders bell, user, log out (06 section 2). */
@Composable
fun TopBar(
    status: ServerStatus,
    pendingReminders: Int?,
    userName: String,
    onBell: () -> Unit,
    onLogout: () -> Unit,
    darkMode: Boolean = false,
    onToggleDarkMode: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s("app.title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val (colour, label) = when (status) {
            ServerStatus.ONLINE -> AppColors.online to s("shell.serverOnline")
            ServerStatus.SLOW -> AppColors.slow to s("shell.serverSlow")
            ServerStatus.OFFLINE -> AppColors.offline to s("shell.serverOffline")
        }
        // Colour plus text: the state is never shown by colour alone (NFR-USE-06).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Box(Modifier.size(10.dp).background(colour, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        if (onToggleDarkMode != null) {
            TextButton(
                onClick = onToggleDarkMode,
                modifier = Modifier.semantics { contentDescription = if (darkMode) "Switch to Light Mode" else "Switch to Dark Mode" },
            ) {
                Text(if (darkMode) "☀️ Light" else "🌙 Dark")
            }
        }
        var showTutorial by remember { mutableStateOf(false) }
        TextButton(onClick = { showTutorial = true }) {
            Text("💡 " + s("guide.showGuide"))
        }
        if (showTutorial) {
            WorkflowTutorialDialog(onDismiss = { showTutorial = false })
        }
        if (pendingReminders != null) {
            TextButton(
                onClick = onBell,
                modifier = Modifier.semantics { contentDescription = s("shell.reminders", pendingReminders) },
            ) {
                Text("🔔 " + s("shell.reminders", pendingReminders))
            }
        }
        Text(userName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        TextButton(onClick = onLogout) { Text(s("shell.logout")) }
    }
}
