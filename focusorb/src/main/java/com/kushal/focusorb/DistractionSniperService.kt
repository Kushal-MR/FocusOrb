package com.kushal.focusorb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.SharedPreferences

/**
 * DistractionSniperService — The silent background referee.
 *
 * Monitors foreground window transitions via AccessibilityService and flags
 * when a blacklisted application is opened during an active focus session.
 *
 * Battery-critical design:
 *  - Only processes TYPE_WINDOW_STATE_CHANGED events.
 *  - All other event types (scroll, click, text change) are discarded
 *    at the top of onAccessibilityEvent before any allocation occurs.
 *  - Package lookups use a locally-cached HashSet for O(1) comparison,
 *    refreshed from [BlocklistStore] on each valid window-change event.
 */
class DistractionSniperService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "FocusOrbSniper"
    }

    // ── Session state ────────────────────────────────────────────────────
    // Dynamically read from SessionStateStore on each event.
    // The watch sends "/session_started" and "/session_ended" signals
    // which WatchListenerService persists to SharedPreferences.
    // No field needed — read inline in the event pipeline.

    // ── Dynamic blocklist ────────────────────────────────────────────────
    // Loaded from SharedPreferences via BlocklistStore.
    // Refreshed on every valid TYPE_WINDOW_STATE_CHANGED event so that
    // dashboard toggle changes are picked up without restarting the service.
    @Volatile
    private var forbiddenPackages: Set<String> = emptySet()

    // Tracks the last detected package to avoid redundant log spam
    // when the system re-fires the same window-state event.
    private var lastDetectedPackage: String? = null

    // ── Coroutine scope ──────────────────────────────────────────────────
    // SupervisorJob ensures a single failed transmission doesn't cancel
    // the entire scope. IO dispatcher keeps BT work off the main thread.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // The continuous poison tick loop
    private var poisonJob: Job? = null

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Programmatic reinforcement of the XML config — belt-and-suspenders
        // approach so the filter is guaranteed even if the XML is mis-parsed.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv() and flags) or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L   // 100 ms debounce between events
        }

        // Initial blocklist load from persistence
        refreshBlocklist()
        
        SessionStateStore.registerListener(this, this)

        Log.d(TAG, "DistractionSniperService connected — ${forbiddenPackages.size} app(s) blocked.")
    }

    override fun onInterrupt() {
        // Called when the system wants to interrupt feedback from this service.
        // Nothing to clean up — we produce no feedback output.
        Log.d(TAG, "Service interrupted by system.")
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionStateStore.unregisterListener(this, this)
        stopPoisonTick()
        serviceScope.cancel() // Prevent coroutine leaks on service teardown
        Log.d(TAG, "Service destroyed — sniper offline.")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "is_session_active") {
            val isActive = SessionStateStore.isSessionActive(this)
            if (isActive) {
                // The exact moment a session starts, we check what app is CURRENTLY open.
                // This catches the case where the user starts the timer while Instagram is already open.
                refreshBlocklist()
                val currentPkg = rootInActiveWindow?.packageName?.toString()?.lowercase()
                if (currentPkg != null && currentPkg in forbiddenPackages) {
                    Log.d(TAG, "DISTRACTION CAPTURED ON START: $currentPkg is already open!")
                    startPoisonTick(currentPkg)
                }
            } else {
                stopPoisonTick()
                lastDetectedPackage = null
            }
        }
    }

    // ── Core event pipeline ──────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ┌─ GUARD 1: Null safety ─────────────────────────────────────────
        if (event == null) return

        // ┌─ GUARD 2: Battery gate — reject everything except window swap ─
        //   This is the single most important line for battery life.
        //   Scrolls, clicks, text changes, and notifications are killed here
        //   before any object allocation or string work occurs.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // ┌─ GUARD 3: Session gate — skip processing when no focus block ──
        if (!SessionStateStore.isSessionActive(this)) {
            stopPoisonTick()
            return
        }

        // ── Refresh blocklist from persistence ───────────────────────────
        refreshBlocklist()

        // ── Extract the foreground package name ──────────────────────────
        val packageName: String = event.packageName?.toString()?.lowercase() ?: return

        // ┌─ GUARD 4: Dedup — same package as last detection, skip ────────
        if (packageName == lastDetectedPackage) return

        // Update tracker regardless of whether this package is forbidden,
        // so we don't re-evaluate the same foreground app on repeated events.
        lastDetectedPackage = packageName

        // ── Blocklist check (O(1) HashSet lookup) ────────────────────────
        if (packageName in forbiddenPackages) {
            Log.d(TAG, "DISTRACTION CAPTURED: $packageName")
            startPoisonTick(packageName)
        } else {
            stopPoisonTick()
        }
    }

    // ── Continuous Poison Tick ────────────────────────────────────────────

    private fun startPoisonTick(packageName: String) {
        if (poisonJob?.isActive == true) return
        
        poisonJob = serviceScope.launch {
            // Inflict immediate initial damage
            WatchBridgeManager.sendDistractionSignal(this@DistractionSniperService)
            
            // Loop infinitely while the user remains in the forbidden app
            while (isActive) {
                delay(5000) // Wait 5 seconds
                
                if (!SessionStateStore.isSessionActive(this@DistractionSniperService)) break
                
                // Double check if the foreground app is STILL a blocked app
                val currentPkg = rootInActiveWindow?.packageName?.toString()?.lowercase()
                if (currentPkg == null || currentPkg !in forbiddenPackages) {
                    break
                }
                
                Log.d(TAG, "POISON TICK: 5 seconds elapsed in $currentPkg, inflicting damage!")
                WatchBridgeManager.sendDistractionSignal(this@DistractionSniperService)
            }
        }
    }

    private fun stopPoisonTick() {
        poisonJob?.cancel()
        poisonJob = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Reads the current blocklist from [BlocklistStore] and caches it
     * in [forbiddenPackages] for O(1) lookups on the hot path.
     */
    private fun refreshBlocklist() {
        forbiddenPackages = BlocklistStore.getBlockedPackages(this)
    }
}
