package com.kushal.focusorb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app the user could choose to block. */
data class InstalledApp(
    /** Lowercased, matching how [BlocklistStore] stores package names. */
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)

/**
 * Lists the apps a user can actually open — anything with a launcher icon.
 *
 * Visibility comes from a `<queries>` entry for launcher intents in the
 * manifest, not the broad QUERY_ALL_PACKAGES permission: the shield only ever
 * needs to know about apps the user can launch, and Android 11+ keeps every
 * other package hidden from us.
 */
object InstalledAppsRepository {

    /** Icons are rasterised once at this size; rows draw them at 40dp. */
    private const val ICON_PX = 96

    /**
     * One app's label, falling back to its package name if it has been
     * uninstalled. A single lookup is cheap enough for the main thread.
     */
    fun labelFor(context: Context, packageName: String): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    /** Loads off the main thread — decoding a few hundred icons takes a moment. */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        activities
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                // An app can be uninstalled between the query and this lookup.
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    InstalledApp(
                        packageName = pkg.lowercase(),
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
