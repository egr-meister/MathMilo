package com.mathmilo.domain.progress

import com.mathmilo.domain.daily.DailyGenerator
import com.mathmilo.domain.daily.TimeProvider
import com.mathmilo.domain.generation.Distractors
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import com.mathmilo.domain.generation.ResultPracticeGenerator
import com.mathmilo.domain.mistakes.MistakeRules
import java.time.LocalDate
import kotlin.random.Random

/** Result of answering any question. */
data class AnswerOutcome(
    val correct: Boolean,
    /** True when this call recorded the answer; false when it was already answered earlier. */
    val recorded: Boolean,
    val setCompleted: Boolean = false,
    val mistakeResolved: Boolean = false,
)

/**
 * All practice rules live here: one persistent set per local date, answer-once semantics,
 * mistake deduplication/resolution/reopening, score isolation and retention.
 */
class PracticeService(
    private val store: PracticeStore,
    private val time: TimeProvider,
    private val random: Random,
) {
    companion object {
        const val KEEP_DAILY_SETS = 90
        const val KEEP_EXTRA_PRACTICE = 50
        const val KEEP_RETRY_ATTEMPTS = 200
    }

    /** Returns the set for [date], generating and persisting the complete set first if needed. */
    suspend fun getOrCreateSet(date: LocalDate, level: PracticeLevel): DailySet = store.transaction {
        store.setByDate(date)?.let { return@transaction it }
        val previousKeys = store.latestSetBefore(date)
            ?.let { prev -> store.questionsForSet(prev.id).map { it.question.normalizedKey }.toSet() }
            ?: emptySet()
        val generated = DailyGenerator.generate(date, level, random, previousKeys)
        val setId = store.insertSet(date, level, time.nowMillis())
        store.insertDailyQuestions(setId, generated.map { it.question to it.options })
        store.pruneDailySets(KEEP_DAILY_SETS)
        store.setById(setId)!!
    }

    suspend fun answerDaily(questionId: Long, selected: Int): AnswerOutcome = store.transaction {
        val q = store.dailyQuestion(questionId) ?: error("Unknown question $questionId")
        if (q.selectedAnswer != null) {
            return@transaction AnswerOutcome(correct = q.isCorrect == true, recorded = false)
        }
        val now = time.nowMillis()
        val correct = selected == q.question.answer
        if (!store.markDailyAnswered(questionId, selected, correct, now)) {
            return@transaction AnswerOutcome(correct, recorded = false)
        }
        var totals = store.totals()
        totals = totals.copy(
            dailyAnswered = totals.dailyAnswered + 1,
            dailyCorrect = totals.dailyCorrect + if (correct) 1 else 0,
        )
        if (!correct) recordMistake(q.question, now)

        var completed = false
        val all = store.questionsForSet(q.dailySetId)
        if (all.all { it.isAnswered } && store.markSetCompleted(q.dailySetId, now)) {
            completed = true
            totals = totals.copy(completedDays = totals.completedDays + 1)
        }
        store.saveTotals(totals)
        AnswerOutcome(correct, recorded = true, setCompleted = completed)
    }

    /** Creates a calculator-result practice question. Returns its id. */
    suspend fun createResultPractice(
        result: Int,
        source: ResultPracticeGenerator.Source,
        sourceCalculationId: Long?,
    ): Long = store.transaction {
        val generated = ResultPracticeGenerator.generate(result, source, random)
        val id = store.insertExtra(sourceCalculationId, generated.question, generated.options, time.nowMillis())
        store.pruneExtra(KEEP_EXTRA_PRACTICE)
        id
    }

    suspend fun answerExtra(id: Long, selected: Int): AnswerOutcome = store.transaction {
        val e = store.extra(id) ?: error("Unknown practice $id")
        if (e.selectedAnswer != null) return@transaction AnswerOutcome(e.isCorrect == true, recorded = false)
        val now = time.nowMillis()
        val correct = selected == e.question.answer
        if (!store.markExtraAnswered(id, selected, correct, now)) return@transaction AnswerOutcome(correct, false)
        val t = store.totals()
        store.saveTotals(
            t.copy(extraAnswered = t.extraAnswered + 1, extraCorrect = t.extraCorrect + if (correct) 1 else 0),
        )
        if (!correct) recordMistake(e.question, now)
        AnswerOutcome(correct, recorded = true)
    }

    /** Returns (creating if needed) a retry attempt with freshly shuffled options. */
    suspend fun getOrCreateRetry(attemptId: String, mistakeId: Long): MistakeRetryRecord = store.transaction {
        store.retry(attemptId)?.let { return@transaction it }
        val m = store.mistake(mistakeId) ?: error("Unknown mistake $mistakeId")
        val options = Distractors.optionsFor(m.question, random)
        store.insertRetry(attemptId, mistakeId, options, time.nowMillis())
        store.pruneRetries(KEEP_RETRY_ATTEMPTS)
        store.retry(attemptId)!!
    }

    /**
     * Answers a retry. A correct answer resolves the mistake (it leaves the active list); an
     * incorrect one keeps it. Original daily scores are never touched.
     */
    suspend fun answerRetry(attemptId: String, selected: Int): AnswerOutcome = store.transaction {
        val r = store.retry(attemptId) ?: error("Unknown attempt $attemptId")
        if (r.selectedAnswer != null) return@transaction AnswerOutcome(r.isCorrect == true, recorded = false)
        val m = store.mistake(r.mistakeId) ?: error("Unknown mistake ${r.mistakeId}")
        val now = time.nowMillis()
        val correct = selected == m.question.answer
        if (!store.markRetryAnswered(attemptId, selected, correct, now)) return@transaction AnswerOutcome(correct, false)
        var resolved = false
        if (correct) {
            if (store.markMistakeResolved(m.id, now)) {
                resolved = true
                val t = store.totals()
                store.saveTotals(t.copy(resolvedMistakeCount = t.resolvedMistakeCount + 1))
            }
        } else {
            store.registerRepeatMistake(m.id, now)
        }
        AnswerOutcome(correct, recorded = true, mistakeResolved = resolved)
    }

    /** Dedupe by normalized expression; reopen a resolved mistake if answered incorrectly again. */
    private suspend fun recordMistake(question: Question, now: Long) {
        val key = MistakeRules.key(question)
        val existing = store.mistakeByKey(key)
        if (existing == null) store.insertMistake(question, key, now) else store.registerRepeatMistake(existing.id, now)
    }
}
