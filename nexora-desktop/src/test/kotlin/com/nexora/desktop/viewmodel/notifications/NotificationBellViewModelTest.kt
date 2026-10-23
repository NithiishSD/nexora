package com.nexora.desktop.viewmodel.notifications

import com.nexora.desktop.repository.NotificationRepository
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NotificationBellViewModelTest {

    @Test
    @DisplayName("FR-NTF-05 counts pending notifications")
    fun countsPendingNotifications() = vmTest { server ->
        // One row is returned, but the bell shows the server's total of PENDING reminders.
        server.onJson(
            "GET /notifications",
            """{"content":[{"id":"n1","notificationType":"SUPPLIER_DUE","kind":"OVERDUE","referenceNumber":"PUR-2026-0003","status":"PENDING"}],
                "page":0,"size":1,"totalElements":3,"totalPages":3}""",
        )
        val vm = NotificationBellViewModel(NotificationRepository(server.client()))
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(3L, vm.count.value)
        assertEquals(mapOf("status" to "PENDING", "size" to "1"), server.last("GET /notifications").query)
    }

    @Test
    @DisplayName("E-NTF-04 retries failed notification")
    fun retriesFailedNotification() = vmTest { server ->
        server.onJson("GET /notifications", """{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}""")
        server.onJson("POST /notifications/n1/retry", """{"id":"n1","notificationType":"SUPPLIER_DUE","status":"PENDING"}""")

        val repo = NotificationRepository(server.client())
        val vm = NotificationListViewModel(repo, com.nexora.desktop.support.TEST_CLOCK)
        var done = false
        vm.retry("n1") { done = true }
        testScheduler.advanceUntilIdle()

        org.junit.jupiter.api.Assertions.assertTrue(done)
        assertEquals(1, server.count("POST /notifications/n1/retry"))
    }
}
