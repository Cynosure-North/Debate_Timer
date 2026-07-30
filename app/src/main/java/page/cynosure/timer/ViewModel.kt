package page.cynosure.timer

import android.app.Application
import android.content.Context
import android.media.SoundPool
import androidx.lifecycle.AndroidViewModel
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
    val isPaused: Boolean = true,
    val maximised: Int = 0,       // 0: portrait, 1: landscape, 2: reverse landscape (anticlockwise)
    val rotationLocked: Boolean = false,
)

class ViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    private var soundPool: SoundPool? = null
    private var oneBell = 0
    private var twoBells = 0
    private var threeBells = 0

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(
                    bellSettings = settings.bellSettings,
                    countUp = settings.countUp,
                    maxTime = settings.maxTime
                ) }
            }
        }
    }

    fun initBells(context: Context? = null) {
        if (context != null) {
            soundPool = SoundPool.Builder().setMaxStreams(1).build()
            oneBell = soundPool?.load(context, R.raw.one_bell, 1) ?: -1
            twoBells = soundPool?.load(context, R.raw.two_bells, 1) ?: -1
            threeBells = soundPool?.load(context, R.raw.three_bells, 1) ?: -1

        }
        else {
            soundPool = null
        }
    }

    fun togglePause() {
        if (_uiState.value.isPaused) {
            startTimer()
        } else {
            pauseTimer()
        }
    }

    fun setMaximise(new_val: Int) {
        _uiState.update { it.copy(maximised = new_val) }
    }

    fun toggleRotationLock() {
        if (_uiState.value.rotationLocked) {
            _uiState.update { it.copy(rotationLocked = false) }
        } else {
            _uiState.update { it.copy(rotationLocked = true) }
        }
    }

    fun unlockRotation() {
        _uiState.update { it.copy(rotationLocked = false) }
    }

    private fun startTimer() {
        _uiState.update { it.copy(isPaused = false) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { it.copy(currentTime = it.currentTime + 1) }
                checkBells()
            }
        }
    }

    private fun checkBells() {
        val time = _uiState.value.currentTime
        val max = _uiState.value.maxTime
        val bellSettings = _uiState.value.bellSettings

        if (time > 0 && bellSettings != 0) {
            // Single bells
            if (((time == 60L) && (bellSettings == 2)) || ((time == (max - 1) * 60L))) {
                soundPool?.play(oneBell, 3f, 3f, 0, 0, 1f)
            }
            // Double bell
            if (time == max * 60L) {
                soundPool?.play(twoBells, 3f, 3f, 0, 0, 1f)
            }
            // Triple bells
            if ((time > max * 60L) && (time % 15 == 0L)) {
                soundPool?.play(threeBells, 3f, 3f, 0, 0, 1f)
            }
        }
    }

    fun playManualBell() {
        soundPool?.play(oneBell, 3f, 3f, 0, 0, 1f)
    }

    private fun pauseTimer() {
        _uiState.update { it.copy(isPaused = true) }
        timerJob?.cancel()
    }

    fun resetTimer() {
        _uiState.update { it.copy(isPaused = true, currentTime = 0L) }
        timerJob?.cancel()
    }

    fun setMaxTime(minutes: Int) {
        _uiState.update { it.copy(maxTime = minutes) }
        viewModelScope.launch {
            repository.updateMaxTime(minutes)
        }
        resetTimer()
    }

    fun cycleBellSettings() {
        val nextSettings = (_uiState.value.bellSettings + 1) % 3
        _uiState.update { it.copy(bellSettings = nextSettings) }
        viewModelScope.launch {
            repository.updateBellSettings(nextSettings)
        }
    }

    fun toggleCountDirection() {
        val nextCountUp = !_uiState.value.countUp
        _uiState.update { it.copy(countUp = nextCountUp) }
        viewModelScope.launch {
            repository.updateCountUp(nextCountUp)
        }
    }

    override fun onCleared() {
        soundPool?.release()
    }
}