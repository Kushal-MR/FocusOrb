package com.kushal.focusorb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * SessionStateStore — Globally accessible session state for the phone module.
 *
 * Written by [WatchListenerService] when the watch's session messages arrive,
 * read by [FocusShieldService] on every poll. Uses [SharedPreferences] so the
 * state survives process restarts.
 *
 * A session is active only until its expiry. The watch sends an expiry with
 * every start (planned end plus a grace period), so if the watch app dies
 * mid-session and never sends "ended", the phone stands down by itself instead
 * of policing the user — and showing its notification — indefinitely.
 */
object SessionStateStore {

    private const val TAG = "SessionState"
    private const val PREFS_NAME = "focusorb_session"
    private const val KEY_SESSION_ACTIVE = "is_session_active"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_PLANNED_MINUTES = "planned_minutes"

    /**
     * Expiry used when a start message carries no payload (a watch build from
     * before payloads existed): comfortably longer than the longest session.
     */
    private const val FALLBACK_LIFETIME_MS = 2 * 60 * 60 * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether a focus session is running right now. Called on every shield
     * poll — SharedPreferences reads are served from memory after the first
     * load, so this is cheap.
     */
    fun isSessionActive(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_SESSION_ACTIVE, false) &&
            System.currentTimeMillis() < p.getLong(KEY_EXPIRES_AT, 0L)
    }

    /** True when a session was marked active but its expiry has passed. */
    fun hasExpired(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_SESSION_ACTIVE, false) &&
            System.currentTimeMillis() >= p.getLong(KEY_EXPIRES_AT, 0L)
    }

    /** The current (or most recent) session's ID, or 0 if none. */
    fun currentSessionId(context: Context): Long = prefs(context).getLong(KEY_SESSION_ID, 0L)

    fun plannedMinutes(context: Context): Int = prefs(context).getInt(KEY_PLANNED_MINUTES, 0)

    /** Records a session start; [started] is null for a legacy empty payload. */
    fun startSession(context: Context, started: SessionMessage.Started?) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            putBoolean(KEY_SESSION_ACTIVE, true)
            putLong(KEY_SESSION_ID, started?.sessionId ?: now)
            putLong(KEY_EXPIRES_AT, started?.expiresAt ?: (now + FALLBACK_LIFETIME_MS))
            putInt(KEY_PLANNED_MINUTES, started?.plannedMinutes ?: 0)
        }
        Log.d(TAG, "Session started: $started")
    }

    fun endSession(context: Context) {
        prefs(context).edit { putBoolean(KEY_SESSION_ACTIVE, false) }
        Log.d(TAG, "Session ended")
    }
}
