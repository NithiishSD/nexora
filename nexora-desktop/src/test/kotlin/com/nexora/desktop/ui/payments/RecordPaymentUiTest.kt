package com.nexora.desktop.ui.payments

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordPaymentUiTest {

    @Test
    @DisplayName("FR-PAY-02 NFR-USE-02 record payment shows server allocations")
    fun recordPayment_showsServerAllocations() = runComposeUiTest {
        val server = MockServer()
        server.onJson("GET /finance/accounts", Fixtures.ACCOUNTS)
        server.onJson("GET /parties/5d0c", Fixtures.party())
        server.onJson("GET /customers/5d0c/outstanding", Fixtures.CUSTOMER_OUTSTANDING)
        server.onJson("GET /orders/4c2d", Fixtures.order(status = "CONFIRMED"))
        server.onJson("POST /payments", Fixtures.PAYMENT, 201)
        val koin = testKoin(server)
        koin.koin.get<SettingsStore>().apply { remember("payment.method", "UPI"); remember("payment.account.UPI", "b7e1") }
        setContent { WithKoin(koin) { RecordPaymentDialog("5d0c", "CUSTOMER", onDone = {}, onOpenPayment = {}) } }

        // Opened from the customer: party, method, account and date are filled; type the amount and press Enter.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("SBI Current")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("payment.amount").requestFocus()
        onNodeWithTag("payment.amount").performTextInput("45000.00")
        onNodeWithTag("payment.amount").performKeyInput { pressKey(Key.Enter) }

        // The result view shows the server's allocations, not anything computed from the 45000.00 typed.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Saved PAY-2026-0001")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("ORD-2026-0001 line 1")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("₹20,000.00")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("ORD-2026-0002 line 1")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("₹25,000.00")).fetchSemanticsNodes().isNotEmpty())
        koin.close()
    }
}
