package com.mathmilo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CalculationEntity): Long

    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<CalculationEntity>>

    @Query("SELECT * FROM calculations WHERE token = :token LIMIT 1")
    suspend fun byToken(token: String): CalculationEntity?

    @Query(
        "DELETE FROM calculations WHERE id NOT IN " +
            "(SELECT id FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM calculations")
    suspend fun clear()
}

@Dao
interface PracticeDao {
    // ---- Daily sets -------------------------------------------------------------------
    @Query("SELECT * FROM daily_sets WHERE localDate = :date LIMIT 1")
    suspend fun setByDate(date: String): DailySetEntity?

    @Query("SELECT * FROM daily_sets WHERE id = :id")
    suspend fun setById(id: Long): DailySetEntity?

    @Query("SELECT * FROM daily_sets WHERE localDate < :date ORDER BY localDate DESC LIMIT 1")
    suspend fun latestSetBefore(date: String): DailySetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(entity: DailySetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestions(entities: List<DailyQuestionEntity>)

    @Query("SELECT * FROM daily_questions WHERE dailySetId = :setId ORDER BY position")
    suspend fun questionsForSet(setId: Long): List<DailyQuestionEntity>

    @Query("SELECT * FROM daily_questions WHERE id = :id")
    suspend fun question(id: Long): DailyQuestionEntity?

    @Query(
        "UPDATE daily_questions SET selectedAnswer = :selected, isCorrect = :correct, answeredAt = :at " +
            "WHERE id = :id AND selectedAnswer IS NULL",
    )
    suspend fun markAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Int

    @Query("UPDATE daily_sets SET completedAt = :at WHERE id = :id AND completedAt IS NULL")
    suspend fun markCompleted(id: Long, at: Long): Int

    @Query(
        "DELETE FROM daily_questions WHERE dailySetId NOT IN " +
            "(SELECT id FROM daily_sets ORDER BY localDate DESC LIMIT :keep)",
    )
    suspend fun pruneQuestions(keep: Int)

    @Query("DELETE FROM daily_sets WHERE id NOT IN (SELECT id FROM daily_sets ORDER BY localDate DESC LIMIT :keep)")
    suspend fun pruneSets(keep: Int)

    @Query("SELECT * FROM daily_sets WHERE localDate = :date LIMIT 1")
    fun observeSetByDate(date: String): Flow<DailySetEntity?>

    @Query("SELECT * FROM daily_sets WHERE id = :id")
    fun observeSet(id: Long): Flow<DailySetEntity?>

    @Query("SELECT * FROM daily_questions WHERE dailySetId = :setId ORDER BY position")
    fun observeQuestions(setId: Long): Flow<List<DailyQuestionEntity>>

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM daily_questions q WHERE q.dailySetId = s.id AND q.selectedAnswer IS NOT NULL) AS answered, " +
            "(SELECT COUNT(*) FROM daily_questions q WHERE q.dailySetId = s.id AND q.isCorrect = 1) AS correct, " +
            "(SELECT COUNT(*) FROM daily_questions q WHERE q.dailySetId = s.id) AS total " +
            "FROM daily_sets s ORDER BY s.localDate DESC LIMIT :limit",
    )
    fun observeRecentSets(limit: Int): Flow<List<DailySetWithStats>>

    // ---- Extra practice ---------------------------------------------------------------
    @Insert
    suspend fun insertExtra(entity: ExtraPracticeEntity): Long

    @Query("SELECT * FROM extra_practice WHERE id = :id")
    suspend fun extra(id: Long): ExtraPracticeEntity?

    @Query("SELECT * FROM extra_practice WHERE id = :id")
    fun observeExtra(id: Long): Flow<ExtraPracticeEntity?>

    @Query(
        "UPDATE extra_practice SET selectedAnswer = :selected, isCorrect = :correct, answeredAt = :at " +
            "WHERE id = :id AND selectedAnswer IS NULL",
    )
    suspend fun markExtraAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Int

    @Query("DELETE FROM extra_practice WHERE id NOT IN (SELECT id FROM extra_practice ORDER BY id DESC LIMIT :keep)")
    suspend fun pruneExtra(keep: Int)

    // ---- Mistakes ---------------------------------------------------------------------
    @Query("SELECT * FROM mistakes WHERE normalizedExpressionKey = :key LIMIT 1")
    suspend fun mistakeByKey(key: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun mistake(id: Long): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMistake(entity: MistakeEntity): Long

    @Query(
        "UPDATE mistakes SET lastSeenAt = :at, incorrectAttempts = incorrectAttempts + 1, resolvedAt = NULL " +
            "WHERE id = :id",
    )
    suspend fun registerRepeat(id: Long, at: Long)

    @Query("UPDATE mistakes SET resolvedAt = :at WHERE id = :id AND resolvedAt IS NULL")
    suspend fun markResolved(id: Long, at: Long): Int

    @Query("SELECT * FROM mistakes WHERE resolvedAt IS NULL ORDER BY lastSeenAt DESC")
    fun observeActiveMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT COUNT(*) FROM mistakes WHERE resolvedAt IS NULL")
    fun observeActiveMistakeCount(): Flow<Int>

    // ---- Retries ----------------------------------------------------------------------
    @Query("SELECT * FROM mistake_retries WHERE attemptId = :attemptId")
    suspend fun retry(attemptId: String): MistakeRetryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRetry(entity: MistakeRetryEntity): Long

    @Query(
        "UPDATE mistake_retries SET selectedAnswer = :selected, isCorrect = :correct, answeredAt = :at " +
            "WHERE attemptId = :attemptId AND selectedAnswer IS NULL",
    )
    suspend fun markRetryAnswered(attemptId: String, selected: Int, correct: Boolean, at: Long): Int

    @Query(
        "DELETE FROM mistake_retries WHERE attemptId NOT IN " +
            "(SELECT attemptId FROM mistake_retries ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun pruneRetries(keep: Int)

    // ---- Totals -----------------------------------------------------------------------
    @Query("SELECT * FROM progress_totals WHERE id = 1")
    suspend fun totals(): ProgressTotalsEntity?

    @Query("SELECT * FROM progress_totals WHERE id = 1")
    fun observeTotals(): Flow<ProgressTotalsEntity?>

    @Upsert
    suspend fun saveTotals(entity: ProgressTotalsEntity)

    // ---- Reset ------------------------------------------------------------------------
    @Query("DELETE FROM daily_questions")
    suspend fun clearQuestions()

    @Query("DELETE FROM daily_sets")
    suspend fun clearSets()

    @Query("DELETE FROM extra_practice")
    suspend fun clearExtra()

    @Query("DELETE FROM mistakes")
    suspend fun clearMistakes()

    @Query("DELETE FROM mistake_retries")
    suspend fun clearRetries()

    @Query("DELETE FROM progress_totals")
    suspend fun clearTotals()
}
