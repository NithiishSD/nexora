package com.nexora.desktop.viewmodel.notifications

import com.nexora.desktop.repository.NotificationRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NotificationListViewModelTest {

    @Test
    @DisplayName("FR-NTF-08 FR-NTF-09 loads loan and chit due reminders and filters by type")
    fun loadsLoanAndChitDueReminders_andFiltersByType() = vmTest { server ->
        server.onJson(
            "GET /notifications",
            Fixtures.page(
                """{"id":"n-loan-1","notificationType":"LOAN_DUE","kind":"DUE_TODAY","referenceType":"LOAN","referenceId":"loan-123","referenceNumber":"LON-2026-0001","message":"Pay loan LON-2026-0001: due today.","status":"PENDING"},""" +
                """{"id":"n-chit-1","notificationType":"CHIT_DUE","kind":"DUE_SOON","referenceType":"CHIT","referenceId":"chit-456","referenceNumber":"CHT-2026-0001","message":"Pay chit CHT-2026-0001 installment #1: due in 3 days.","status":"PENDING"}""",
                2,
            ),
        )

        val vm = NotificationListViewModel(NotificationRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()

        val items = vm.state.value.page!!.content
        assertEquals(2, items.size)
        assertEquals("LOAN_DUE", items[0].notificationType)
        assertEquals("LOAN", items[0].referenceType)
        assertEquals("loan-123", items[0].referenceId)

        assertEquals("CHIT_DUE", items[1].notificationType)
        assertEquals("CHIT", items[1].referenceType)
        assertEquals("chit-456", items[1].referenceId)

        vm.setFilter("type", "LOAN_DUE")
        testScheduler.advanceUntilIdle()
        assertEquals("LOAN_DUE", server.last("GET /notifications").query["type"])

        vm.setFilter("type", "CHIT_DUE")
        testScheduler.advanceUntilIdle()
        assertEquals("CHIT_DUE", server.last("GET /notifications").query["type"])
    }

    @Test
    @DisplayName("FR-NTF-01 updates reminder configuration for loan and chit dues")
    fun updatesReminderConfiguration_forLoanAndChit() = vmTest { server ->
        server.onJson(
            "GET /notifications/configurations",
            """[
                {"notificationType":"SUPPLIER_DUE","enabled":true,"daysBeforeDue":3,"overdueRepeatDays":1,"priority":"HIGH"},
                {"notificationType":"LOAN_DUE","enabled":true,"daysBeforeDue":5,"overdueRepeatDays":2,"priority":"HIGH"},
                {"notificationType":"CHIT_DUE","enabled":false,"daysBeforeDue":3,"overdueRepeatDays":1,"priority":"MEDIUM"}
            ]""",
        )
        server.onJson(
            "PUT /notifications/configurations/LOAN_DUE",
            """{"notificationType":"LOAN_DUE","enabled":true,"daysBeforeDue":7,"overdueRepeatDays":3,"priority":"HIGH"}""",
        )

        val vm = ReminderSettingsViewModel(NotificationRepository(server.client()))
        testScheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.configs.size)
        vm.change("LOAN_DUE") { it.copy(daysBeforeDue = 7, overdueRepeatDays = 3) }
        vm.save("LOAN_DUE")
        testScheduler.advanceUntilIdle()

        assertEquals("LOAN_DUE", vm.state.value.saved)
        val updated = vm.state.value.configs.first { it.notificationType == "LOAN_DUE" }
        assertEquals(7, updated.daysBeforeDue)
        assertEquals(3, updated.overdueRepeatDays)
        assertTrue(updated.enabled)
    }
}
