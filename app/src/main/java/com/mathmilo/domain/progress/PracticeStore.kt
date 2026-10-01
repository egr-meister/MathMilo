package com.mathmilo.domain.progress

import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import java.time.LocalDate

/**
 * Persistence boundary for practice data. Implemented by Room in production and by an
 * in-memory fake in unit tests. Every "mark…" method is conditional (only updates rows that
 * are still unanswered/unresolved) and reports whether it changed anything, which is what
 * makes repeated taps and process recreation unable to double-count.
 */
interface PracticeStore {
    suspend fun <T> transaction(block: suspend () -> T): T

    // Daily sets
    suspend fun setByDate(date: LocalDate): DailySet?
    suspend fun setById(id: Long): DailySet?
    suspend fun latestSetBefore(date: LocalDate): DailySet?
    suspend fun insertSet(date: LocalDate, level: PracticeLevel, createdAt: Long): Long
    suspend fun insertDailyQuestions(setId: Long, questions: List<Pair<Question, List<Int>>>)
    suspend fun questionsForSet(setId: Long): List<DailyQuestionRecord>
    suspend fun dailyQuestion(id: Long): DailyQuestionRecord?
    suspend fun markDailyAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Boolean
    suspend fun markSetCompleted(setId: Long, at: Long): Boolean
    /** Deletes all but the newest [keep] sets (and their questions). */
    suspend fun pruneDailySets(keep: Int)

    // Extra (calculator-result) practice
    suspend fun insertExtra(sourceCalculationId: Long?, question: Question, options: List<Int>, createdAt: Long): Long
    suspend fun extra(id: Long): ExtraPracticeRecord?
    suspend fun markExtraAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Boolean
    suspend fun pruneExtra(keep: Int)

    // Mistakes
    suspend fun mistakeByKey(key: String): MistakeRecord?
    suspend fun mistake(id: Long): MistakeRecord?
    suspend fun insertMistake(question: Question, key: String, at: Long): Long
    suspend fun registerRepeatMistake(id: Long, at: Long)
    suspend fun markMistakeResolved(id: Long, at: Long): Boolean

    // Retry attempts
    suspend fun retry(attemptId: String): MistakeRetryRecord?
    suspend fun insertRetry(attemptId: String, mistakeId: Long, options: List<Int>, createdAt: Long)
    suspend fun markRetryAnswered(attemptId: String, selected: Int, correct: Boolean, at: Long): Boolean
    suspend fun pruneRetries(keep: Int)

    // Totals
    suspend fun totals(): ProgressTotals
    suspend fun saveTotals(totals: ProgressTotals)
}
