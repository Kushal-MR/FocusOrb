package com.kushal.focusorb.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * PhoneBridgeManager — Watch-to-phone session signaling.
 *
 * Sends "/session_started" and "/session_ended" messages to all connected
 * phone nodes so the phone's FocusShieldService knows when to activate and
 * deactivate monitoring. Payloads are built by [SessionMessage].
 *
 * All I/O runs on [Dispatchers.IO] — safe to call from [viewModelScope].
 */
object PhoneBridgeManager {

    private const val TAG = "PhoneBridge"

    const val PATH_SESSION_STARTED = "/session_started"
    const val PATH_SESSION_ENDED = "/session_ended"

    /**
     * Sends go out one at a time, in the order they were requested.
     *
     * Each send is launched in its own coroutine, so without this a quick
     * pause-then-resume could race and deliver "started" before "ended" —
     * leaving the phone convinced the session is over while the watch runs.
     * [Mutex] is fair, so waiters are served first-come, first-served, and the
     * lock is taken before any suspension so call order is preserved.
     */
    private val sendLock = Mutex()

    /** Tells the phone to START monitoring for distractions. */
    suspend fun sendSessionStarted(context: Context, payload: ByteArray) {
        broadcastMessage(context, PATH_SESSION_STARTED, payload)
    }

    /** Tells the phone to STOP monitoring for distractions. */
    suspend fun sendSessionEnded(context: Context, payload: ByteArray) {
        broadcastMessage(context, PATH_SESSION_ENDED, payload)
    }

    /** Broadcasts [payload] on [path] to every connected node. */
    private suspend fun broadcastMessage(context: Context, path: String, payload: ByteArray) {
        sendLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val nodes = Wearable.getNodeClient(context)
                        .connectedNodes
                        .await()

                    if (nodes.isEmpty()) {
                        Log.w(TAG, "No connected nodes — '$path' signal dropped.")
                        return@withContext
                    }

                    val messageClient = Wearable.getMessageClient(context)

                    for (node in nodes) {
                        try {
                            messageClient
                                .sendMessage(node.id, path, payload)
                                .await()
                            Log.d(TAG, "Sent '$path' → ${node.displayName} (${node.id})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send '$path' to ${node.displayName}: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Node discovery failed for '$path': ${e.message}")
                }
            }
        }
    }
}
