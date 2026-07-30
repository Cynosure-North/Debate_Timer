package page.cynosure.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class TimerSettings(
    val bellSettings: Int,
    val countUp: Boolean,
    val maxTime: Int,
)

class SettingsRepository(private val context: Context) {
    private object PreferencesKeys {
        val BELL_SETTINGS = intPreferencesKey("bell_settings")
        val COUNT_UP = booleanPreferencesKey("count_up")
        val MAX_TIME = intPreferencesKey("max_time")
    }

    val settingsFlow: Flow<TimerSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            TimerSettings(
                bellSettings = preferences[PreferencesKeys.BELL_SETTINGS] ?: 2,
                countUp = preferences[PreferencesKeys.COUNT_UP] ?: false,
                maxTime = preferences[PreferencesKeys.MAX_TIME] ?: 8
            )
        }

    suspend fun updateBellSettings(bellSettings: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BELL_SETTINGS] = bellSettings
        }
    }

    suspend fun updateCountUp(countUp: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_UP] = countUp
        }
    }

    suspend fun updateMaxTime(maxTime: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_TIME] = maxTime
        }
    }
}
