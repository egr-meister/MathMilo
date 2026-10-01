package com.mathmilo.domain.generation

import com.mathmilo.domain.Operator
import kotlin.random.Random

/**
 * Builds four distinct integer options (one correct) with plausible distractors.
 * Bounded: a fixed candidate list followed by a deterministic fallback walk.
 */
object Distractors {

    fun optionsFor(question: Question, random: Random): List<Int> {
        val answer = question.answer
        val minAllowed = when (question.operator) {
            Operator.ADD, Operator.SUBTRACT -> 0
            Operator.MULTIPLY, Operator.DIVIDE -> 1
        }
        fun valid(v: Int) = v != answer && v >= minAllowed

        val plausible = plausibleCandidates(question).filter(::valid).distinct().shuffled(random)
        val chosen = LinkedHashSet<Int>()
        for (c in plausible) {
            if (chosen.size == 3) break
            chosen += c
        }
        // Deterministic fallback: walk outward from the answer.
        var step = 1
        while (chosen.size < 3) {
            val up = answer + step
            if (valid(up)) chosen += up
            if (chosen.size < 3) {
                val down = answer - step
                if (valid(down)) chosen += down
            }
            step++
        }
        return (chosen.toList().take(3) + answer).shuffled(random)
    }

    private fun plausibleCandidates(q: Question): List<Int> {
        val a = q.first
        val b = q.second
        val ans = q.answer
        val list = mutableListOf(ans + 1, ans - 1, ans + 2, ans - 2, ans + 10, ans - 10)
        when (q.operator) {
            Operator.ADD -> {
                list += a - b
                list += kotlin.math.abs(a - b)
                list += ans + 1
            }
            Operator.SUBTRACT -> {
                list += a + b
                list += ans + b
            }
            Operator.MULTIPLY -> {
                list += a + b
                list += (a + 1) * b
                list += (a - 1) * b
                list += a * (b + 1)
                list += a * (b - 1)
            }
            Operator.DIVIDE -> {
                list += a - b
                list += b
                if (b > 1 && a % (b - 1) == 0) list += a / (b - 1)
                if (a % (b + 1) == 0) list += a / (b + 1)
            }
        }
        return list
    }
}
