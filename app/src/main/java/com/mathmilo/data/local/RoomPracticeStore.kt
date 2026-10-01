package com.mathmilo.data.local

import androidx.room.withTransaction
import com.mathmilo.domain.generation.OptionCodec
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import com.mathmilo.domain.progress.PracticeStore
import com.mathmilo.domain.progress.ProgressTotals
import java.time.LocalDate

/** Room-backed [PracticeStore]. All multi-step operations run inside [transaction]. */
class RoomPracticeStore(private val db: MathMiloDatabase) : PracticeStore {
    private val dao = db.practiceDao()

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun setByDate(date: LocalDate) = dao.setByDate(date.toString())?.toDomain()
    override suspend fun setById(id: Long) = dao.setById(id)?.toDomain()
    override suspend fun latestSetBefore(date: LocalDate) = dao.latestSetBefore(date.toString())?.toDomain()

    override suspend fun insertSet(date: LocalDate, level: PracticeLevel, createdAt: Long): Long =
        dao.insertSet(DailySetEntity(localDate = date.toString(), level = level.code, createdAt = createdAt))

    override suspend fun insertDailyQuestions(setId: Long, questions: List<Pair<Question, List<Int>>>) {
        dao.insertQuestions(
            questions.mapIndexed { i, (q, options) ->
                DailyQuestionEntity(
                    dailySetId = setId,
                    position = i + 1,
                    firstOperand = q.first,
                    operatorCode = q.operator.code,
                    secondOperand = q.second,
                    correctAnswer = q.answer,
                    options = OptionCodec.encode(options),
                )
            },
        )
    }

    override suspend fun questionsForSet(setId: Long) = dao.questionsForSet(setId).map { it.toDomain() }
    override suspend fun dailyQuestion(id: Long) = dao.question(id)?.toDomain()

    override suspend fun markDailyAnswered(id: Long, selected: Int, correct: Boolean, at: Long) =
        dao.markAnswered(id, selected, correct, at) == 1

    override suspend fun markSetCompleted(setId: Long, at: Long) = dao.markCompleted(setId, at) == 1

    override suspend fun pruneDailySets(keep: Int) {
        dao.pruneQuestions(keep)
        dao.pruneSets(keep)
    }

    override suspend fun insertExtra(sourceCalculationId: Long?, question: Question, options: List<Int>, createdAt: Long) =
        dao.insertExtra(
            ExtraPracticeEntity(
                sourceCalculationId = sourceCalculationId,
                firstOperand = question.first,
                operatorCode = question.operator.code,
                secondOperand = question.second,
                correctAnswer = question.answer,
                options = OptionCodec.encode(options),
                createdAt = createdAt,
            ),
        )

    override suspend fun extra(id: Long) = dao.extra(id)?.toDomain()

    override suspend fun markExtraAnswered(id: Long, selected: Int, correct: Boolean, at: Long) =
        dao.markExtraAnswered(id, selected, correct, at) == 1

    override suspend fun pruneExtra(keep: Int) = dao.pruneExtra(keep)

    override suspend fun mistakeByKey(key: String) = dao.mistakeByKey(key)?.toDomain()
    override suspend fun mistake(id: Long) = dao.mistake(id)?.toDomain()

    override suspend fun insertMistake(question: Question, key: String, at: Long): Long =
        dao.insertMistake(
            MistakeEntity(
                normalizedExpressionKey = key,
                firstOperand = question.first,
                operatorCode = question.operator.code,
                secondOperand = question.second,
                correctAnswer = question.answer,
                firstSeenAt = at,
                lastSeenAt = at,
                incorrectAttempts = 1,
            ),
        )

    override suspend fun registerRepeatMistake(id: Long, at: Long) = dao.registerRepeat(id, at)
    override suspend fun markMistakeResolved(id: Long, at: Long) = dao.markResolved(id, at) == 1

    override suspend fun retry(attemptId: String) = dao.retry(attemptId)?.toDomain()

    override suspend fun insertRetry(attemptId: String, mistakeId: Long, options: List<Int>, createdAt: Long) {
        dao.insertRetry(MistakeRetryEntity(attemptId, mistakeId, OptionCodec.encode(options), createdAt))
    }

    override suspend fun markRetryAnswered(attemptId: String, selected: Int, correct: Boolean, at: Long) =
        dao.markRetryAnswered(attemptId, selected, correct, at) == 1

    override suspend fun pruneRetries(keep: Int) = dao.pruneRetries(keep)

    override suspend fun totals(): ProgressTotals = dao.totals().toDomain()
    override suspend fun saveTotals(totals: ProgressTotals) = dao.saveTotals(totals.toEntity())
}
