package com.nexora.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import kotlinx.coroutines.delay

/** One choice in a picker: what is stored (id) and what the user reads (business number, name, detail). */
data class PickOption(val id: String, val label: String, val detail: String? = null)

/**
 * Search-as-you-type picker (06 section 6): waits 300 ms after the last key, needs 2 characters, and a newer
 * search cancels the older one (LaunchedEffect restarts on every change). Arrow keys move, Enter picks.
 */
@Composable
fun EntityPicker(
    label: String,
    selected: PickOption?,
    search: suspend (String) -> ApiResult<List<PickOption>>,
    onPick: (PickOption) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    var text by remember(selected) { mutableStateOf(selected?.label.orEmpty()) }
    var results by remember { mutableStateOf<List<PickOption>>(emptyList()) }
    var highlighted by remember { mutableIntStateOf(0) }
    var searchError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text == selected?.label || text.trim().length < ListQuery.MIN_SEARCH) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(PagedListViewModel.SEARCH_DEBOUNCE_MILLIS)
        when (val found = search(text.trim())) {
            is ApiResult.Success -> { results = found.value; highlighted = 0; searchError = null }
            is ApiResult.Failure -> searchError = ErrorMessages.message(found.error)
        }
    }
    val pick = { option: PickOption ->
        onPick(option)
        text = option.label
        results = emptyList()
    }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(100) },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error != null || searchError != null,
            supportingText = (error ?: searchError ?: selected?.detail)?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || results.isEmpty()) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { highlighted = (highlighted + 1).coerceAtMost(results.lastIndex); true }
                        Key.DirectionUp -> { highlighted = (highlighted - 1).coerceAtLeast(0); true }
                        Key.Enter -> { pick(results[highlighted]); true }
                        Key.Escape -> { results = emptyList(); true }
                        else -> false
                    }
                }
                .let { if (tag != null) it.testTag(tag) else it },
        )
        if (results.isNotEmpty()) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    results.forEachIndexed { index, option ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                .background(if (index == highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { pick(option) }
                                .padding(8.dp),
                        ) {
                            Text(option.label)
                            option.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
