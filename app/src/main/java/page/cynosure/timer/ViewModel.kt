package page.cynosure.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Represents the entire state of the timer UI
data class TimerUiState(
    val currentTime: Long = 0L,
    val maxTime: Int = 8,
    val bellSettings: Int = 2, // 0: off, 1: at the end, 2: POI times
    val countUp: Boolean = false,
    val isPaused: Boolean = true
)

class ViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun togglePause() {
        if (_uiState.value.isPaused) {
            startTimer()
        } else {
            pauseTimer()
        }
    }

    private fun startTimer() {
        _uiState.update { it.copy(isPaused = false) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { it.copy(currentTime = it.currentTime + 1) }
            }
        }
    }

    private fun pauseTimer() {
        _uiState.update { it.copy(isPaused = true) }
        timerJob?.cancel()
    }

    fun stopTimer() {
        _uiState.update { it.copy(isPaused = true, currentTime = 0L) }
        timerJob?.cancel()
    }

    fun setMaxTime(minutes: Int) {
        _uiState.update { it.copy(maxTime = minutes) }
        stopTimer()
    }

    fun cycleBellSettings() {
        _uiState.update { it.copy(bellSettings = (it.bellSettings + 1) % 3) }
    }

    fun toggleCountDirection() {
        _uiState.update { it.copy(countUp = !it.countUp) }
    }


}