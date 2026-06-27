package com.kushal.focusorb

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * WatchListenerService — Background receiver for watch session signals.
 *
 * Listens for "/session_started" and "/session_ended" messages from the
 * paired Wear OS device and persists the session state to [SessionStateStore].
 *
 * The [DistractionSniperService] reads from [SessionStateStore] on every
 * accessibility event to decide whether to monitor or sleep.
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
            }
            PATH_SESSION_ENDED -> {
                Log.d(TAG, "SESSION ENDED signal from watch (node: ${messageEvent.sourceNodeId})")
                SessionStateStore.setSessionActive(this, false)
            }
        }
    }
}
