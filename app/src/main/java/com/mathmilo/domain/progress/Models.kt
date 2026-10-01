package com.mathmilo.domain.progress

import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import java.time.LocalDate

data class DailySet(
    val id: Long,
    val localDate: LocalDate,
    val level: PracticeLevel,
    val createdAt: Long,
    val completedAt: Long?,
)

data class DailyQuestionRecord(
    val id: Long,
    val dailySetId: Long,
    val position: Int,
    val question: Question,
    val options: List<Int>,
    val selectedAnswer: Int?,
    val answeredAt: Long?,
    val isCorrect: Boolean?,
) {
    val isAnswered: Boolean get() = selectedAnswer != null
}

data class ExtraPracticeRecord(
    val id: Long,
    val sourceCalculationId: Long?,
    val question: Question,
    val options: List<Int>,
    val createdAt: Long,
    val selectedAnswer: Int?,
    val answeredAt: Long?,
    val isCorrect: Boolean?,
)

data class MistakeRecord(
    val id: Long,
    val normalizedKey: String,
    val question: Question,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val incorrectAttempts: Int,
    val resolvedAt: Long?,
) {
    val isResolved: Boolean get() = resolvedAt != null
}

/** One retry attempt; created when the retry screen opens so its shuffled options survive recreation. */
data class MistakeRetryRecord(
    val attemptId: String,
    val mistakeId: Long,
    val options: List<Int>,
    val createdAt: Long,
    val selectedAnswer: Int?,
    val isCorrect: Boolean?,
    val answeredAt: Long?,
)

data class ProgressTotals(
    val dailyAnswered: Int = 0,
    val dailyCorrect: Int = 0,
    val extraAnswered: Int = 0,
    val extraCorrect: Int = 0,
    val completedDays: Int = 0,
    val resolvedMistakeCount: Int = 0,
)

/** Summary of one daily set, computed from its questions. */
data class DailySummary(
    val set: DailySet,
    val answered: Int,
    val correct: Int,
    val total: Int,
) {
    val isComplete: Boolean get() = set.completedAt != null
    val accuracyText: String get() = Accuracy.text(correct, answered)

    companion object {
        fun of(set: DailySet, questions: List<DailyQuestionRecord>) = DailySummary(
            set = set,
            answered = questions.count { it.isAnswered },
            correct = questions.count { it.isCorrect == true },
            total = questions.size,
        )
    }
}

object Accuracy {
    const val NO_ANSWERS = "No answers yet"

    fun percent(correct: Int, answered: Int): Int? =
        if (answered <= 0) null else Math.round(correct * 100.0 / answered).toInt()

    fun text(correct: Int, answered: Int): String = percent(correct, answered)?.let { "$it%" } ?: NO_ANSWERS
}
