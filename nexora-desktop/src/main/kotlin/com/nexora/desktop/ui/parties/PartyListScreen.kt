package com.nexora.desktop.ui.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PartySummaryResponse
import com.nexora.desktop.ui.components.FilterDropdown
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.StatusChip
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.parties.PartyListViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import com.nexora.desktop.viewmodel.shell.Screen

val PARTY_ROLES = listOf("CUSTOMER", "SUPPLIER", "MANUFACTURER", "WORKER", "LENDER")

/** S-PTY-01 Party list (FR-PARTY-07). */
@Composable
fun PartyListScreen(nav: NavigationViewModel, filters: Map<String, String>, reloadTick: Int, searchTick: Int) {
    val vm: PartyListViewModel = screenViewModel(filters)
    val state by vm.state.collectAsState()
    ListScreen(
        title = s("nav.parties"),
        vm = vm,
        columns = listOf(
            textColumn<PartySummaryResponse>(s("party.code"), 1f, "partyCode") { it.partyCode },
            textColumn(s("party.name"), 2f, "name") { it.name },
            TableColumn(s("party.roles"), 2f) { p ->
                FlowRow(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    p.roles.forEach { Text(enumLabel("PartyRole", it)) }
                }
            },
            textColumn(s("party.primaryPhone"), 1.5f) { it.primaryPhone.orEmpty() },
            textColumn(s("party.city"), 1f) { it.city.orEmpty() },
            TableColumn(s("common.status"), 1f) { p -> Row(Modifier.weight(1f)) { StatusChip("PartyStatus", p.status) } },
        ),
        emptyMessage = s("party.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        onOpen = { nav.push(Screen.PartyDetail(it.id)) },
        onBack = nav.backIfNested(),
        searchable = true,
        newLabel = if (nav.can("PARTY_MANAGE")) s("party.new") else null,
        onNew = { nav.show(Overlay.PartyForm()) },
        filters = {
            FilterDropdown(s("party.role"), PARTY_ROLES.map { it to enumLabel("PartyRole", it) }, state.query.filters["role"]) {
                vm.setFilter("role", it)
            }
            FilterDropdown(
                s("common.status"),
                listOf("ACTIVE", "INACTIVE").map { it to enumLabel("PartyStatus", it) },
                state.query.filters["status"],
            ) { vm.setFilter("status", it) }
        },
    )
}
