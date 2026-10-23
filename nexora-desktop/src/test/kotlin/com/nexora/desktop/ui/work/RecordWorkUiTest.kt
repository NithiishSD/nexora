package com.nexora.desktop.ui.work

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import com.nexora.desktop.util.SettingsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordWorkUiTest {

    @Test
    @DisplayName("FR-WORK-01 record work keeps worker for next entry")
    fun recordWork_keepsWorkerForNextEntry() = runComposeUiTest {
        val server = MockServer()
        server.onJson("GET /parties/77ab", Fixtures.party(id = "77ab", roles = "\"WORKER\"").replace("Kumar Silks", "Selvi"))
        server.onJson(
            "POST /work-records",
            """{"id":"w1","worker":{"id":"77ab","name":"Selvi"},"workDate":"2026-10-08","workType":"ROLLING","hours":8.00,"rate":100.00,"amount":800.00,"status":"RECORDED"}""",
            201,
        )
        val koin = testKoin(server)
        // The worker last used on this computer is pre-selected.
        koin.koin.get<SettingsStore>().remember("work.worker", "77ab")
        setContent { WithKoin(koin) { RecordWorkPanel(null, onClose = {}) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Selvi")).fetchSemanticsNodes().isNotEmpty() }

        // First entry: 8 hours, Enter.
        onNodeWithTag("work.hours").requestFocus()
        onNodeWithTag("work.hours").performTextInput("8.00")
        onNodeWithTag("work.hours").performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Saved for Selvi: ₹800.00. Enter the next one.")).fetchSemanticsNodes().isNotEmpty() }

        // The form stays open with Selvi kept and the hours cleared; the second entry needs only the hours.
        onNodeWithTag("work.hours").requestFocus()
        onNodeWithTag("work.hours").performTextInput("6.50")
        onNodeWithTag("work.hours").performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { server.count("POST /work-records") == 2 }
        assertEquals(
            """{"workerId":"77ab","workDate":"2026-10-08","workType":"ROLLING","hours":6.50}""",
            server.last("POST /work-records").body,
        )
        koin.close()
    }
}
