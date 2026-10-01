package com.mathmilo.domain.generation

import com.mathmilo.domain.Operator
import kotlin.random.Random

/**
 * Builds a single question whose correct answer equals a calculator result (0..1000).
 *
 * The question must not reproduce the calculator expression or merely swap commutative operands.
 * Candidate search is bounded and ends with a guaranteed subtraction fallback.
 */
object ResultPracticeGenerator {

    data class Source(val first: Int?, val operator: Operator?, val second: Int?)

    fun generate(result: Int, source: Source, random: Random): GeneratedQuestion {
        require(result in 0..1000) { "Result must be in 0..1000" }
        val question = pickQuestion(result, source, random)
        check(question.answer == result)
        return GeneratedQuestion(question, Distractors.optionsFor(question, random))
    }

    internal fun pickQuestion(result: Int, source: Source, random: Random): Question {
        if (result == 0) return zeroQuestion(source, random)

        val order = buildList {
            source.operator?.let { add(it) }
            addAll(listOf(Operator.ADD, Operator.SUBTRACT, Operator.MULTIPLY, Operator.DIVIDE).shuffled(random))
        }.distinct()

        for (op in order) {
            val candidates = candidates(result, op).filterNot { sameAsSource(it, source) }
            if (candidates.isEmpty()) continue
            val nontrivial = candidates.filter(::isNontrivial)
            return (nontrivial.ifEmpty { candidates }).random(random)
        }
        return subtractionFallback(result, source)
    }

    private fun candidates(result: Int, op: Operator): List<Question> = when (op) {
        Operator.ADD -> (1 until result).map { Question(it, Operator.ADD, result - it) }
            .filter { it.first <= it.second || result < 4 } // one ordering per pair keeps the list compact
        Operator.SUBTRACT -> (1..20).map { Question(result + it, Operator.SUBTRACT, it) }
        Operator.MULTIPLY -> if (result < 4) emptyList() else (2..kotlin.math.sqrt(result.toDouble()).toInt())
            .filter { result % it == 0 }
            .map { Question(result / it, Operator.MULTIPLY, it) }
        Operator.DIVIDE -> (2..10).map { Question(result * it, Operator.DIVIDE, it) }
    }

    private fun isNontrivial(q: Question): Boolean = when (q.operator) {
        Operator.ADD -> q.first >= 2 && q.second >= 2
        Operator.SUBTRACT -> q.second >= 2
        Operator.MULTIPLY -> q.first >= 2 && q.second >= 2
        Operator.DIVIDE -> q.second >= 2
    }

    private fun sameAsSource(q: Question, s: Source): Boolean {
        if (s.first == null || s.second == null || s.operator == null) return false
        if (q.operator != s.operator) return false
        if (q.first == s.first && q.second == s.second) return true
        return q.operator.isCommutative && q.first == s.second && q.second == s.first
    }

    private fun zeroQuestion(source: Source, random: Random): Question {
        val start = 2 + random.nextInt(9) // 2..10
        val q = Question(start, Operator.SUBTRACT, start)
        return if (sameAsSource(q, source)) Question(start + 1, Operator.SUBTRACT, start + 1) else q
    }

    /** Guaranteed valid: (result + k) − k for the first k that differs from the source. */
    private fun subtractionFallback(result: Int, source: Source): Question {
        var k = 2
        while (true) {
            val q = Question(result + k, Operator.SUBTRACT, k)
            if (!sameAsSource(q, source)) return q
            k++
        }
    }
}
