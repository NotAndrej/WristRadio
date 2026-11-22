package com.notandrej.wristradio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * A single, deliberately dark palette: Wear OS watches are OLED, so the
 * interface is near-black with one cool accent for interactive text and one
 * warm accent for the highlight day in the launcher glyph.
 */
private val WristRadioColors = Colors(
    primary = Color(0xFF9CD0FF),
    primaryVariant = Color(0xFF5AA9E6),
    secondary = Color(0xFFFFD166),
    secondaryVariant = Color(0xFFE0A800),
    background = Color(0xFF06121A),
    surface = Color(0xFF0D1B24),
    error = Color(0xFFFF5A5F),
    onPrimary = Color(0xFF06212F),
    onSecondary = Color(0xFF3A2A00),
    onBackground = Color(0xFFE6F1F8),
    onSurface = Color(0xFFE6F1F8),
    onSurfaceVariant = Color(0xFFB6C6D1),
    onError = Color(0xFF2A0507),
)

@Composable
fun WristRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = WristRadioColors, content = content)
}
