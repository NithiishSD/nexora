package com.nexora.desktop.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.NotificationResponse
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.TextInput
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.notifications.NotificationListViewModel
import com.nexora.desktop.viewmodel.notifications.ReminderSettingsViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

/** S-NTF-01 Notifications: read-only list in the MVP. */
@Composable
fun NotificationsScreen(nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    val vm: NotificationListViewModel = screenViewModel()
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("notification.title"),
        vm = vm,
        columns = listOf(
            textColumn<NotificationResponse>(s("common.date"), 1f) { Dates.formatIsoTimestamp(it.scheduledAt) },
            TableColumn(s("notification.type"), 1.1f) { n -> Row(Modifier.weight(1.1f)) { StatusChip("NotificationType", n.notificationType) } },
            TableColumn(s("notification.kind"), 1f) { n -> Row(Modifier.weight(1f)) { StatusChip("ReminderKind", n.kind) } },
            textColumn(s("notification.party"), 1.2f) { it.party?.name ?: "-" },
            textColumn(s("notification.reference"), 1.1f) { it.referenceNumber ?: "-" },
            textColumn(s("notification.message"), 2.4f) { it.message.orEmpty() },
            TableColumn(s("common.status"), 1f) { n -> Row(Modifier.weight(1f)) { StatusChip("NotificationStatus", n.status) } },
        ),
        emptyMessage = s("notification.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { n ->
            when (n.referenceType) {
                "PURCHASE" -> if (n.referenceId != null) nav.push(Screen.PurchaseDetail(n.referenceId))
                "LOAN" -> if (n.referenceId != null) nav.push(Screen.LoanDetail(n.referenceId))
                "CHIT" -> if (n.referenceId != null) nav.push(Screen.ChitDetail(n.referenceId))
                else -> Unit
            }
        },
        onBack = nav.backIfNested(),
        filters = {
            FilterDropdown(s("notification.type"), listOf("SUPPLIER_DUE", "LOAN_DUE", "CHIT_DUE", "LOW_STOCK").map { it to enumLabel("NotificationType", it) }, state.query.filters["type"]) {
                vm.setFilter("type", it)
            }
            FilterDropdown(s("common.status"), listOf("PENDING", "SENT", "RESOLVED", "FAILED").map { it to enumLabel("NotificationStatus", it) }, state.query.filters["status"]) {
                vm.setFilter("status", it)
            }
        },
    )
}

/** S-NTF-02 Reminder settings, shown under Settings; saving needs FINANCE_MANAGE. */
@Composable
fun ReminderSettingsPanel(canEdit: Boolean) {
    val vm: ReminderSettingsViewModel = screenViewModel()
    val state by vm.state.collectAsState()
    Section(s("notification.settings")) {
        state.error?.let { ErrorBanner(it) }
        state.configs.forEach { c ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(enumLabel("NotificationType", c.notificationType), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = c.enabled, enabled = canEdit,
                        onCheckedChange = { on -> vm.change(c.notificationType) { it.copy(enabled = on) } },
                        modifier = Modifier.semantics { contentDescription = s("notification.enabled") },
                    )
                    Text(s("notification.enabled"))
                    TextInput(s("notification.daysBefore"), c.daysBeforeDue.toString(), { v ->
                        vm.change(c.notificationType) { it.copy(daysBeforeDue = v.filter(Char::isDigit).take(2).toIntOrNull() ?: 0) }
                    }, Modifier.width(160.dp), maxLength = 2, enabled = canEdit)
                    TextInput(s("notification.repeatDays"), c.overdueRepeatDays.toString(), { v ->
                        vm.change(c.notificationType) { it.copy(overdueRepeatDays = v.filter(Char::isDigit).take(2).toIntOrNull() ?: 1) }
                    }, Modifier.width(200.dp), maxLength = 2, enabled = canEdit)
                    Dropdown(s("notification.priority"), listOf("LOW", "MEDIUM", "HIGH").map { it to enumLabel("Priority", it) }, c.priority, { v ->
                        if (canEdit) vm.change(c.notificationType) { it.copy(priority = v) }
                    }, Modifier.width(160.dp))
                    if (canEdit) OutlinedButton(onClick = { vm.save(c.notificationType) }, enabled = state.saving == null) { Text(s("common.save")) }
                    if (state.saved == c.notificationType) Text(s("notification.saved"))
                }
            }
        }
    }
}
