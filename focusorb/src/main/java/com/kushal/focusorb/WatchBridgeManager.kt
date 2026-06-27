package com.kushal.focusorb

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * WatchBridgeManager — Fire-and-forget messaging bridge to paired Wear OS devices.
 *
 * Isolated singleton that owns all Wearable Data Layer communication.
 * Every public entry point is a `suspend` function executed on [Dispatchers.IO]
 * so the caller's thread (the Accessibility pipeline) is never blocked.
 *
 * Protocol:
 *  - Path:    "/distraction_triggered"
 *  - Payload: Empty ByteArray (the watch only needs to know *that* it happened,
 *             not *what* app was opened — keeps latency in the low milliseconds).
 */
object WatchBridgeManager {

    private const val TAG = "WatchBridge"

    /** Strict message path constant — must match the receiver path on the watch module. */
    const val PATH_DISTRACTION_TRIGGERED = "/distraction_triggered"

    /**
     * Discovers all connected Wear OS nodes and broadcasts a distraction signal
     * to every one of them.
     *
     * This function is **coroutine-safe** and performs all I/O on [Dispatchers.IO].
     * It will never throw to the caller — all failures are caught and logged.
     *
     * @param context  Any valid Android [Context] (Application, Service, Activity).
     */
    suspend fun sendDistractionSignal(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // ── Phase 1: Node Discovery ──────────────────────────────
                // Query the Wearable Node API for all actively connected
                // Bluetooth peers (watches, car units, etc.).
                val nodes = Wearable.getNodeClient(context)
                    .connectedNodes
                    .await()

                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected Wear OS nodes found — signal dropped.")
                    return@withContext
                }

                Log.d(TAG, "Discovered ${nodes.size} node(s). Firing distraction signal...")

                // ── Phase 2: Broadcast ───────────────────────────────────
                // Fire an empty-payload message to every connected node.
                // Empty ByteArray keeps the BT packet minimal for
                // sub-millisecond transmission latency.
                val messageClient = Wearable.getMessageClient(context)

                for (node in nodes) {
                    try {
                        messageClient
                            .sendMessage(
                                node.id,
                                PATH_DISTRACTION_TRIGGERED,
                                ByteArray(0)
                            )
                            .await()

                        Log.d(TAG, "Signal delivered → node '${node.displayName}' (${node.id})")
                    } catch (e: Exception) {
                        // Per-node catch so one failing node doesn't block others.
                        Log.e(TAG, "Failed to signal node '${node.displayName}': ${e.message}")
                    }
                }

            } catch (e: Exception) {
                // Outer catch covers node discovery failures (BT off, Play
                // Services unavailable, etc.). Silently degrade — the phone
                // app must never crash the Accessibility pipeline.
                Log.e(TAG, "Node discovery failed: ${e.message}")
            }
        }
    }
}
