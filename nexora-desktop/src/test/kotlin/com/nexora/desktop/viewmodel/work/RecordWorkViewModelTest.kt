package com.nexora.desktop.viewmodel.work

import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.WorkRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.testSettings
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.ui.components.PickOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordWorkViewModelTest {

    @Test
    @DisplayName("FR-WORK-02 NFR-USE-01 rolling shows hours warping shows kg")
    fun rollingShowsHoursWarpingShowsKg() = vmTest { server ->
        server.onJson("GET /parties/77ab", Fixtures.party(id = "77ab", roles = "\"WORKER\"").replace("\"defaultRollingRatePerHour\":null", "\"defaultRollingRatePerHour\":100.00"))
        server.onJson(
            "POST /work-records",
            """{"id":"w1","worker":{"id":"77ab","name":"Selvi"},"workDate":"2026-10-08","workType":"WARPING","quantityKg":12.500,"rate":40.00,"amount":500.00,"status":"RECORDED"}""",
            201,
        )
        val vm = RecordWorkViewModel(WorkRepository(server.client()), PartyRepository(server.client()), testSettings(), TEST_CLOCK)
        vm.pickWorker(PickOption("77ab", "Selvi"))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.showsHours(vm.form.value)) // Rolling by default: hours
        assertEquals("₹100.00 /hour", vm.defaultRateHint())
        vm.edit { it.copy(hours = "8.00") }

        vm.setWorkType("WARPING")
        assertFalse(vm.showsHours(vm.form.value))
        assertEquals("", vm.form.value.hours) // the other measure is cleared, never sent
        vm.edit { it.copy(quantityKg = "12.500", rate = "40.00") }
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(
            """{"workerId":"77ab","workDate":"2026-10-08","workType":"WARPING","quantityKg":12.500,"rate":40.00}""",
            server.last("POST /work-records").body,
        )
    }

    @Test
    @DisplayName("FR-WORK-02 warping without kg is rejected locally")
    fun warpingWithoutKg_rejected() = vmTest { server ->
        val vm = RecordWorkViewModel(WorkRepository(server.client()), PartyRepository(server.client()), testSettings(), TEST_CLOCK)
        vm.edit { it.copy(worker = PickOption("77ab", "Selvi")) }
        vm.setWorkType("WARPING")
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Required", vm.status.value.fieldErrors["quantityKg"])
        assertEquals(null, vm.status.value.fieldErrors["hours"])
    }
}
