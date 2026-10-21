package com.kushal.focusorb.presentation

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionState {
    IDLE, RUNNING, PAUSED, COMPLETED, SHATTERED
}

enum class TimerEvent {
    PULSE, COMPLETED, SHATTER
}

enum class StarSize(val sizeDp: Float) {
    SMALL(10f),
    MEDIUM(15f),
    LARGE(20f),
    EPIC(28f)
}

data class SessionDuration(val minutes: Int, val starSize: StarSize) {
    val ms: Long get() = minutes * 60 * 1000L
}

val AVAILABLE_DURATIONS = listOf(
    SessionDuration(20, StarSize.SMALL),
    SessionDuration(30, StarSize.MEDIUM),
    SessionDuration(40, StarSize.LARGE),
    SessionDuration(60, StarSize.EPIC)
)

data class FocusUiState(
    val selectedDurationIndex: Int = 1, // Default to 30 mins
    val timeRemainingMs: Long = AVAILABLE_DURATIONS[1].ms,
    val sessionState: SessionState = SessionState.IDLE,
    val orbHealth: Int = 3,
    val earnedStars: List<StarSize> = emptyList()
) {
    val currentDuration: SessionDuration
        get() = AVAILABLE_DURATIONS[selectedDurationIndex]

    val progress: Float
        get() = if (currentDuration.ms > 0) 1f - (timeRemainingMs.toFloat() / currentDuration.ms.toFloat()) else 0f
}

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "FocusSession"
    }

    private val prefs = application.getSharedPreferences("galaxy_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _timerEvent = Channel<TimerEvent>()
    val timerEvent = _timerEvent.receiveAsFlow()

    private var timerJob: Job? = null

    /**
     * Identifies the current session to the phone: its original start time.
     * Kept across pause/resume so the phone can treat the pieces as one
     * session; cleared once the session reaches an outcome.
     */
    private var sessionId: Long? = null

    /** Grace period the phone allows past the planned end before giving up. */
    private val expiryGraceMs = 60_000L

    init {
        loadStars()
    }

    private fun loadStars() {
        val starsString = prefs.getString("earned_stars_v3", null)
        val stars = if (starsString.isNullOrEmpty()) {
            emptyList()
        } else {
            starsString.split(",").mapNotNull { name ->
                runCatching { StarSize.valueOf(name) }.getOrNull()
            }
        }
        _uiState.update { it.copy(earnedStars = stars) }
    }

    private fun saveStars(stars: List<StarSize>) {
        val starsString = stars.joinToString(",") { it.name }
        prefs.edit().putString("earned_stars_v3", starsString).apply()
    }

    fun selectDuration(index: Int) {
        if (_uiState.value.sessionState != SessionState.IDLE) return
        if (index < 0 || index >= AVAILABLE_DURATIONS.size) return
        _uiState.update {
            it.copy(
                selectedDurationIndex = index,
                timeRemainingMs = AVAILABLE_DURATIONS[index].ms
            )
        }
    }

    fun toggleSession() {
        when (_uiState.value.sessionState) {
            SessionState.RUNNING -> pauseSession()
            SessionState.IDLE, SessionState.PAUSED -> startSession()
            SessionState.COMPLETED, SessionState.SHATTERED -> resetSession()
        }
    }

    fun startSession() {
        if (_uiState.value.sessionState == SessionState.RUNNING || 
            _uiState.value.sessionState == SessionState.COMPLETED ||
            _uiState.value.sessionState == SessionState.SHATTERED) return
        
        _uiState.update { it.copy(sessionState = SessionState.RUNNING) }

        // Signal the phone to start monitoring. The expiry lets the phone stand
        // down on its own if this app dies mid-session and never says "ended".
        val now = System.currentTimeMillis()
        val id = sessionId ?: now.also { sessionId = it }
        val payload = SessionMessage.started(
            sessionId = id,
            expiresAt = now + _uiState.value.timeRemainingMs + expiryGraceMs,
            plannedMinutes = _uiState.value.currentDuration.minutes
        )
        Log.d(TAG, "Session $id started (${_uiState.value.timeRemainingMs / 1000}s remaining)")
        viewModelScope.launch {
            PhoneBridgeManager.sendSessionStarted(getApplication(), payload)
        }
        
        timerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + _uiState.value.timeRemainingMs
            var nextPulseThreshold = 10 * 60 * 1000L
            
            while (_uiState.value.timeRemainingMs > 0) {
                kotlinx.coroutines.delay(1000)
                val remaining = kotlin.math.max(0L, endTime - System.currentTimeMillis())
                _uiState.update { 
                    it.copy(timeRemainingMs = remaining)
                }
                
                val elapsed = _uiState.value.currentDuration.ms - remaining
                if (elapsed >= nextPulseThreshold && remaining > 0) {
                    _timerEvent.send(TimerEvent.PULSE)
                    nextPulseThreshold += 10 * 60 * 1000L
                }
            }
            if (_uiState.value.timeRemainingMs <= 0) {
                val earnedStar = _uiState.value.currentDuration.starSize
                val newStars = _uiState.value.earnedStars + earnedStar
                saveStars(newStars)
                
                _uiState.update { it.copy(sessionState = SessionState.COMPLETED, timeRemainingMs = 0, earnedStars = newStars) }
                _timerEvent.send(TimerEvent.COMPLETED)

                // Signal the phone to stop monitoring — session completed successfully
                endPhoneSession(SessionMessage.Outcome.COMPLETED)
            }
        }
    }

    fun pauseSession() {
        timerJob?.cancel()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED) }

        // Stand the phone down too. Previously a paused orb kept taking hits,
        // because the phone was never told the clock had stopped.
        endPhoneSession(SessionMessage.Outcome.PAUSED)
    }

    fun takeDamage() {
        if (_uiState.value.sessionState == SessionState.COMPLETED || _uiState.value.sessionState == SessionState.SHATTERED) return
        
        val newHealth = _uiState.value.orbHealth - 1
        if (newHealth <= 0) {
            timerJob?.cancel()
            _uiState.update { it.copy(orbHealth = 0, sessionState = SessionState.SHATTERED) }
            // Signal the phone to stop monitoring — session shattered
            endPhoneSession(SessionMessage.Outcome.SHATTERED)
            viewModelScope.launch {
                _timerEvent.send(TimerEvent.SHATTER)
            }
        } else {
            _uiState.update { it.copy(orbHealth = newHealth) }
        }
    }

    fun resetSession() {
        val wasActive = _uiState.value.sessionState == SessionState.RUNNING ||
                        _uiState.value.sessionState == SessionState.PAUSED
        timerJob?.cancel()
        _uiState.update { 
            it.copy(
                sessionState = SessionState.IDLE, 
                timeRemainingMs = it.currentDuration.ms,
                orbHealth = 3
            )
        }

        // Signal the phone to stop monitoring if session was active
        if (wasActive) {
            endPhoneSession(SessionMessage.Outcome.CANCELLED)
        }
    }

    /**
     * Tells the phone the session has stopped, and why. Every outcome except
     * a pause closes the session out, so the next start gets a fresh ID.
     */
    private fun endPhoneSession(outcome: SessionMessage.Outcome) {
        val id = sessionId ?: return
        if (outcome != SessionMessage.Outcome.PAUSED) sessionId = null
        val payload = SessionMessage.ended(id, outcome)
        Log.d(TAG, "Session $id ended: $outcome")
        viewModelScope.launch {
            PhoneBridgeManager.sendSessionEnded(getApplication(), payload)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
