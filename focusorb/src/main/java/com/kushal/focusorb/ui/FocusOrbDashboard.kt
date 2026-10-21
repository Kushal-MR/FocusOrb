package com.kushal.focusorb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kushal.focusorb.BlocklistStore
import com.kushal.focusorb.InstalledApp
import com.kushal.focusorb.InstalledAppsRepository

// ── Color palette (minimalistic premium dark mode) ───────────────────────
// Internal so the other dashboard sections in ui/ draw from the same palette.
internal val SurfaceBlack       = Color(0xFF0A0A0C)
internal val CardSurface        = Color(0xFF141417)
internal val TextPrimary        = Color(0xFFF2F2F2)
internal val TextSecondary      = Color(0xFF8E8E93)
internal val AccentCyan         = Color(0xFFE0E0E0) // Minimalistic soft white/silver accent
internal val AccentRed          = Color(0xFFFF453A) // Apple-style soft destructive red
internal val WarningAmber       = Color(0xFFFF9F0A)
internal val DividerColor       = Color(0xFF1C1C1E)

/**
 * FocusOrbDashboard — The top-level Compose screen.
 *
 * Shows the setup checklist and the blocklist, and swaps to [AppPickerScreen]
 * to add apps. [BlocklistStore] is the source of truth; the in-memory set
 * here only mirrors it so the UI recomposes on change.
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

    // ── Blocklist ────────────────────────────────────────────────────
    var blocked by remember { mutableStateOf(BlocklistStore.getBlockedPackages(context)) }

    fun setBlocked(packageName: String, block: Boolean) {
        if (block) BlocklistStore.addPackage(context, packageName)
        else BlocklistStore.removePackage(context, packageName)
        blocked = BlocklistStore.getBlockedPackages(context)
    }

    // Loaded once in the background; null until ready.
    val installedApps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = InstalledAppsRepository.load(context)
    }
    val installedByPackage = remember(installedApps) {
        installedApps?.associateBy { it.packageName }.orEmpty()
    }

    var showPicker by rememberSaveable { mutableStateOf(false) }
    if (showPicker) {
        AppPickerScreen(
            apps = installedApps,
            blocked = blocked,
            onToggle = ::setBlocked,
            onDone = { showPicker = false }
        )
        return
    }

    // Blocked apps, labelled from the installed list where we can. An app that
    // has since been uninstalled stays listed by package so it can be removed.
    val blockedRows = remember(blocked, installedByPackage) {
        blocked.map { pkg ->
            val app = installedByPackage[pkg]
            BlockedRow(pkg, app?.label ?: pkg, app?.icon)
        }.sortedBy { it.label.lowercase() }
    }

    // Common time sinks, offered only if installed and not already blocked.
    val suggestions = remember(blocked, installedByPackage) {
        DEFAULT_DISTRACTION_APPS.mapNotNull { installedByPackage[it.packageName.lowercase()] }
            .filter { it.packageName !in blocked }
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
                    text = "Opening these apps during a focus session costs your orb health.",
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

        // ── Blocked apps ─────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("BLOCKED APPS", modifier = Modifier.weight(1f))
                TextButton(onClick = { showPicker = true }) {
                    Text(text = "Add apps", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (blockedRows.isEmpty()) {
            item {
                Text(
                    text = "Nothing blocked yet. Add the apps that pull you away from work.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            item {
                AppListCard(rowCount = blockedRows.size) { index ->
                    val row = blockedRows[index]
                    AppActionRow(
                        icon = row.icon,
                        label = row.label,
                        packageName = row.packageName,
                        actionLabel = "Remove",
                        actionColor = TextSecondary,
                        onAction = { setBlocked(row.packageName, false) }
                    )
                }
            }
        }

        // ── Suggestions ──────────────────────────────────────────────
        if (suggestions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("SUGGESTED")
            }
            item {
                AppListCard(rowCount = suggestions.size) { index ->
                    val app = suggestions[index]
                    AppActionRow(
                        icon = app.icon,
                        label = app.label,
                        packageName = app.packageName,
                        actionLabel = "Block",
                        actionColor = AccentRed,
                        onAction = { setBlocked(app.packageName, true) }
                    )
                }
            }
        }

        // ── Bottom breathing room ────────────────────────────────────
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

private data class BlockedRow(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}

/** A card holding rows separated by hairline dividers. */
@Composable
internal fun AppListCard(rowCount: Int, row: @Composable (index: Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            for (index in 0 until rowCount) {
                row(index)
                if (index < rowCount - 1) {
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── App Row ──────────────────────────────────────────────────────────────

@Composable
private fun AppActionRow(
    icon: ImageBitmap?,
    label: String,
    packageName: String,
    actionLabel: String,
    actionColor: Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon = icon, label = label)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onAction) {
            Text(text = actionLabel, color = actionColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
