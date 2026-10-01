package com.mathmilo.data.repository

import androidx.room.withTransaction
import com.mathmilo.data.local.MathMiloDatabase
import com.mathmilo.data.local.toDomain
import com.mathmilo.data.local.toSummary
import com.mathmilo.domain.daily.TimeProvider
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.ResultPracticeGenerator
import com.mathmilo.domain.progress.AnswerOutcome
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.domain.progress.DailySet
import com.mathmilo.domain.progress.DailySummary
import com.mathmilo.domain.progress.ExtraPracticeRecord
import com.mathmilo.domain.progress.MistakeRecord
import com.mathmilo.domain.progress.MistakeRetryRecord
import com.mathmilo.domain.progress.PracticeService
import com.mathmilo.domain.progress.ProgressTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** UI-facing access to practice data. Writes go through [PracticeService]. */
class PracticeRepository(
    private val db: MathMiloDatabase,
    private val service: PracticeService,
    private val time: TimeProvider,
) {
    private val dao = db.practiceDao()

    fun today(): LocalDate = time.today()

    suspend fun getOrCreateSet(date: LocalDate, level: PracticeLevel): DailySet = service.getOrCreateSet(date, level)

    suspend fun set(id: Long): DailySet? = dao.setById(id)?.toDomain()

    fun observeSet(id: Long): Flow<DailySet?> = dao.observeSet(id).map { it?.toDomain() }

    fun observeSetForDate(date: LocalDate): Flow<DailySet?> = dao.observeSetByDate(date.toString()).map { it?.toDomain() }

    fun observeQuestions(setId: Long): Flow<List<DailyQuestionRecord>> =
        dao.observeQuestions(setId).map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    fun observeRecentSummaries(limit: Int = PracticeService.KEEP_DAILY_SETS): Flow<List<DailySummary>> =
        dao.observeRecentSets(limit).map { list -> list.map { it.toSummary() } }

    fun observeTotals(): Flow<ProgressTotals> = dao.observeTotals().map { it.toDomain() }

    fun observeActiveMistakes(): Flow<List<MistakeRecord>> =
        dao.observeActiveMistakes().map { list -> list.map { it.toDomain() } }

    fun observeActiveMistakeCount(): Flow<Int> = dao.observeActiveMistakeCount()

    fun observeExtra(id: Long): Flow<ExtraPracticeRecord?> = dao.observeExtra(id).map { it?.toDomain() }

    suspend fun mistake(id: Long): MistakeRecord? = dao.mistake(id)?.toDomain()

    suspend fun answerDaily(questionId: Long, selected: Int): AnswerOutcome = service.answerDaily(questionId, selected)

    suspend fun createResultPractice(result: Int, source: ResultPracticeGenerator.Source, calcId: Long?): Long =
        service.createResultPractice(result, source, calcId)

    suspend fun answerExtra(id: Long, selected: Int): AnswerOutcome = service.answerExtra(id, selected)

    suspend fun getOrCreateRetry(attemptId: String, mistakeId: Long): MistakeRetryRecord =
        service.getOrCreateRetry(attemptId, mistakeId)

    suspend fun retry(attemptId: String): MistakeRetryRecord? = dao.retry(attemptId)?.toDomain()

    suspend fun answerRetry(attemptId: String, selected: Int): AnswerOutcome = service.answerRetry(attemptId, selected)

    /** Clears daily sets, extra practice, mistakes, retries and totals. */
    suspend fun clearPracticeData() = db.withTransaction {
        dao.clearRetries()
        dao.clearMistakes()
        dao.clearExtra()
        dao.clearQuestions()
        dao.clearSets()
        dao.clearTotals()
    }
}
