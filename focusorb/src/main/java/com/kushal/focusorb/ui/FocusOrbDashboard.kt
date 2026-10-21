package com.kushal.focusorb.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kushal.focusorb.BlocklistStore

// ── Color palette (minimalistic premium dark mode) ───────────────────────
// Internal so the other dashboard sections in ui/ draw from the same palette.
internal val SurfaceBlack       = Color(0xFF0A0A0C)
internal val CardSurface        = Color(0xFF141417)
internal val CardBorder         = Color(0xFF1A1A1A)
internal val TextPrimary        = Color(0xFFF2F2F2)
internal val TextSecondary      = Color(0xFF8E8E93)
internal val AccentCyan         = Color(0xFFE0E0E0) // Minimalistic soft white/silver accent
internal val AccentRed          = Color(0xFFFF453A) // Apple-style soft destructive red
internal val WarningAmber       = Color(0xFFFF9F0A)
internal val SwitchTrackOff     = Color(0xFF1C1C1E)
internal val DividerColor       = Color(0xFF1C1C1E)

/**
 * FocusOrbDashboard — The top-level Compose screen.
 *
 * Renders the permission gate (if accessibility is disabled) and the
 * blocklist manager. All state is driven by [BlocklistStore].
 */
@Composable
fun FocusOrbDashboard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Setup status ─────────────────────────────────────────────────
    // Re-checked every time the Activity resumes (e.g., returning from Settings).
    var setupStatus by remember { mutableStateOf(SetupStatus.read(context)) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            setupStatus = SetupStatus.read(context)
        }
    }

    // ── Blocklist toggle states (keyed by package name) ──────────────
    val toggleStates = remember { mutableStateMapOf<String, Boolean>() }

    // Seed from persistence on first composition
    LaunchedEffect(Unit) {
        val blocked = BlocklistStore.getBlockedPackages(context)
        DEFAULT_DISTRACTION_APPS.forEach { app ->
            toggleStates[app.packageName] = app.packageName.lowercase() in blocked
        }
    }

    // ── Layout ───────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────
        item {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "FOCUS ORB",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distraction Shield",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select the apps you want to block during focus sessions.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }

        // ── Setup checklist ──────────────────────────────────────────
        item {
            SetupSection(status = setupStatus, context = context)
        }

        // ── Section label ────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BLOCKED APPLICATIONS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        // ── App rows ─────────────────────────────────────────────────
        items(
            items = DEFAULT_DISTRACTION_APPS,
            key = { it.packageName }
        ) { app ->
            val isBlocked = toggleStates[app.packageName] ?: false

            AppRow(
                app = app,
                isBlocked = isBlocked,
                onToggle = { nowBlocked ->
                    toggleStates[app.packageName] = nowBlocked
                    if (nowBlocked) {
                        BlocklistStore.addPackage(context, app.packageName)
                    } else {
                        BlocklistStore.removePackage(context, app.packageName)
                    }
                }
            )

            if (app != DEFAULT_DISTRACTION_APPS.last()) {
                HorizontalDivider(
                    color = DividerColor,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // ── Bottom breathing room ────────────────────────────────────
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

// ── App Row ──────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app: DistractionApp,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.displayName,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = app.packageName,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // Toggle
        Switch(
            checked = isBlocked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentRed,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SwitchTrackOff,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
