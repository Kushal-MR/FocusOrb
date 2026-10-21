package com.kushal.focusorb

import android.content.Context
import android.util.Log

/**
 * What the phone does when the watch starts or ends a session.
 *
 * Kept apart from [WatchListenerService] so it has no dependency on the
 * Wearable message types — which also lets the same code be driven without a
 * paired watch when testing.
 */
object SessionSignalHandler {

    private const val TAG = "SessionSignal"

    fun onStarted(context: Context, started: SessionMessage.Started?) {
        SessionStateStore.startSession(context, started)
        SessionHistoryStore.onSessionStarted(
            context,
            sessionId = SessionStateStore.currentSessionId(context),
            plannedMinutes = SessionStateStore.plannedMinutes(context)
        )
        startShield(context)
    }

    fun onEnded(context: Context, ended: SessionMessage.Ended?) {
        // An "ended" for some earlier session — say, one delayed in transit
        // past the start of the next — must not cut short the one running now.
        if (ended != null && ended.sessionId != SessionStateStore.currentSessionId(context)) {
            Log.w(TAG, "Ignoring stale end for session ${ended.sessionId}")
            return
        }
        SessionStateStore.endSession(context)
        FocusShieldService.stop(context)
        recordOutcome(context, ended)
    }

    /**
     * Closes the history entry. A pause leaves it open — the session isn't
     * over, and resuming continues the same entry. An empty payload from an
     * older watch build carries no outcome, so it is recorded as cancelled.
     */
    private fun recordOutcome(context: Context, ended: SessionMessage.Ended?) {
        val outcome = when (ended?.outcome) {
            SessionMessage.Outcome.PAUSED -> return
            SessionMessage.Outcome.COMPLETED -> SessionHistoryStore.Outcome.COMPLETED
            SessionMessage.Outcome.SHATTERED -> SessionHistoryStore.Outcome.SHATTERED
            SessionMessage.Outcome.CANCELLED, null -> SessionHistoryStore.Outcome.CANCELLED
        }
        SessionHistoryStore.onSessionEnded(context, SessionStateStore.currentSessionId(context), outcome)
    }

    /**
     * This runs in the background, and Android 12+ only lets a background app
     * start a foreground service under specific exemptions. The one this app
     * relies on is the user exempting it from battery optimisation, which the
     * dashboard walks them through. Without it, the start is refused — so
     * catch that rather than crash, and leave a clear trail in the log.
     */
    private fun startShield(context: Context) {
        try {
            FocusShieldService.start(context)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is a subclass.
            Log.e(TAG, "Could not start the shield from the background — is battery optimisation still on?", e)
        }
    }
}
