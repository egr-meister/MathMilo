package com.mathmilo.data.repository

import android.content.Context
import com.mathmilo.data.local.MathMiloDatabase
import com.mathmilo.data.local.RoomPracticeStore
import com.mathmilo.domain.daily.SystemTimeProvider
import com.mathmilo.domain.daily.TimeProvider
import com.mathmilo.domain.progress.PracticeService
import kotlin.random.Random

/** Manual dependency injection. Created once in [com.mathmilo.MathMiloApp]. */
class AppContainer(
    context: Context,
    val time: TimeProvider = SystemTimeProvider(),
    random: Random = Random.Default,
) {
    private val database: MathMiloDatabase = MathMiloDatabase.create(context)
    val preferences = PreferencesRepository(context)
    val calculations = CalculationRepository(database, time)
    val practice = PracticeRepository(database, PracticeService(RoomPracticeStore(database), time, random), time)

    /** Clear all local data, restore Starter level and generate a fresh set for today. */
    suspend fun clearAllData() {
        calculations.clear()
        practice.clearPracticeData()
        preferences.clearAll()
        practice.getOrCreateSet(time.today(), preferences.current().level)
    }

    /** Clear practice history and mistakes, then provide a fresh set for today. */
    suspend fun clearPracticeData() {
        practice.clearPracticeData()
        practice.getOrCreateSet(time.today(), preferences.current().level)
    }
}
