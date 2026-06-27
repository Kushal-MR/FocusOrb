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
    SessionDuration(1, StarSize.SMALL),
    SessionDuration(0, StarSize.MEDIUM),
    SessionDuration(45, StarSize.LARGE),
    SessionDuration(60, StarSize.EPIC)
)

data class FocusUiState(
    val selectedDurationIndex: Int = 2, // Default to 45 mins
    val timeRemainingMs: Long = AVAILABLE_DURATIONS[2].ms,
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
    private val prefs = application.getSharedPreferences("galaxy_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _timerEvent = Channel<TimerEvent>()
    val timerEvent = _timerEvent.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        loadStars()
    }

    private fun loadStars() {
        // Force a beautifully mixed 100-star structure for testing
        val mockStars = (List(20) { StarSize.SMALL } + 
                         List(30) { StarSize.MEDIUM } + 
                         List(40) { StarSize.LARGE } + 
                         List(10) { StarSize.EPIC }).shuffled()
        val stars = mockStars
        _uiState.update { it.copy(earnedStars = stars) }
    }

    private fun saveStars(stars: List<StarSize>) {
        val starsString = stars.joinToString(",") { it.name }
        prefs.edit().putString("earned_stars", starsString).apply()
    }

    fun selectNextDuration() {
        if (_uiState.value.sessionState != SessionState.IDLE) return
        val nextIndex = (_uiState.value.selectedDurationIndex + 1) % AVAILABLE_DURATIONS.size
        _uiState.update { 
            it.copy(
                selectedDurationIndex = nextIndex,
                timeRemainingMs = AVAILABLE_DURATIONS[nextIndex].ms
            )
        }
    }

    fun selectPreviousDuration() {
        if (_uiState.value.sessionState != SessionState.IDLE) return
        val prevIndex = if (_uiState.value.selectedDurationIndex - 1 < 0) {
            AVAILABLE_DURATIONS.size - 1
        } else {
            _uiState.value.selectedDurationIndex - 1
        }
        _uiState.update { 
            it.copy(
                selectedDurationIndex = prevIndex,
                timeRemainingMs = AVAILABLE_DURATIONS[prevIndex].ms
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

        // Signal the phone to start monitoring
        viewModelScope.launch {
            PhoneBridgeManager.sendSessionStarted(getApplication())
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
                PhoneBridgeManager.sendSessionEnded(getApplication())
            }
        }
    }

    fun pauseSession() {
        timerJob?.cancel()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
    }

    fun takeDamage() {
        if (_uiState.value.sessionState == SessionState.COMPLETED || _uiState.value.sessionState == SessionState.SHATTERED) return
        
        val newHealth = _uiState.value.orbHealth - 1
        if (newHealth <= 0) {
            timerJob?.cancel()
            _uiState.update { it.copy(orbHealth = 0, sessionState = SessionState.SHATTERED) }
            viewModelScope.launch {
                _timerEvent.send(TimerEvent.SHATTER)
                // Signal the phone to stop monitoring — session shattered
                PhoneBridgeManager.sendSessionEnded(getApplication())
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
            viewModelScope.launch {
                PhoneBridgeManager.sendSessionEnded(getApplication())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
