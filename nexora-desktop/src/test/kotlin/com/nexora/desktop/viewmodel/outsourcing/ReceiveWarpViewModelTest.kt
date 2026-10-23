package com.nexora.desktop.viewmodel.outsourcing

import com.nexora.desktop.repository.OutsourcingRepository
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ReceiveWarpViewModelTest {

    @Test
    @DisplayName("FR-OUT-03 receive above still outside shows error")
    fun receiveAboveStillOutside_showsError() = vmTest { server ->
        server.on("POST /outsourcing-jobs/j1/receive") { problem(409, "OUTSOURCE_RECEIPT_EXCEEDED", "Only 47.000 kg are still outside.") }
        // The server said 50.000 kg is still with the manufacturer.
        val vm = ReceiveWarpViewModel(OutsourcingRepository(server.client()), TEST_CLOCK, "j1", BigDecimal("50.000"))

        vm.edit { it.copy(received = "48.000", wastage = "3.000") } // 51 kg > 50 kg outside
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("This is more than the silk still with the manufacturer.", vm.status.value.fieldErrors["receivedWeightKg"])
        assertEquals(0, server.count("POST /outsourcing-jobs/j1/receive"))

        // Within the local figure but refused by the server (someone received meanwhile): same field, same message.
        vm.edit { it.copy(received = "47.000", wastage = "3.000") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("This is more than the silk still with the manufacturer.", vm.status.value.fieldErrors["receivedWeightKg"])
        assertNotNull(server.last("POST /outsourcing-jobs/j1/receive").headers["Idempotency-Key"])
    }
}
