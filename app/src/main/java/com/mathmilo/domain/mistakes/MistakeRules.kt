package com.mathmilo.domain.mistakes

import com.mathmilo.domain.Operator
import com.mathmilo.domain.generation.Question
import com.mathmilo.domain.progress.MistakeRecord

object MistakeRules {
    const val EMPTY_STATE = "No mistakes to practice right now."

    /** Normalized expression key (reversed operands are the same mistake for + and ×). */
    fun key(question: Question): String = question.normalizedKey

    /** Unresolved mistakes grouped by operation, in a stable operation order. */
    fun groupActive(mistakes: List<MistakeRecord>): Map<Operator, List<MistakeRecord>> =
        mistakes.filter { !it.isResolved }
            .sortedByDescending { it.lastSeenAt }
            .groupBy { it.question.operator }
            .toSortedMap(compareBy { it.ordinal })
}
