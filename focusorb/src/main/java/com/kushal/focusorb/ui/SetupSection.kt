package com.kushal.focusorb.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * The three things the phone needs before it can referee a session.
 *
 * None of them can be granted silently — each is a trip to Settings — so the
 * dashboard shows them as a checklist and re-checks whenever the user returns.
 */
data class SetupStatus(
    /** "Usage access": lets the shield see which app is in front. */
    val usageAccess: Boolean,
    /** Required on Android 13+ to show the foreground-service notification. */
    val notifications: Boolean,
    /**
     * Exemption from battery optimisation. This is what allows the shield to
     * start from the background when the watch begins a session — Android 12+
     * refuses background foreground-service starts otherwise.
     */
    val batteryUnrestricted: Boolean
) {
    val isComplete: Boolean get() = usageAccess && notifications && batteryUnrestricted

    companion object {
        fun read(context: Context) = SetupStatus(
            usageAccess = hasUsageAccess(context),
            notifications = hasNotificationPermission(context),
            batteryUnrestricted = isBatteryUnrestricted(context)
        )
    }
}

/**
 * @param sessionActive A session is running right now, so the "ready" card
 *                      reports that instead of inviting the user to start one.
 */
@Composable
fun SetupSection(status: SetupStatus, context: Context, sessionActive: Boolean) {
    if (status.isComplete) {
        ShieldReadyCard(sessionActive)
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(AccentRed)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Finish setup",
                    color = AccentRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Focus Orb only sees which app is open — never what's on your screen.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SetupStep(
                    title = "Usage access",
                    detail = "To see which app is in the foreground.",
                    done = status.usageAccess,
                    onGrant = { context.openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
                SetupStep(
                    title = "Notifications",
                    detail = "Shows that a session is running.",
                    done = status.notifications,
                    onGrant = { context.openSettings(notificationSettingsIntent(context)) }
                )
                SetupStep(
                    title = "Unrestricted battery",
                    detail = "Lets your watch start a session while this app is closed.",
                    done = status.batteryUnrestricted,
                    onGrant = { context.openSettings(batteryExemptionIntent(context)) }
                )
            }
        }
    }
}

@Composable
private fun SetupStep(title: String, detail: String, done: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (done) AccentCyan else WarningAmber)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (done) {
            Text(text = "Done", color = TextSecondary, fontSize = 13.sp)
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningAmber,
                    contentColor = Color.Black
                )
            ) {
                Text(text = "Grant", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ShieldReadyCard(sessionActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(if (sessionActive) WarningAmber else AccentCyan)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (sessionActive) "Session in progress" else "Shield ready",
                    color = if (sessionActive) WarningAmber else AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (sessionActive) "Blocked apps will cost your orb health."
                           else "Start a session on your watch to begin.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ── Checks ───────────────────────────────────────────────────────────────

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Deprecated as of API 36 but still the documented way to read this op
        // on every version this app supports (minSdk 28).
        @Suppress("DEPRECATION")
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    // MODE_DEFAULT means "defer to the manifest permission", which some OEM
    // builds report instead of MODE_ALLOWED.
    return if (mode == AppOpsManager.MODE_DEFAULT) {
        context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBatteryUnrestricted(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

// ── Intents ──────────────────────────────────────────────────────────────

/**
 * App notification settings rather than a runtime prompt: after two denials
 * Android stops showing the prompt at all, and the button would silently do
 * nothing. Settings always works.
 */
private fun notificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

/**
 * Asks for the exemption directly with a one-tap system dialog, instead of
 * dropping the user into a list of every app on the phone to hunt for ours.
 */
@SuppressLint("BatteryLife")
private fun batteryExemptionIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

private fun Context.openSettings(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
