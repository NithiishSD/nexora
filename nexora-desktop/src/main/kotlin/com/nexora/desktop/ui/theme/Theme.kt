package com.nexora.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The single light theme of the MVP (06 section 11 #9). Every text/background pair used here is checked by
 * AccessibilityUiTest to reach 4.5:1 contrast (NFR-USE-06).
 */
object AppColors {
    val primary = Color(0xFF1F4E79)       // deep blue: buttons, focus outline
    val onPrimary = Color.White
    val background = Color(0xFFFFFFFF)
    val onBackground = Color(0xFF1B1B1F)
    val surfaceVariant = Color(0xFFEEF1F5)  // sidebar, top bar
    val onSurfaceVariant = Color(0xFF2E3440)
    val primaryContainer = Color(0xFFD6E4F5)
    val onPrimaryContainer = Color(0xFF0B2540)
    val error = Color(0xFFB3261E)
    val muted = Color(0xFF5A5F69)          // "Last updated", small details
    val online = Color(0xFF1B7F3B)
    val slow = Color(0xFF8A5300)
    val offline = Color(0xFFB3261E)
    val negative = Color(0xFFB3261E)       // negative money in red (06 section 3.6)

    /** Text/background pairs the UI uses; the contrast test walks this list. */
    val textPairs: List<Pair<Color, Color>> = listOf(
        onBackground to background,
        onSurfaceVariant to surfaceVariant,
        onPrimary to primary,
        onPrimaryContainer to primaryContainer,
        error to background,
        muted to background,
        negative to background,
        online to background,
        slow to background,
    )

    /** WCAG contrast ratio between two colours (1:1 .. 21:1). */
    fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        // luminance() is a Float; adding the Double 0.05 already makes these Doubles.
        return if (l1 > l2) l1 / l2 else l2 / l1
    }
}

private val lightColors = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = AppColors.onPrimary,
    primaryContainer = AppColors.primaryContainer,
    onPrimaryContainer = AppColors.onPrimaryContainer,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.background,
    onSurface = AppColors.onBackground,
    surfaceVariant = AppColors.surfaceVariant,
    onSurfaceVariant = AppColors.onSurfaceVariant,
    error = AppColors.error,
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171),
)

/**
 * Applies the colours and the user's text size (100-200 %, NFR-USE-06). Changing `fontScale` scales every
 * `sp` text in the app at once; layouts wrap or scroll so nothing is clipped.
 */
@Composable
fun AppTheme(textScale: Float = 1f, darkMode: Boolean = false, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, textScale.coerceIn(1f, 2f))) {
        MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors, content = content)
    }
}
