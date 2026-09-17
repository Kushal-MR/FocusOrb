package com.kushal.focusorb.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * FocusOrb's Wear colour palette.
 *
 * Pulled directly from the visuals the app already draws, so the chrome and the
 * canvas read as one product: [primary] is the orb's mid-session cyan and
 * [secondary] is the golden white an Epic star collapses into.
 *
 * Backgrounds are true black rather than a dark grey — the watch is OLED, so
 * black pixels are physically off. That saves power and lets the orb's glow
 * bleed into the bezel with no visible panel edge.
 */
internal val FocusOrbColors = Colors(
    primary = Color(0xFF00BCD4),
    primaryVariant = Color(0xFF0097A7),
    secondary = Color(0xFFFFFACD),
    secondaryVariant = Color(0xFFD8D2A8),
    background = Color.Black,
    surface = Color(0xFF17181A),
    error = Color(0xFFFF5252),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B4BA),
    onError = Color.Black
)

@Composable
fun FocusOrbTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = FocusOrbColors,
        content = content
    )
}
