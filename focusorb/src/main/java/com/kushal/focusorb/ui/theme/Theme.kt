package com.kushal.focusorb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * True-black dark theme — always forced, no light mode.
 * Matches the premium Wear OS orb aesthetic.
 */
private val OrbDarkScheme = darkColorScheme(
    primary        = OrbCyan,
    secondary      = OrbAmber,
    tertiary       = OrbRed,
    background     = OrbBlack,
    surface        = OrbSurface,
    onPrimary      = Color.Black,
    onSecondary    = Color.Black,
    onTertiary     = Color.White,
    onBackground   = OrbTextPrim,
    onSurface      = OrbTextPrim
)

@Composable
fun FocusOrbTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OrbDarkScheme,
        typography = Typography,
        content = content
    )
}