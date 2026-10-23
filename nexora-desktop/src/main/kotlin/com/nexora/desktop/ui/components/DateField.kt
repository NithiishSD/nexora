package com.nexora.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import org.koin.compose.koinInject
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Date entry as `dd-MM-yyyy`; also accepts `ddMMyyyy` and `t` for today, which are rewritten to `dd-MM-yyyy`
 * when the field loses focus. `Alt+Down` (or the button) opens a month calendar (06 section 6).
 * The view-model keeps the text and parses it with [Dates.parseInput].
 */
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    tag: String? = null,
) {
    val clock: Clock = koinInject()
    var calendarOpen by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(10)) },
            label = { Text(label) },
            placeholder = { Text(s("date.placeholder")) },
            isError = error != null,
            supportingText = error?.let { { FieldError(it) } },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = { calendarOpen = true }, modifier = Modifier.semantics { contentDescription = s("date.openCalendar") }) {
                    Text(s("date.calendarSymbol"))
                }
            },
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focus ->
                    if (!focus.isFocused) Dates.parseInput(value, clock)?.let { onValueChange(Dates.format(it)) }
                }
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.isAltPressed && it.key == Key.DirectionDown) {
                        calendarOpen = true
                        true
                    } else {
                        false
                    }
                }
                .let { if (tag != null) it.testTag(tag) else it },
        )
        DropdownMenu(expanded = calendarOpen, onDismissRequest = { calendarOpen = false }) {
            MonthCalendar(Dates.parseInput(value, clock) ?: Dates.today(clock)) { picked ->
                onValueChange(Dates.format(picked))
                calendarOpen = false
            }
        }
    }
}

@Composable
private fun MonthCalendar(initial: LocalDate, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    Column(Modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text(s("date.previousMonth")) }
            Text(s("date.monthTitle", s("month.${month.monthValue}"), month.year))
            TextButton(onClick = { month = month.plusMonths(1) }) { Text(s("date.nextMonth")) }
        }
        // Weeks start on Monday; blank cells before the 1st.
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    if (day == null) {
                        Text("", Modifier.size(40.dp))
                    } else {
                        TextButton(onClick = { onPick(day) }, modifier = Modifier.size(40.dp)) { Text(day.dayOfMonth.toString()) }
                    }
                }
            }
        }
    }
}
