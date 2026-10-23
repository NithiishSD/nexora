package com.nexora.desktop.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.shell.Section

fun sectionIcon(section: Section): String = when (section) {
    Section.DASHBOARD -> "📊"
    Section.PARTIES -> "👥"
    Section.PURCHASES -> "📥"
    Section.INVENTORY -> "📦"
    Section.ORDERS -> "📑"
    Section.PAYMENTS -> "💳"
    Section.PRODUCTION -> "🏭"
    Section.OUTSOURCING -> "🔄"
    Section.WORK -> "🔨"
    Section.MONEY -> "💰"
    Section.EXPENSES -> "🧾"
    Section.LOANS -> "🏦"
    Section.CHITS -> "🪙"
    Section.REPORTS -> "📈"
    Section.DOCUMENTS -> "📁"
    Section.SETTINGS -> "⚙️"
}

/** Modern 210 px sidebar, or a 64 px rail of icons with tooltips when collapsed (06 section 2). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(sections: List<Section>, current: Section?, collapsed: Boolean, onOpen: (Section) -> Unit, onToggle: () -> Unit) {
    Column(
        Modifier.width(if (collapsed) 64.dp else 210.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp, horizontal = if (collapsed) 4.dp else 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (!collapsed) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s("shell.sidebarHeader").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 6.dp),
                )
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Text(s("shell.toggleSidebar"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                ) {
                    Surface(
                        onClick = onToggle,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.heightIn(min = 32.dp).widthIn(min = 32.dp).semantics { contentDescription = s("shell.toggleSidebar") },
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(s("shell.collapseSymbol"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Text(s("shell.toggleSidebar"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                ) {
                    Surface(
                        onClick = onToggle,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.heightIn(min = 36.dp).widthIn(min = 36.dp).semantics { contentDescription = s("shell.toggleSidebar") },
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(s("shell.expandSymbol"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        sections.forEachIndexed { index, section ->
            val label = s(section.labelKey)
            val icon = sectionIcon(section)
            // Alt+1..9 reach the first nine entries; the number is part of the accessible name.
            val shortcut = if (index < 9) s("shell.altShortcut", index + 1) else ""
            val selected = section == current
            val entry: @Composable () -> Unit = {
                Surface(
                    onClick = { onOpen(section) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp)
                        .semantics { contentDescription = label + shortcut },
                ) {
                    if (!collapsed) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(icon, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(icon, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (collapsed) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Text(label + shortcut, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                ) {
                    entry()
                }
            } else {
                entry()
            }
        }
    }
}
