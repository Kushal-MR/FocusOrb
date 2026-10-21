package com.kushal.focusorb

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * WatchListenerService — Background receiver for watch session signals.
 *
 * Listens for "/session_started" and "/session_ended" messages from the
 * paired Wear OS device, persists the session state to [SessionStateStore],
 * and raises or lowers [FocusShieldService] to match.
 *
 * This service runs without any Activity — it wakes automatically when
 * a message arrives over the Wearable Data Layer, even if the phone app
 * is not in the foreground.
 */
class WatchListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchListener"
        private const val PATH_SESSION_STARTED = "/session_started"
        private const val PATH_SESSION_ENDED = "/session_ended"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        when (messageEvent.path) {
            PATH_SESSION_STARTED -> {
                Log.d(TAG, "SESSION STARTED signal from watch (node: ${messageEvent.sourceNodeId})")
                SessionStateStore.setSessionActive(this, true)
                startShield()
            }
            PATH_SESSION_ENDED -> {
                Log.d(TAG, "SESSION ENDED signal from watch (node: ${messageEvent.sourceNodeId})")
                SessionStateStore.setSessionActive(this, false)
                FocusShieldService.stop(this)
            }
        }
    }

    /**
     * We are in the background here, and Android 12+ only lets a background
     * app start a foreground service under specific exemptions. The one this
     * app relies on is the user exempting it from battery optimisation, which
     * the dashboard walks them through. Without it, the start is refused —
     * so catch that rather than crash, and leave a clear trail in the log.
     */
    private fun startShield() {
        try {
            FocusShieldService.start(this)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is a subclass.
            Log.e(TAG, "Could not start the shield from the background — is battery optimisation still on?", e)
        }
    }
}
