package com.nexora.desktop.viewmodel.shell

import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.ServerStatus
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.TEST_CLOCK
import com.nexora.desktop.support.json
import com.nexora.desktop.support.vmTest
import com.nexora.desktop.viewmodel.parties.PartyListViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException

class OfflineStateViewModelTest {

    @Test
    @DisplayName("NFR-USE-04 NFR-USE-05 connect failure keeps last data and shows retry")
    fun connectFailure_keepsLastDataAndShowsRetry() = vmTest { server ->
        var online = true
        server.on("GET /parties") {
            if (!online) throw IOException("Connection refused")
            json(Fixtures.page("""{"id":"5d0c","partyCode":"PTY-2026-0001","name":"Kumar Silks","roles":["CUSTOMER"],"status":"ACTIVE"}"""))
        }
        val api = server.client()
        val offline = OfflineStateViewModel(api)
        val list = PartyListViewModel(PartyRepository(api), TEST_CLOCK)
        testScheduler.advanceUntilIdle()
        val loadedAt = list.state.value.lastUpdated
        assertNotNull(loadedAt)
        assertFalse(offline.state.value.showBanner)

        // The connection drops; the user presses F5.
        online = false
        list.load()
        testScheduler.advanceUntilIdle()

        val state = list.state.value
        // The rows on screen stay, with the time they were loaded ("Last updated hh:mm").
        assertEquals("Kumar Silks", state.page!!.content.single().name)
        assertEquals(loadedAt, state.lastUpdated)
        assertEquals(ApiError.NETWORK, state.error?.code)
        // Red dot and the "Cannot reach the server" banner with Retry.
        assertEquals(ServerStatus.OFFLINE, offline.state.value.status)
        assertTrue(offline.state.value.showBanner)

        // Retry when the server is back: data reloads and the banner goes away.
        online = true
        offline.retry(list::load)
        testScheduler.advanceUntilIdle()
        assertEquals(null, list.state.value.error)
        assertFalse(offline.state.value.showBanner)
    }
}
