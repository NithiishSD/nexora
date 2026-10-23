package com.nexora.desktop.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay

/**
 * Global keyboard shortcuts of 06 section 1.1. Returns true when the key was used, so it does not also reach
 * the focused field. Shortcuts for a missing permission show the "Not allowed" hint instead (navigation decides).
 */
fun handleGlobalShortcut(event: KeyEvent, nav: NavigationViewModel): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed
    val shift = event.isShiftPressed
    return when {
        ctrl && shift -> when (event.key) {
            Key.P -> { nav.show(Overlay.RecordPayment()); true }
            Key.R -> { nav.show(Overlay.ReceiveSilk()); true }
            Key.E -> { nav.show(Overlay.RecordExpense); true }
            Key.W -> { nav.show(Overlay.RecordWork()); true }
            Key.O -> { nav.show(Overlay.OrderForm()); true }
            Key.U -> { nav.show(Overlay.NewPurchase); true }
            else -> false
        }
        ctrl -> when (event.key) {
            Key.N -> { nav.newRecord(); true }
            Key.F -> { nav.focusSearch(); true }
            Key.L -> { nav.show(Overlay.ConfirmLogout); true }
            Key.B -> { nav.toggleSidebar(); true }
            else -> false
        }
        event.isAltPressed -> when (event.key) {
            Key.DirectionLeft -> { nav.back(); true }
            in digitKeys -> { nav.openNumber(digitKeys.indexOf(event.key) + 1); true }
            else -> false
        }
        event.key == Key.F5 -> { nav.reload(); true }
        else -> false
    }
}

private val digitKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
