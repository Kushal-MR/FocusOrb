package com.kushal.focusorb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * SessionStateStore — Globally accessible session state for the phone module.
 *
 * Acts as the bridge between [WatchListenerService] (which writes) and
 * [DistractionSniperService] (which reads). Uses [SharedPreferences] so
 * the state survives process restarts.
 *
 * Defaults to `false` (inactive) — the sniper sleeps until the watch
 * explicitly sends a "/session_started" signal.
 */
object SessionStateStore {

    private const val TAG = "SessionState"
    private const val PREFS_NAME = "focusorb_session"
    private const val KEY_SESSION_ACTIVE = "is_session_active"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns whether a focus session is currently active on the watch.
     * Called on the accessibility event hot path — SharedPreferences reads
     * are in-memory after the first load, so this is effectively O(1).
     */
    fun isSessionActive(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SESSION_ACTIVE, false)
    }

    /**
     * Updates the session state. Called by [WatchListenerService] when
     * a session signal arrives from the watch.
     */
    fun setSessionActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_SESSION_ACTIVE, active).apply()
        Log.d(TAG, "Session state updated: active=$active")
    }

    fun registerListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
