package com.kushal.focusorb.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * PhoneBridgeManager — Watch-to-phone session signaling.
 *
 * Sends "/session_started" and "/session_ended" messages to all connected
 * phone nodes so the phone's [DistractionSniperService] knows when to
 * activate and deactivate monitoring.
 *
 * All I/O runs on [Dispatchers.IO] — safe to call from [viewModelScope].
 */
object PhoneBridgeManager {

    private const val TAG = "PhoneBridge"

    const val PATH_SESSION_STARTED = "/session_started"
    const val PATH_SESSION_ENDED = "/session_ended"

    /**
     * Tells the phone to START monitoring for distractions.
     */
    suspend fun sendSessionStarted(context: Context) {
        broadcastMessage(context, PATH_SESSION_STARTED)
    }

    /**
     * Tells the phone to STOP monitoring for distractions.
     */
    suspend fun sendSessionEnded(context: Context) {
        broadcastMessage(context, PATH_SESSION_ENDED)
    }

    /**
     * Broadcasts an empty-payload message on [path] to every connected node.
     */
    private suspend fun broadcastMessage(context: Context, path: String) {
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
                            .sendMessage(node.id, path, ByteArray(0))
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
