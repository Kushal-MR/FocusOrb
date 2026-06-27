package com.kushal.focusorb.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.kushal.focusorb.R

/**
 * OngoingActivityManager — The VIP Pass for Ambient Mode on Wear OS 3+
 *
 * In order for an app to stay in its own Ambient Mode (without dropping back
 * to the watch face) when the user lowers their wrist, it MUST declare itself
 * as an active, ongoing task.
 *
 * We do this by posting a persistent Notification and attaching an
 * [OngoingActivity] to it. This signals the OS to respect our ambient state.
 */
object OngoingActivityManager {
    private const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "focus_session_channel"

    fun startOngoingActivity(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Create the Notification Channel (Required on Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Focus Orb sessions"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Create the Intent that brings our app back to the foreground
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build the Notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Focus Session Active")
            .setContentText("Focus Orb is protecting your time.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Placeholder icon
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        // 4. Attach the Ongoing Activity (The VIP Pass)
        val status = Status.Builder()
            .addTemplate("Focusing...")
            .build()

        OngoingActivity.Builder(context, NOTIFICATION_ID, notificationBuilder)
            .setTouchIntent(pendingIntent)
            .setStatus(status)
            .build()
            .apply(context)

        // 5. Post the Notification
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    fun stopOngoingActivity(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
