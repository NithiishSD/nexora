package com.nexora.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.ui.theme.AppColors
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf
import java.time.Instant

/**
 * The view-model of the current screen, created by Koin and kept in the navigation entry's store.
 * [params] are passed to the constructor (for example the record id of a detail screen).
 */
@Composable
inline fun <reified VM : ViewModel> screenViewModel(vararg params: Any?): VM {
    val koin = getKoin()
    return viewModel(key = VM::class.qualifiedName + params.joinToString()) { koin.get<VM> { parametersOf(*params) } }
}

/** Screen title with a Back button for detail screens and the screen's actions on the right. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text(s("common.backShortcut")) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/**
 * Read error handling of 06 section 3.4: the banner with Retry, and when older data is still on screen a grey
 * "Last updated hh:mm" label so the owner knows the figures may be stale.
 */
@Composable
fun LoadBanner(error: ApiError?, lastUpdated: Instant?, onRetry: () -> Unit) {
    if (error == null) return
    Column(Modifier.padding(bottom = 8.dp)) {
        ErrorBanner(error, onRetry = onRetry)
        if (lastUpdated != null) {
            Text(s("common.lastUpdated", Dates.formatTime(lastUpdated)), color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Reloads when F5 was pressed (the navigation's reload counter changed); the first composition is skipped. */
@Composable
fun ReloadOn(tick: Int, reload: () -> Unit) {
    val current by rememberUpdatedState(reload)
    LaunchedEffect(tick) { if (tick > 0) current() }
}

/**
 * A small table that is not paged (lines of one record: receipts, allocations, history). Each row may open
 * something; numbers given by [numeric] column indexes are right-aligned.
 */
@Composable
fun StaticTable(
    headers: List<String>,
    rows: List<List<String>>,
    numeric: Set<Int> = emptySet(),
    onRowClick: ((Int) -> Unit)? = null,
    trailing: (@Composable (Int) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = if (i in numeric) TextAlign.End else TextAlign.Start)
            }
            if (trailing != null) Spacer(Modifier.width(140.dp))
        }
        if (rows.isEmpty()) Text(s("table.none"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        rows.forEachIndexed { r, cells ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .let { if (onRowClick != null) it.clickable { onRowClick(r) } else it }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cells.forEachIndexed { i, c ->
                    Text(c, Modifier.weight(1f), textAlign = if (i in numeric) TextAlign.End else TextAlign.Start)
                }
                if (trailing != null) Row(Modifier.width(140.dp)) { trailing(r) }
            }
            HorizontalDivider()
        }
    }
}
