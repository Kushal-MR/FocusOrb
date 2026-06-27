package com.kushal.focusorb.presentation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

/**
 * DistractionMessageReceiver — Bluetooth signal listener for the watch side.
 *
 * Registers itself on the Wearable [MessageClient] and listens for the
 * "/distraction_triggered" path sent by the phone's [WatchBridgeManager].
 *
 * When the signal is caught:
 *  1. Fires an aggressive double-pulse haptic penalty so the user
 *     physically feels the consequence of their distraction.
 *  2. Invokes the provided [onDistractionCaught] callback (typically
 *     wired to [FocusViewModel.takeDamage]) to deduct orb health.
 *
 * Lifecycle:
 *  - Call [register] in onResume / DisposableEffect setup.
 *  - Call [unregister] in onPause / DisposableEffect disposal.
 */
class DistractionMessageReceiver(
    private val context: Context,
    private val onDistractionCaught: () -> Unit
) : MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "DistractionReceiver"
        const val PATH_DISTRACTION_TRIGGERED = "/distraction_triggered"
    }

    // ── Listener registration ────────────────────────────────────────────

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Log.d(TAG, "Message listener registered — watching for distraction signals.")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Log.d(TAG, "Message listener unregistered.")
    }

    // ── Message handling ─────────────────────────────────────────────────

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // ── Path filter — ignore anything that isn't our distraction signal ──
        if (messageEvent.path != PATH_DISTRACTION_TRIGGERED) return

        Log.d(TAG, "DISTRACTION SIGNAL RECEIVED from node: ${messageEvent.sourceNodeId}")

        // ── 1. Force screen wake up from ambient ──────────────────────────
        wakeUpScreen()

        // ── 2. Haptic penalty — aggressive double pulse ──────────────────
        fireHapticPenalty()

        // ── 3. Orb damage callback ───────────────────────────────────────
        onDistractionCaught()
    }

    private fun wakeUpScreen() {
        val activity = context as? android.app.Activity ?: return
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    // ── Haptic engine ────────────────────────────────────────────────────

    /**
     * Fires a distinct, aggressive double-pulse vibration pattern:
     *   0ms delay → 150ms heavy buzz → 100ms pause → 150ms heavy buzz
     *
     * Uses [VibratorManager] on API 31+ and falls back to the legacy
     * [Vibrator] service on older devices.
     */
    private fun fireHapticPenalty() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Double heavy pulse: delay, buzz, pause, buzz
            val timings   = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 255,   0, 255)
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 100, 150), -1)
        }
    }
}
