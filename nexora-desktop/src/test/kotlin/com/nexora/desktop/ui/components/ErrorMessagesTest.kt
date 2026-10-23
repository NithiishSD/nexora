package com.nexora.desktop.ui.components

import com.nexora.desktop.util.Strings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ErrorMessagesTest {

    /** The error-code catalogue of 05 section 2 (DOMAIN_RULES section 7 plus the plan-decision codes). */
    private val serverCodes = listOf(
        "VALIDATION_FAILED", "MALFORMED_REQUEST", "UNAUTHENTICATED", "TOKEN_EXPIRED", "INVALID_CREDENTIALS", "ACCESS_DENIED",
        "RESOURCE_NOT_FOUND", "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE", "DUPLICATE_BUSINESS_NUMBER", "DUPLICATE_RESOURCE",
        "DUPLICATE_REQUEST", "CONCURRENT_MODIFICATION", "INVALID_STATE_TRANSITION", "ORDER_LOCKED", "INSUFFICIENT_INVENTORY",
        "INSUFFICIENT_FUNDS", "PURCHASE_RECEIPT_EXCEEDED", "OUTSOURCE_RECEIPT_EXCEEDED", "PAYMENT_ALLOCATION_EXCEEDED",
        "INVALID_PAYMENT_ALLOCATION", "PRODUCTION_NOT_RECONCILED", "INVALID_PARTY_ROLE", "PARTY_INACTIVE",
        "INVALID_PRODUCT_FOR_OPERATION", "INVALID_PAYMENT_METHOD", "RATE_LIMITED", "INTERNAL_ERROR",
    )

    @Test
    @DisplayName("NFR-USE-04 every server error code has user message")
    fun everyServerErrorCode_hasUserMessage() {
        serverCodes.forEach { code ->
            assertTrue(Strings.has("error.$code"), "missing message for $code")
            // A message is a sentence for the owner, never the raw code.
            assertTrue(!ErrorMessages.message(code).contains(code), code)
        }
        assertEquals("Not enough stock for this.", ErrorMessages.message("INSUFFICIENT_INVENTORY"))
        assertEquals("Something went wrong (NEW_CODE). Please try again or contact support.", ErrorMessages.message("NEW_CODE"))
    }
}
