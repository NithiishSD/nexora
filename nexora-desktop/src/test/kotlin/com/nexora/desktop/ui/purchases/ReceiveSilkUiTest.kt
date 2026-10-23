package com.nexora.desktop.ui.purchases

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.support.Fixtures
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReceiveSilkUiTest {

    @Test
    @DisplayName("FR-PROC-08 FR-PROC-19 receive silk keyboard only flow")
    fun receiveSilk_keyboardOnlyFlow() = runComposeUiTest {
        val server = MockServer()
        server.onJson("GET /purchases/8a1b", Fixtures.purchase())
        server.onJson(
            "POST /purchases/8a1b/receipts",
            Fixtures.purchase(received = "160.000", accepted = "155.000", remaining = "90.000"),
            201,
        )
        val koin = testKoin(server)
        var saved: PurchaseResponse? = null
        setContent { WithKoin(koin) { ReceiveSilkDialog("8a1b", onSaved = { saved = it }, onCancel = {}) } }

        // The remaining weight comes from the server's purchase.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Remaining to receive: 170.000 kg")).fetchSemanticsNodes().isNotEmpty() }

        // Keyboard only: focus a field, type, move on; Enter saves.
        onNodeWithTag("receipt.received").requestFocus()
        onNodeWithTag("receipt.received").performTextReplacement("80.000")
        onNodeWithTag("receipt.accepted").requestFocus()
        onNodeWithTag("receipt.accepted").performTextReplacement("75.000")
        onNodeWithTag("receipt.rejected").requestFocus()
        onNodeWithTag("receipt.rejected").performTextReplacement("5.000")
        onNodeWithTag("receipt.rejected").performKeyInput { pressKey(Key.Enter) }

        waitUntil(timeoutMillis = 5_000) { saved != null }
        val request = server.last("POST /purchases/8a1b/receipts")
        assertEquals("""{"receiptDate":"2026-10-08","receivedWeightKg":80.000,"acceptedWeightKg":75.000,"rejectedWeightKg":5.000}""", request.body)
        assertNotNull(request.headers["Idempotency-Key"])
        // The dialog hands over the server's updated purchase; nothing is recomputed.
        assertEquals("90.000", saved!!.remainingWeightKg.toPlainString())
        koin.close()
    }
}
