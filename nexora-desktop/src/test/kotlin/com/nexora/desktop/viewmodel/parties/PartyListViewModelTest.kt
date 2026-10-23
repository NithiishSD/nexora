package com.nexora.desktop.viewmodel.parties

import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.json
import com.nexora.desktop.support.vmTest
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PartyListViewModelTest {

    @Test
    @DisplayName("FR-PARTY-07 search debounced300ms and cancels stale requests")
    fun search_debounced300msAndCancelsStaleRequests() = vmTest { server ->
        server.on("GET /parties") { request ->
            val search = request.url.parameters["search"]
            // The answer to the first search arrives late; a newer search must win.
            if (search == "ram") delay(1_000)
            json(Fixtures.page("""{"id":"p-$search","partyCode":"PTY-2026-0003","name":"$search","roles":["SUPPLIER"],"status":"ACTIVE"}"""))
        }
        val vm = PartyListViewModel(PartyRepository(server.client()), TEST_CLOCK)
        testScheduler.advanceUntilIdle()
        val initialCalls = server.count("GET /parties")
        assertEquals("ACTIVE", server.last("GET /parties").query["status"])

        // Typing r, ra, ram quickly: nothing is sent before 300 ms of quiet, and 1 letter is never sent.
        vm.onSearch("r")
        testScheduler.advanceTimeBy(100)
        vm.onSearch("ra")
        testScheduler.advanceTimeBy(100)
        vm.onSearch("ram")
        testScheduler.advanceTimeBy(299)
        assertEquals(initialCalls, server.count("GET /parties"))
        testScheduler.advanceTimeBy(2) // 300 ms after the last key
        testScheduler.runCurrent()
        assertEquals(initialCalls + 1, server.count("GET /parties"))
        assertEquals("ram", server.last("GET /parties").query["search"])

        // While "ram" is still loading the user types more; the stale "ram" answer is cancelled.
        vm.onSearch("rames")
        testScheduler.advanceUntilIdle()
        assertEquals("rames", vm.state.value.page!!.content.single().name)
        assertEquals(initialCalls + 2, server.count("GET /parties"))
    }
}
