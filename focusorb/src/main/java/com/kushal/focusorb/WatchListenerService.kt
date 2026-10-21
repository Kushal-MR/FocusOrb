package com.kushal.focusorb

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * WatchListenerService — Background receiver for watch session signals.
 *
 * Decodes the "/session_started" and "/session_ended" messages from the paired
 * Wear OS device and hands them to [SessionSignalHandler].
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
                val started = SessionMessage.decodeStarted(messageEvent.data)
                Log.d(TAG, "SESSION STARTED from watch (node: ${messageEvent.sourceNodeId}): $started")
                SessionSignalHandler.onStarted(this, started)
            }
            PATH_SESSION_ENDED -> {
                val ended = SessionMessage.decodeEnded(messageEvent.data)
                Log.d(TAG, "SESSION ENDED from watch (node: ${messageEvent.sourceNodeId}): $ended")
                SessionSignalHandler.onEnded(this, ended)
            }
        }
    }
}
