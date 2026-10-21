package com.kushal.focusorb

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * ForegroundAppTracker — answers "which app is the user looking at right now?"
 *
 * Built on [UsageStatsManager] rather than an AccessibilityService. Usage
 * events only carry a package name and an event type: there is no way to read
 * what is on screen. That distinction matters in practice — UPI and banking
 * apps refuse to run while a screen-reading accessibility service is enabled,
 * which is what this class replaced. Requires the "Usage access" special
 * permission (PACKAGE_USAGE_STATS), granted by the user in Settings.
 *
 * Designed to be polled. Each call reads only the events since the previous
 * call, so a poll costs a handful of events rather than a history scan.
 */
class ForegroundAppTracker(context: Context) {

    companion object {
        /**
         * How far back the first poll looks. Long enough to find an app that
         * was already open when the session started, which has no fresh
         * "resumed" event of its own.
         */
        private const val INITIAL_LOOKBACK_MS = 60 * 60 * 1000L

        /**
         * Each poll re-reads a small window of the previous one. Usage events
         * can land in the log slightly after their timestamp, and without the
         * overlap one arriving late would be skipped forever. Re-reading is
         * harmless: events replay in order, so the newest still wins.
         */
        private const val QUERY_OVERLAP_MS = 2_000L
    }

    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private var lastQueryTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
    private var lastResumedPackage: String? = null

    // Reused across polls — UsageEvents fills it in place.
    private val event = UsageEvents.Event()

    /**
     * Returns the lowercase package name of the app in the foreground, or null
     * when nothing should count: the screen is off or the phone is locked.
     *
     * The lock check is not cosmetic. The last resumed app stays "last" after
     * the screen goes dark, so without it, putting your phone down with
     * Instagram open would keep damaging the orb from your pocket.
     */
    fun currentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(lastQueryTime, now)
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (isForegroundEvent(event.eventType)) {
                lastResumedPackage = event.packageName
            }
        }
        lastQueryTime = now - QUERY_OVERLAP_MS

        if (!power.isInteractive || keyguard.isKeyguardLocked) return null
        return lastResumedPackage?.lowercase()
    }

    private fun isForegroundEvent(type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            type == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
}
