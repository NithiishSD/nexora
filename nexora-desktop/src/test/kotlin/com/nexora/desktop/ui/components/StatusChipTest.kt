package com.nexora.desktop.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.nexora.desktop.util.Strings
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StatusChipTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("NFR-USE-06 status shown as text not colour only")
    fun statusShownAsTextNotColourOnly() {
        // Every coloured status has its business wording (06 section 8), never the raw enum name.
        STATUS_COLOURS.forEach { (type, values) ->
            values.keys.forEach { value ->
                assertTrue(Strings.has("enum.$type.$value"), "no text for $type.$value")
                assertTrue(enumLabel(type, value) != value, "$type.$value shows the code")
            }
        }
        // And the chip really draws that text.
        runComposeUiTest {
            setContent { StatusChip("OrderStatus", "IN_PROGRESS") }
            onNodeWithText("In work").assertExists()
        }
    }
}
