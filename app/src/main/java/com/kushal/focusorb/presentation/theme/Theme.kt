package com.kushal.focusorb.presentation.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.ReduceMotion
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

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun FocusOrbTheme(
    content: @Composable () -> Unit
) {
    // Supply the reduce-motion preference ourselves rather than letting Wear
    // Compose read it. Its own read (ScalingLazyColumn and friends) is not
    // guarded, and on some Wear OS 5 builds — seen on a OnePlus watch — the
    // "reduce_motion" setting throws SecurityException for apps targeting
    // API 35+. Uncaught, that crashed the app on launch.
    val context = LocalContext.current
    val reduceMotion = remember { readReduceMotion(context) }
    val reduceMotionLocal = remember(reduceMotion) {
        object : ReduceMotion {
            @Composable
            override fun enabled(): Boolean = reduceMotion
        }
    }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotionLocal) {
        MaterialTheme(
            colors = FocusOrbColors,
            content = content
        )
    }
}

/** The system reduce-motion flag, or false where the platform won't let us read it. */
private fun readReduceMotion(context: Context): Boolean =
    try {
        Settings.Global.getInt(context.contentResolver, "reduce_motion", 0) == 1
    } catch (e: SecurityException) {
        false
    }
