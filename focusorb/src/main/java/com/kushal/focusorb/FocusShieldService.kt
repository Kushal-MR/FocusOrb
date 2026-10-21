package com.kushal.focusorb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FocusShieldService — the phone's referee for the length of a focus session.
 *
 * A foreground service that polls [ForegroundAppTracker] once a second and,
 * when a blocked app is in front, signals the watch through
 * [WatchBridgeManager]. It only exists while a session is running: the watch's
 * "/session_started" message starts it, "/session_ended" stops it, and it stops
 * itself if [SessionStateStore] reports the session is over.
 *
 * Penalty rules, carried over unchanged from the accessibility version:
 *  - Opening a blocked app costs a hit immediately.
 *  - Staying in it costs another hit every [POISON_TICK_MS] (the "poison tick").
 *  - Leaving resets the clock, so coming back costs an immediate hit again.
 */
class FocusShieldService : Service() {

    companion object {
        private const val TAG = "FocusShield"
        private const val CHANNEL_ID = "focus_shield"
        private const val NOTIFICATION_ID = 2001

        private const val POLL_INTERVAL_MS = 1_000L
        private const val POISON_TICK_MS = 5_000L

        /** Starts the shield. Safe to call when it is already running. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FocusShieldService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusShieldService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    /** When the last hit was sent, or null while no blocked app is in front. */
    private var lastHitAt: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must run on every start, before any early return:
        // a service launched with startForegroundService that fails to call it
        // within a few seconds crashes the app.
        enterForeground()

        if (!SessionStateStore.isSessionActive(this)) {
            Log.d(TAG, "No active session — shield standing down.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (pollJob?.isActive != true) {
            pollJob = scope.launch { pollLoop() }
            Log.d(TAG, "Shield up — polling the foreground app.")
        }
        // If the system kills us mid-session, come back; the session check
        // above stands the restarted service down if the session has ended.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        Log.d(TAG, "Shield down.")
        super.onDestroy()
    }

    // ── Detection loop ───────────────────────────────────────────────────

    private suspend fun kotlinx.coroutines.CoroutineScope.pollLoop() {
        val tracker = ForegroundAppTracker(this@FocusShieldService)
        while (isActive) {
            if (!SessionStateStore.isSessionActive(this@FocusShieldService)) {
                stopSelf()
                return
            }

            val foreground = tracker.currentForegroundPackage()
            // Re-read every poll so dashboard toggles apply mid-session.
            val blocked = BlocklistStore.getBlockedPackages(this@FocusShieldService)

            if (foreground != null && foreground in blocked) {
                val now = System.currentTimeMillis()
                val last = lastHitAt
                if (last == null || now - last >= POISON_TICK_MS) {
                    lastHitAt = now
                    Log.d(TAG, if (last == null) "DISTRACTION: $foreground" else "POISON TICK: still in $foreground")
                    launch { WatchBridgeManager.sendDistractionSignal(this@FocusShieldService) }
                }
            } else {
                lastHitAt = null
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    // ── Foreground notification ──────────────────────────────────────────

    private fun enterForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Focus shield", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a focus session is running on your watch"
                    setShowBadge(false)
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Focus session active")
            .setContentText("Distracting apps will cost your orb health.")
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
