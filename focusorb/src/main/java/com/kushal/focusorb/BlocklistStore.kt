package com.kushal.focusorb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * BlocklistStore — Lightweight persistence layer for the distraction blocklist.
 *
 * Wraps [SharedPreferences] to store a `Set<String>` of forbidden package names.
 * This is the single source of truth that both the UI (Compose dashboard) and
 * the background service ([DistractionSniperService]) read from.
 *
 * Design notes:
 *  - Uses `getStringSet` / `putStringSet` for atomic set persistence.
 *  - Returns defensive copies to prevent SharedPreferences mutation bugs.
 *  - All writes are `apply()` (async) — safe for UI-thread calls.
 */
object BlocklistStore {

    private const val PREFS_NAME = "focusorb_blocklist"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current set of blocked package names.
     * Always returns a new [HashSet] to avoid the SharedPreferences
     * "do not modify the returned set" contract violation.
     */
    fun getBlockedPackages(context: Context): Set<String> {
        return HashSet(prefs(context).getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet())
    }

    /**
     * Adds a package name to the blocklist.
     */
    fun addPackage(context: Context, packageName: String) {
        val current = getBlockedPackages(context).toMutableSet()
        current.add(packageName.lowercase())
        prefs(context).edit { putStringSet(KEY_BLOCKED_PACKAGES, current) }
    }

    /**
     * Removes a package name from the blocklist.
     */
    fun removePackage(context: Context, packageName: String) {
        val current = getBlockedPackages(context).toMutableSet()
        current.remove(packageName.lowercase())
        prefs(context).edit { putStringSet(KEY_BLOCKED_PACKAGES, current) }
    }

    /**
     * Checks if a specific package is currently blocked.
     */
    fun isBlocked(context: Context, packageName: String): Boolean {
        return packageName.lowercase() in getBlockedPackages(context)
    }
}
