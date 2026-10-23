package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.WithKoin
import com.nexora.desktop.support.testKoin
import com.nexora.desktop.ui.shell.Sidebar
import com.nexora.desktop.ui.shell.TopBar
import com.nexora.desktop.api.ServerStatus
import com.nexora.desktop.ui.theme.AppColors
import com.nexora.desktop.ui.theme.AppTheme
import com.nexora.desktop.viewmodel.common.ListState
import com.nexora.desktop.viewmodel.shell.Section
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AccessibilityUiTest {

    private data class Row3(val number: String, val party: String, val amount: String)

    private val sampleList = ListState(
        page = PageResponse(
            content = listOf(Row3("PUR-2026-0012", "Ramesh Silks, Salem", "₹12,50,000.50"), Row3("PUR-2026-0013", "Kumar Silks", "₹999.00")),
            page = 0, size = 20, totalElements = 312, totalPages = 16,
        ),
    )

    /** A list screen as the owner sees it: top bar, full sidebar, table with footer, and an open form. */
    @androidx.compose.runtime.Composable
    private fun ListScreenSample() {
        Column(Modifier.requiredSize(1366.dp, 768.dp)) {
            TopBar(ServerStatus.ONLINE, 3, "Owner", {}, {})
            Row {
                Sidebar(Section.entries, Section.PURCHASES, collapsed = false, onOpen = {}, onToggle = {})
                Column(Modifier.width(640.dp)) {
                    DataTable(
                        sampleList,
                        listOf(
                            textColumn("Number", sortField = "purchaseNumber") { it.number },
                            textColumn("Supplier", 2f) { it.party },
                            textColumn("Outstanding", numeric = true) { it.amount },
                        ),
                        onOpen = {}, onSort = {}, onPage = {}, onSize = {}, emptyMessage = "No purchases yet",
                    )
                }
                Column(Modifier.width(500.dp)) {
                    FormPanel("New purchase", "Save", false, false, null, {}, {}) {
                        MoneyField("Rate (₹/kg)", "850.00", {})
                        WeightField("Ordered weight (kg)", "250.000", {})
                        DateField("Purchase date", "08-10-2026", {})
                        Segmented("Payment type", listOf("CREDIT" to "Credit", "CASH" to "Cash"), "CREDIT", {})
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("NFR-USE-06 text scale200 no label is clipped")
    fun textScale200_noLabelIsClipped() = runComposeUiTest {
        val koin = testKoin(MockServer())
        setContent { WithKoin(koin) { AppTheme(textScale = 2f) { ListScreenSample() } } }
        waitForIdle()

        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).fetchSemanticsNodes()
        assertTrue(texts.size > 20)
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                // Clipped = lines cut off at the bottom, a line ending in "...", or a line wider than its box.
                // At 200 % labels must wrap to more lines instead.
                val text = layout.layoutInput.text
                val isEditableInput = node.config.getOrNull(SemanticsProperties.EditableText) != null
                if (!isEditableInput) {
                    assertTrue(!layout.didOverflowHeight, "cut off at the bottom: $text")
                }
                (0 until layout.lineCount).forEach { line ->
                    assertTrue(!layout.isLineEllipsized(line), "ellipsised: $text")
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1, "wider than its box: $text")
                }
            }
        }
        koin.close()
    }

    @Test
    @DisplayName("NFR-USE-06 icon only buttons have accessible names")
    fun iconOnlyButtons_haveAccessibleNames() = runComposeUiTest {
        val koin = testKoin(MockServer())
        setContent {
            WithKoin(koin) {
                AppTheme {
                    Column {
                        Sidebar(Section.entries, Section.PURCHASES, collapsed = true, onOpen = {}, onToggle = {})
                        DateField("Date", "08-10-2026", {})
                    }
                }
            }
        }
        waitForIdle()
        val clickables = onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickables.isNotEmpty())
        clickables.forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            // A button whose visible text is a symbol or an abbreviation must carry a spoken name.
            if (text.length <= 3) assertTrue(description.isNotEmpty(), "button '$text' has no accessible name")
        }
        koin.close()
    }

    @Test
    @DisplayName("NFR-USE-06 theme colours meet contrast4 5 to1")
    fun themeColours_meetContrast4_5To1() {
        AppColors.textPairs.forEach { (text, background) ->
            assertTrue(AppColors.contrast(text, background) >= 4.5, "text $text on $background")
        }
        ChipColour.entries.forEach { chip ->
            assertTrue(AppColors.contrast(chip.text, chip.background) >= 4.5, "chip $chip")
        }
    }
}
