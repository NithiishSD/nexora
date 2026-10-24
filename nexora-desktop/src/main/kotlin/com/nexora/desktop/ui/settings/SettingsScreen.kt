package com.nexora.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.LabeledValue
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.Segmented
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.notifications.ReminderSettingsPanel
import com.nexora.desktop.ui.shell.LocalShellActions
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.settings.SettingsViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay

/** S-SET-01 Settings and about (+ S-NTF-02 reminder settings for FINANCE_VIEW). */
@Composable
fun SettingsScreen(nav: NavigationViewModel) {
    val vm: SettingsViewModel = screenViewModel()
    val settings by vm.current.collectAsState()
    val about by vm.about.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val sysSettings by vm.systemSettings.collectAsState()
    val statusMsg by vm.settingsStatus.collectAsState()
    val actions = LocalShellActions.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(s("nav.settings"))

        statusMsg?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        // Business Profile Section
        val biz = sysSettings["BUSINESS"] ?: emptyMap()
        var bizName by remember(biz["name"]) { mutableStateOf(biz["name"] ?: "") }
        var bizCity by remember(biz["city"]) { mutableStateOf(biz["city"] ?: "") }
        var bizPhone by remember(biz["phone"]) { mutableStateOf(biz["phone"] ?: "") }
        var bizGstin by remember(biz["gstin"]) { mutableStateOf(biz["gstin"] ?: "") }

        Section("Business Profile") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = bizName,
                    onValueChange = { bizName = it },
                    label = { Text("Business Name") },
                    modifier = Modifier.width(260.dp),
                    enabled = nav.can("FINANCE_MANAGE")
                )
                OutlinedTextField(
                    value = bizCity,
                    onValueChange = { bizCity = it },
                    label = { Text("City") },
                    modifier = Modifier.width(200.dp),
                    enabled = nav.can("FINANCE_MANAGE")
                )
                OutlinedTextField(
                    value = bizPhone,
                    onValueChange = { bizPhone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.width(200.dp),
                    enabled = nav.can("FINANCE_MANAGE")
                )
                OutlinedTextField(
                    value = bizGstin,
                    onValueChange = { bizGstin = it },
                    label = { Text("GSTIN") },
                    modifier = Modifier.width(220.dp),
                    enabled = nav.can("FINANCE_MANAGE")
                )
            }
            if (nav.can("FINANCE_MANAGE")) {
                Button(
                    onClick = {
                        vm.updateSystemCategory(
                            "BUSINESS",
                            mapOf("name" to bizName, "city" to bizCity, "phone" to bizPhone, "gstin" to bizGstin)
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Save Business Profile")
                }
            }
        }

        // Cloud Backup & Safety Section
        val backupConfig = sysSettings["BACKUP"] ?: emptyMap()
        var backupMsg by remember { mutableStateOf<String?>(null) }
        var isBackingUp by remember { mutableStateOf(false) }

        var backupProvider by remember(backupConfig["provider"]) {
            mutableStateOf(backupConfig["provider"] ?: "local")
        }
        var autoBackupEnabled by remember(backupConfig["auto_backup_enabled"]) {
            mutableStateOf(backupConfig["auto_backup_enabled"] != "false")
        }
        var backupFrequency by remember(backupConfig["frequency"]) {
            mutableStateOf(backupConfig["frequency"] ?: "daily_2am")
        }
        var retentionDays by remember(backupConfig["retention_days"]) {
            mutableStateOf(backupConfig["retention_days"] ?: "30")
        }
        var s3Endpoint by remember(backupConfig["s3_endpoint"]) {
            mutableStateOf(backupConfig["s3_endpoint"] ?: "")
        }
        var s3Bucket by remember(backupConfig["s3_bucket"]) {
            mutableStateOf(backupConfig["s3_bucket"] ?: "")
        }
        var s3Region by remember(backupConfig["s3_region"]) {
            mutableStateOf(backupConfig["s3_region"] ?: "ap-south-1")
        }
        var s3AccessKey by remember(backupConfig["s3_access_key"]) {
            mutableStateOf(backupConfig["s3_access_key"] ?: "")
        }
        var s3SecretKey by remember(backupConfig["s3_secret_key"]) {
            mutableStateOf(backupConfig["s3_secret_key"] ?: "")
        }
        var cloudVaultKey by remember(backupConfig["cloud_vault_key"]) {
            mutableStateOf(backupConfig["cloud_vault_key"] ?: "")
        }

        Section("Backup & Cloud Safety") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(
                    label = "Storage Destination",
                    options = listOf(
                        "local" to "Local Storage (Device)",
                        "smartsilk_cloud" to "SmartSilk Cloud Vault",
                        "custom_s3" to "Custom S3 / Cloud Bucket",
                    ),
                    selected = backupProvider,
                    onSelect = { backupProvider = it },
                    enabled = nav.can("FINANCE_MANAGE"),
                )

                when (backupProvider) {
                    "local" -> {
                        Text(
                            "Backups are encrypted and stored in your device's local application data folder and user Downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    "smartsilk_cloud" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Automated real-time multi-region encrypted cloud backup directly to SmartSilk Cloud Vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = cloudVaultKey,
                                onValueChange = { cloudVaultKey = it },
                                label = { Text("Cloud Vault Key / License") },
                                modifier = Modifier.width(360.dp),
                                enabled = nav.can("FINANCE_MANAGE"),
                            )
                        }
                    }
                    "custom_s3" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Connect your own S3-compatible cloud storage (AWS S3, MinIO, Cloudflare R2, Google Cloud Storage).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = s3Endpoint,
                                    onValueChange = { s3Endpoint = it },
                                    label = { Text("Endpoint URL (e.g. https://s3.ap-south-1.amazonaws.com)") },
                                    modifier = Modifier.width(380.dp),
                                    enabled = nav.can("FINANCE_MANAGE"),
                                )
                                OutlinedTextField(
                                    value = s3Bucket,
                                    onValueChange = { s3Bucket = it },
                                    label = { Text("Bucket Name") },
                                    modifier = Modifier.width(220.dp),
                                    enabled = nav.can("FINANCE_MANAGE"),
                                )
                                OutlinedTextField(
                                    value = s3Region,
                                    onValueChange = { s3Region = it },
                                    label = { Text("Region") },
                                    modifier = Modifier.width(160.dp),
                                    enabled = nav.can("FINANCE_MANAGE"),
                                )
                                OutlinedTextField(
                                    value = s3AccessKey,
                                    onValueChange = { s3AccessKey = it },
                                    label = { Text("Access Key ID") },
                                    modifier = Modifier.width(220.dp),
                                    enabled = nav.can("FINANCE_MANAGE"),
                                )
                                OutlinedTextField(
                                    value = s3SecretKey,
                                    onValueChange = { s3SecretKey = it },
                                    label = { Text("Secret Access Key") },
                                    modifier = Modifier.width(260.dp),
                                    enabled = nav.can("FINANCE_MANAGE"),
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoBackupEnabled,
                        onCheckedChange = { autoBackupEnabled = it },
                        enabled = nav.can("FINANCE_MANAGE"),
                    )
                    Text("Enable Automatic Scheduled Backups", style = MaterialTheme.typography.bodyMedium)
                }

                if (autoBackupEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Dropdown(
                            "Backup Frequency",
                            listOf(
                                "every_6h" to "Every 6 Hours",
                                "daily_2am" to "Daily at Night (02:00 AM) — Recommended",
                                "weekly" to "Weekly (Sunday 02:00 AM)",
                                "on_close" to "On Application Close",
                                "manual" to "Manual Backups Only",
                            ),
                            backupFrequency,
                            { if (nav.can("FINANCE_MANAGE")) backupFrequency = it },
                            Modifier.width(340.dp),
                        )
                        Dropdown(
                            "Retention Policy",
                            listOf(
                                "7" to "Keep for 7 Days",
                                "30" to "Keep for 30 Days — Recommended",
                                "90" to "Keep for 90 Days",
                                "365" to "Keep for 1 Year",
                                "forever" to "Keep Indefinitely",
                            ),
                            retentionDays,
                            { if (nav.can("FINANCE_MANAGE")) retentionDays = it },
                            Modifier.width(260.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (nav.can("FINANCE_MANAGE")) {
                        Button(onClick = {
                            vm.updateSystemCategory(
                                "BACKUP",
                                mapOf(
                                    "provider" to backupProvider,
                                    "auto_backup_enabled" to autoBackupEnabled.toString(),
                                    "frequency" to backupFrequency,
                                    "retention_days" to retentionDays,
                                    "s3_endpoint" to s3Endpoint,
                                    "s3_bucket" to s3Bucket,
                                    "s3_region" to s3Region,
                                    "s3_access_key" to s3AccessKey,
                                    "s3_secret_key" to s3SecretKey,
                                    "cloud_vault_key" to cloudVaultKey,
                                ),
                            )
                        }) {
                            Text("Save Backup Configuration")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            isBackingUp = true
                            vm.triggerBackup { _, msg ->
                                isBackingUp = false
                                backupMsg = msg
                            }
                        },
                        enabled = !isBackingUp,
                    ) {
                        Text(if (isBackingUp) "Exporting Snapshot..." else "Create & Download Snapshot Now")
                    }
                }

                backupMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Feature Integrations Section (WhatsApp & AI OCR)
        val waConfig = sysSettings["WHATSAPP"] ?: emptyMap()
        var waEnabled by remember(waConfig["enabled"]) { mutableStateOf(waConfig["enabled"] == "true") }
        var waPhoneId by remember(waConfig["phone_number_id"]) { mutableStateOf(waConfig["phone_number_id"] ?: "") }
        var waToken by remember(waConfig["access_token"]) { mutableStateOf(waConfig["access_token"] ?: "") }

        val aiConfig = sysSettings["AI_OCR"] ?: emptyMap()
        var aiEnabled by remember(aiConfig["enabled"]) { mutableStateOf(aiConfig["enabled"] == "true") }
        var aiKey by remember(aiConfig["gemini_api_key"]) { mutableStateOf(aiConfig["gemini_api_key"] ?: "") }

        if (nav.can("FINANCE_MANAGE")) {
            Section("Integrations (Optional)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WhatsApp Automated Reminders", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = waEnabled, onCheckedChange = { waEnabled = it })
                        Text("Enable WhatsApp Business API Notifications")
                    }
                    if (waEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = waPhoneId,
                                onValueChange = { waPhoneId = it },
                                label = { Text("Phone Number ID") },
                                modifier = Modifier.width(260.dp)
                            )
                            OutlinedTextField(
                                value = waToken,
                                onValueChange = { waToken = it },
                                label = { Text("Access Token") },
                                modifier = Modifier.width(320.dp)
                            )
                        }
                    }

                    Text("AI Document & Bill Scanner", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = aiEnabled, onCheckedChange = { aiEnabled = it })
                        Text("Enable Gemini AI Supplier Bill OCR")
                    }
                    if (aiEnabled) {
                        OutlinedTextField(
                            value = aiKey,
                            onValueChange = { aiKey = it },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.width(360.dp)
                        )
                    }

                    Button(onClick = {
                        vm.updateSystemCategory(
                            "WHATSAPP",
                            mapOf("enabled" to waEnabled.toString(), "phone_number_id" to waPhoneId, "access_token" to waToken)
                        )
                        vm.updateSystemCategory(
                            "AI_OCR",
                            mapOf("enabled" to aiEnabled.toString(), "gemini_api_key" to aiKey)
                        )
                    }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Save Integrations")
                    }
                }
            }
        }

        Section(s("settings.server")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val url = settings.serverUrl.orEmpty()
                val isLocal = url.contains(":8088")
                Column(Modifier.weight(1f)) {
                    Text(if (isLocal) "Local Engine ($url)" else url, style = MaterialTheme.typography.bodyMedium)
                    if (isLocal) {
                        Text(
                            "Isolated Local Engine (Port 8088)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                OutlinedButton(onClick = actions.changeServer) { Text(s("settings.changeServer")) }
            }
        }
        Section(s("settings.display")) {
            Segmented(
                s("settings.textSize"),
                listOf(1f, 1.25f, 1.5f, 1.75f, 2f).map { it to s("settings.percent", (it * 100).toInt()) },
                settings.textScale,
                vm::setTextScale,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.sidebarCollapsed, onCheckedChange = vm::setSidebarCollapsed,
                    modifier = Modifier.semantics { contentDescription = s("settings.sidebarCollapsed") },
                )
                Text(s("settings.sidebarCollapsed"))
            }
        }
        if (accounts.isNotEmpty()) {
            Section(s("settings.defaults")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountRules.METHODS.forEach { method ->
                        Dropdown(
                            s("settings.paymentAccount", enumLabel("PaymentMethod", method)),
                            AccountRules.accountsFor(method, accounts).map { it.id to it.accountName },
                            settings.lastUsed["payment.account.$method"],
                            { vm.setDefault("payment.account.$method", it) },
                            Modifier.width(240.dp),
                        )
                    }
                    Dropdown(s("settings.expenseAccount"), accounts.map { it.id to it.accountName }, settings.lastUsed["expense.account"], {
                        vm.setDefault("expense.account", it)
                    }, Modifier.width(240.dp))
                }
            }
        }
        if (nav.can("FINANCE_VIEW")) ReminderSettingsPanel(canEdit = nav.can("FINANCE_MANAGE"))
        Section(s("settings.about")) {
            FlowRow {
                LabeledValue(s("settings.clientVersion"), about.clientVersion)
                LabeledValue(s("settings.serverVersion"), about.serverVersion ?: "—")
                LabeledValue(s("settings.apiVersion"), about.apiVersion ?: "—")
                LabeledValue(s("settings.clientApi"), ApiClient.API_MAJOR.toString())
            }
            Text(s("settings.licences"), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { nav.show(Overlay.ConfirmLogout) }) { Text(s("shell.logout")) }
    }
}
