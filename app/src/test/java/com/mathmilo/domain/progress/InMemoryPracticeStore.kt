package com.mathmilo.domain.progress

import com.mathmilo.domain.daily.TimeProvider
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import java.time.LocalDate

/** Simple in-memory implementation mirroring the Room store's conditional semantics. */
class InMemoryPracticeStore : PracticeStore {
    val sets = mutableListOf<DailySet>()
    val questions = mutableListOf<DailyQuestionRecord>()
    val extras = mutableListOf<ExtraPracticeRecord>()
    val mistakes = mutableListOf<MistakeRecord>()
    val retries = mutableListOf<MistakeRetryRecord>()
    var totalsRow = ProgressTotals()
    private var nextId = 1L

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun setByDate(date: LocalDate) = sets.firstOrNull { it.localDate == date }
    override suspend fun setById(id: Long) = sets.firstOrNull { it.id == id }
    override suspend fun latestSetBefore(date: LocalDate) =
        sets.filter { it.localDate < date }.maxByOrNull { it.localDate }

    override suspend fun insertSet(date: LocalDate, level: PracticeLevel, createdAt: Long): Long {
        require(sets.none { it.localDate == date }) { "UNIQUE constraint failed: localDate" }
        val id = nextId++
        sets += DailySet(id, date, level, createdAt, null)
        return id
    }

    override suspend fun insertDailyQuestions(setId: Long, questions: List<Pair<Question, List<Int>>>) {
        questions.forEachIndexed { i, (q, o) ->
            this.questions += DailyQuestionRecord(nextId++, setId, i + 1, q, o, null, null, null)
        }
    }

    override suspend fun questionsForSet(setId: Long) = questions.filter { it.dailySetId == setId }.sortedBy { it.position }
    override suspend fun dailyQuestion(id: Long) = questions.firstOrNull { it.id == id }

    override suspend fun markDailyAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Boolean {
        val i = questions.indexOfFirst { it.id == id && it.selectedAnswer == null }
        if (i < 0) return false
        questions[i] = questions[i].copy(selectedAnswer = selected, isCorrect = correct, answeredAt = at)
        return true
    }

    override suspend fun markSetCompleted(setId: Long, at: Long): Boolean {
        val i = sets.indexOfFirst { it.id == setId && it.completedAt == null }
        if (i < 0) return false
        sets[i] = sets[i].copy(completedAt = at)
        return true
    }

    override suspend fun pruneDailySets(keep: Int) {
        val remove = sets.sortedByDescending { it.localDate }.drop(keep).map { it.id }.toSet()
        sets.removeAll { it.id in remove }
        questions.removeAll { it.dailySetId in remove }
    }

    override suspend fun insertExtra(sourceCalculationId: Long?, question: Question, options: List<Int>, createdAt: Long): Long {
        val id = nextId++
        extras += ExtraPracticeRecord(id, sourceCalculationId, question, options, createdAt, null, null, null)
        return id
    }

    override suspend fun extra(id: Long) = extras.firstOrNull { it.id == id }

    override suspend fun markExtraAnswered(id: Long, selected: Int, correct: Boolean, at: Long): Boolean {
        val i = extras.indexOfFirst { it.id == id && it.selectedAnswer == null }
        if (i < 0) return false
        extras[i] = extras[i].copy(selectedAnswer = selected, isCorrect = correct, answeredAt = at)
        return true
    }

    override suspend fun pruneExtra(keep: Int) {
        val remove = extras.sortedByDescending { it.id }.drop(keep).map { it.id }.toSet()
        extras.removeAll { it.id in remove }
    }

    override suspend fun mistakeByKey(key: String) = mistakes.firstOrNull { it.normalizedKey == key }
    override suspend fun mistake(id: Long) = mistakes.firstOrNull { it.id == id }

    override suspend fun insertMistake(question: Question, key: String, at: Long): Long {
        val id = nextId++
        mistakes += MistakeRecord(id, key, question, at, at, 1, null)
        return id
    }

    override suspend fun registerRepeatMistake(id: Long, at: Long) {
        val i = mistakes.indexOfFirst { it.id == id }
        mistakes[i] = mistakes[i].copy(lastSeenAt = at, incorrectAttempts = mistakes[i].incorrectAttempts + 1, resolvedAt = null)
    }

    override suspend fun markMistakeResolved(id: Long, at: Long): Boolean {
        val i = mistakes.indexOfFirst { it.id == id && it.resolvedAt == null }
        if (i < 0) return false
        mistakes[i] = mistakes[i].copy(resolvedAt = at)
        return true
    }

    override suspend fun retry(attemptId: String) = retries.firstOrNull { it.attemptId == attemptId }

    override suspend fun insertRetry(attemptId: String, mistakeId: Long, options: List<Int>, createdAt: Long) {
        if (retries.none { it.attemptId == attemptId }) {
            retries += MistakeRetryRecord(attemptId, mistakeId, options, createdAt, null, null, null)
        }
    }

    override suspend fun markRetryAnswered(attemptId: String, selected: Int, correct: Boolean, at: Long): Boolean {
        val i = retries.indexOfFirst { it.attemptId == attemptId && it.selectedAnswer == null }
        if (i < 0) return false
        retries[i] = retries[i].copy(selectedAnswer = selected, isCorrect = correct, answeredAt = at)
        return true
    }

    override suspend fun pruneRetries(keep: Int) {
        val remove = retries.sortedByDescending { it.createdAt }.drop(keep).map { it.attemptId }.toSet()
        retries.removeAll { it.attemptId in remove }
    }

    override suspend fun totals() = totalsRow
    override suspend fun saveTotals(totals: ProgressTotals) { totalsRow = totals }
}

class FakeTime(var date: LocalDate, var millis: Long = 1_000L) : TimeProvider {
    override fun nowMillis(): Long = millis++
    override fun today(): LocalDate = date
}
