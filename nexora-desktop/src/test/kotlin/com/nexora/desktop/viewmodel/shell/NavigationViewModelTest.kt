package com.nexora.desktop.viewmodel.shell

import com.nexora.desktop.api.ApiJson
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.support.OWNER_ME
import com.nexora.desktop.support.STAFF_ME
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NavigationViewModelTest {

    private fun me(json: String) = ApiJson.decodeFromString(MeResponse.serializer(), json)

    @Test
    @DisplayName("FR-AUTH-09 entries without permission are hidden")
    fun entriesWithoutPermission_areHidden() = vmTest {
        val nav = NavigationViewModel(testSettings())
        nav.start(me(STAFF_ME))

        // STAFF holds PARTY_VIEW, INVENTORY_VIEW, ORDER_VIEW, PRODUCTION_VIEW only; Settings is always shown.
        assertEquals(
            listOf(Section.PARTIES, Section.INVENTORY, Section.ORDERS, Section.PRODUCTION, Section.SETTINGS),
            nav.state.value.sections,
        )
        // A hidden entry cannot be opened by Alt+n or a stale click either.
        nav.open(Section.MONEY)
        assertEquals(Section.INVENTORY, nav.state.value.section)
        // A shortcut for a missing permission shows the hint and opens nothing.
        nav.show(Overlay.RecordPayment())
        assertNull(nav.state.value.overlay)
        assertEquals("common.notAllowed", nav.state.value.hint)

        nav.start(me(OWNER_ME))
        assertEquals(Section.entries.toList(), nav.state.value.sections)
    }

    @Test
    @DisplayName("FR-AUTH-09 staff without report view lands on inventory")
    fun staffWithoutReportView_landsOnInventory() = vmTest {
        val nav = NavigationViewModel(testSettings())
        nav.start(me(STAFF_ME))
        assertEquals(Screen.Root(Section.INVENTORY), nav.state.value.current?.screen)

        nav.start(me(OWNER_ME))
        assertEquals(Screen.Root(Section.DASHBOARD), nav.state.value.current?.screen)
    }

    @Test
    @DisplayName("back closes the detail screen and keeps the root")
    fun back_closesDetailKeepsRoot() = vmTest {
        val nav = NavigationViewModel(testSettings())
        nav.start(me(OWNER_ME))
        nav.openNumber(2) // Alt+2 = Parties
        nav.push(Screen.PartyDetail("5d0c"))
        nav.back()
        nav.back()
        assertEquals(listOf(Screen.Root(Section.PARTIES)), nav.state.value.stack.map { it.screen })
    }
}
