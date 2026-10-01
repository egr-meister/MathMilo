package com.mathmilo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mathmilo.domain.generation.PracticeLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mathmilo_prefs")

data class UserPreferences(
    val level: PracticeLevel = PracticeLevel.DEFAULT,
    val reducedMotion: Boolean = false,
)

/** DataStore-backed preferences, UI state and the calculator draft. */
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val LEVEL = stringPreferencesKey("practice_level")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val CALC_DRAFT = stringPreferencesKey("calculator_draft")
    }

    private val safeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val preferences: Flow<UserPreferences> = safeData.map { p ->
        UserPreferences(
            level = PracticeLevel.fromCode(p[Keys.LEVEL]),
            reducedMotion = p[Keys.REDUCED_MOTION] ?: false,
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setLevel(level: PracticeLevel) {
        store.edit { it[Keys.LEVEL] = level.code }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        store.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun calculatorDraft(): String? = safeData.first()[Keys.CALC_DRAFT]

    suspend fun saveCalculatorDraft(encoded: String) {
        store.edit { it[Keys.CALC_DRAFT] = encoded }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
