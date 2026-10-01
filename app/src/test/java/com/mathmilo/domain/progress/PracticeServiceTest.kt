package com.mathmilo.domain.progress

import com.mathmilo.domain.Operator
import com.mathmilo.domain.daily.DayRules
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.ResultPracticeGenerator
import com.mathmilo.domain.mistakes.MistakeRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PracticeServiceTest {
    private val day1 = LocalDate.of(2026, 10, 1)
    private val store = InMemoryPracticeStore()
    private val time = FakeTime(day1)
    private val service = PracticeService(store, time, Random(42))

    private fun wrongOption(q: DailyQuestionRecord) = q.options.first { it != q.question.answer }

    @Test fun sameSetIsReusedAcrossRelaunches() = runBlocking {
        val a = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val qa = store.questionsForSet(a.id)
        // "Relaunch": a brand-new service with a different random source on the same store.
        val relaunched = PracticeService(store, time, Random(999))
        val b = relaunched.getOrCreateSet(day1, PracticeLevel.EXPLORER)
        assertEquals(a.id, b.id)
        assertEquals(PracticeLevel.STARTER, b.level)
        assertEquals(qa, store.questionsForSet(b.id))
        assertEquals(1, store.sets.size)
        assertEquals(10, qa.size)
    }

    @Test fun levelChangeAppliesToNextDayOnly() = runBlocking {
        service.getOrCreateSet(day1, PracticeLevel.STARTER)
        assertEquals(PracticeLevel.STARTER, service.getOrCreateSet(day1, PracticeLevel.GROWING).level)
        assertEquals(PracticeLevel.GROWING, service.getOrCreateSet(day1.plusDays(1), PracticeLevel.GROWING).level)
    }

    @Test fun duplicateAnswersCountOnce() = runBlocking {
        val set = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val q = store.questionsForSet(set.id).first()
        val first = service.answerDaily(q.id, q.question.answer)
        val second = service.answerDaily(q.id, wrongOption(q))
        assertTrue(first.recorded && first.correct)
        assertFalse(second.recorded)
        assertTrue(second.correct) // reports the original answer
        assertEquals(1, store.totals().dailyAnswered)
        assertEquals(1, store.totals().dailyCorrect)
        assertEquals(q.question.answer, store.dailyQuestion(q.id)!!.selectedAnswer)
        assertTrue(store.mistakes.isEmpty())
    }

    @Test fun completingTenAnswersCompletesTheDayOnce() = runBlocking {
        val set = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val qs = store.questionsForSet(set.id)
        var completions = 0
        qs.forEachIndexed { i, q ->
            val o = service.answerDaily(q.id, if (i % 3 == 0) wrongOption(q) else q.question.answer)
            if (o.setCompleted) completions++
        }
        qs.forEach { service.answerDaily(it.id, it.question.answer) }
        assertEquals(1, completions)
        assertEquals(1, store.totals().completedDays)
        assertNotNull(store.setById(set.id)!!.completedAt)
        val summary = DailySummary.of(store.setById(set.id)!!, store.questionsForSet(set.id))
        assertEquals(10, summary.answered)
        assertEquals(6, summary.correct)
    }

    @Test fun mistakesDeduplicateResolveAndReopen() = runBlocking {
        val set = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val q = store.questionsForSet(set.id).first()
        service.answerDaily(q.id, wrongOption(q))
        assertEquals(1, store.mistakes.size)

        // The same expression wrong again on another day → same row, updated.
        val day2 = service.getOrCreateSet(day1.plusDays(1), PracticeLevel.STARTER)
        val extraId = service.createResultPractice(
            q.question.answer, ResultPracticeGenerator.Source(null, null, null), null,
        )
        // Force a known-matching mistake via extra practice with an identical question
        store.extras.replaceAll { if (it.id == extraId) it.copy(question = q.question, options = q.options) else it }
        service.answerExtra(extraId, wrongOption(q))
        assertEquals(1, store.mistakes.size)
        assertEquals(2, store.mistakes.single().incorrectAttempts)
        assertNotNull(day2)

        // Wrong retry keeps it active.
        val m = store.mistakes.single()
        val r1 = service.getOrCreateRetry("attempt-1", m.id)
        assertEquals(4, r1.options.toSet().size)
        assertTrue(m.question.answer in r1.options)
        val wrong = service.answerRetry("attempt-1", r1.options.first { it != m.question.answer })
        assertFalse(wrong.correct)
        assertEquals(1, MistakeRules.groupActive(store.mistakes).values.flatten().size)

        // Correct retry resolves (once), even with repeated taps.
        val r2 = service.getOrCreateRetry("attempt-2", m.id)
        val ok = service.answerRetry("attempt-2", m.question.answer)
        val again = service.answerRetry("attempt-2", m.question.answer)
        assertTrue(ok.mistakeResolved)
        assertFalse(again.recorded)
        assertTrue(MistakeRules.groupActive(store.mistakes).isEmpty())
        assertEquals(1, store.totals().resolvedMistakeCount)
        assertNotNull(r2)

        // Answering the same expression wrong later reopens it.
        val extra2 = service.createResultPractice(5, ResultPracticeGenerator.Source(null, null, null), null)
        store.extras.replaceAll { if (it.id == extra2) it.copy(question = q.question, options = q.options) else it }
        service.answerExtra(extra2, wrongOption(q))
        assertEquals(1, store.mistakes.size)
        assertNull(store.mistakes.single().resolvedAt)
    }

    @Test fun retryRecreationKeepsSameOptions() = runBlocking {
        val set = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val q = store.questionsForSet(set.id).first()
        service.answerDaily(q.id, wrongOption(q))
        val m = store.mistakes.single()
        val a = service.getOrCreateRetry("same", m.id)
        val b = PracticeService(store, time, Random(1)).getOrCreateRetry("same", m.id)
        assertEquals(a.options, b.options)
    }

    @Test fun retriesNeverChangeOriginalDailyScore() = runBlocking {
        val set = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        val qs = store.questionsForSet(set.id)
        qs.forEach { service.answerDaily(it.id, wrongOption(it)) }
        val before = DailySummary.of(store.setById(set.id)!!, store.questionsForSet(set.id))
        val totalsBefore = store.totals()
        store.mistakes.toList().forEachIndexed { i, m ->
            service.getOrCreateRetry("r$i", m.id)
            service.answerRetry("r$i", m.question.answer)
        }
        val after = DailySummary.of(store.setById(set.id)!!, store.questionsForSet(set.id))
        assertEquals(before.correct, after.correct)
        assertEquals(0, after.correct)
        assertEquals(totalsBefore.dailyCorrect, store.totals().dailyCorrect)
        assertEquals(totalsBefore.dailyAnswered, store.totals().dailyAnswered)
        assertTrue(MistakeRules.groupActive(store.mistakes).isEmpty())
    }

    @Test fun extraPracticeDoesNotAffectDailyCounters() = runBlocking {
        val id = service.createResultPractice(24, ResultPracticeGenerator.Source(6, Operator.MULTIPLY, 4), 77L)
        val e = store.extra(id)!!
        assertEquals(24, e.question.answer)
        service.answerExtra(id, e.question.answer)
        service.answerExtra(id, e.question.answer)
        assertEquals(0, store.totals().dailyAnswered)
        assertEquals(1, store.totals().extraAnswered)
        assertEquals(1, store.totals().extraCorrect)
    }

    @Test fun midnightAndDateRollback() = runBlocking {
        val set1 = service.getOrCreateSet(day1, PracticeLevel.STARTER)
        // Question opened on day1; midnight passes; it may still be answered in its original set.
        val openedOn = day1
        time.date = day1.plusDays(1)
        assertTrue(DayRules.canAnswer(set1.localDate, openedOn))
        val q = store.questionsForSet(set1.id).first()
        assertTrue(service.answerDaily(q.id, q.question.answer).recorded)
        assertTrue(DayRules.isNewDayReady(set1.localDate, time.today()))
        // A screen opened on day2 cannot continue day1's set.
        assertFalse(DayRules.canAnswer(set1.localDate, time.today()))

        val set2 = service.getOrCreateSet(time.today(), PracticeLevel.STARTER)
        assertTrue(set2.id != set1.id)

        // Clock goes back to day1 → existing set reused, no new grant.
        time.date = day1
        assertEquals(set1.id, service.getOrCreateSet(time.today(), PracticeLevel.STARTER).id)
        assertEquals(2, store.sets.size)
        // The incomplete day stays incomplete; unanswered questions are not errors.
        val s = DailySummary.of(store.setById(set1.id)!!, store.questionsForSet(set1.id))
        assertFalse(s.isComplete)
        assertEquals(1, s.answered)
    }

    @Test fun retentionKeepsTotalsAndUnresolvedMistakes() = runBlocking {
        var firstMistakeKey: String? = null
        for (d in 0 until 95) {
            val set = service.getOrCreateSet(day1.plusDays(d.toLong()), PracticeLevel.STARTER)
            store.questionsForSet(set.id).forEachIndexed { i, q ->
                service.answerDaily(q.id, if (i == 0) wrongOption(q) else q.question.answer)
            }
            if (d == 0) firstMistakeKey = store.mistakes.first().normalizedKey
        }
        assertEquals(PracticeService.KEEP_DAILY_SETS, store.sets.size)
        assertEquals(95, store.totals().completedDays)
        assertEquals(950, store.totals().dailyAnswered)
        assertEquals(855, store.totals().dailyCorrect)
        assertNotNull(store.mistakeByKey(firstMistakeKey!!))

        repeat(60) { service.createResultPractice(10, ResultPracticeGenerator.Source(null, null, null), null) }
        assertEquals(PracticeService.KEEP_EXTRA_PRACTICE, store.extras.size)
    }

    @Test fun accuracyText() {
        assertEquals("No answers yet", Accuracy.text(0, 0))
        assertEquals("75%", Accuracy.text(3, 4))
    }
}
