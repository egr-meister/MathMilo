package com.mathmilo.domain.daily

import com.mathmilo.domain.Operator
import com.mathmilo.domain.generation.Distractors
import com.mathmilo.domain.generation.GeneratedQuestion
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import java.time.LocalDate
import kotlin.random.Random

/**
 * Generates the 10-question daily set locally.
 *
 * Mix: two questions per operation plus two extra questions from two distinct operations
 * that rotate by date. The final order is shuffled and no question repeats within a set
 * (reversed operands count as duplicates for + and ×).
 */
object DailyGenerator {
    const val QUESTIONS_PER_DAY = 10
    private val OPS = listOf(Operator.ADD, Operator.SUBTRACT, Operator.MULTIPLY, Operator.DIVIDE)
    private const val RANDOM_TRIES = 60
    private const val SET_RETRIES = 5

    fun extraOperations(date: LocalDate): List<Operator> {
        val idx = Math.floorMod(date.toEpochDay(), 4L).toInt()
        return listOf(OPS[idx], OPS[(idx + 1) % 4])
    }

    fun operationPlan(date: LocalDate): List<Operator> = OPS.flatMap { listOf(it, it) } + extraOperations(date)

    /**
     * @param previousKeys normalized keys of the most recent earlier set, used to avoid an identical
     * consecutive set where practical.
     */
    fun generate(
        date: LocalDate,
        level: PracticeLevel,
        random: Random,
        previousKeys: Set<String> = emptySet(),
    ): List<GeneratedQuestion> {
        var attempt = 0
        while (true) {
            val questions = generateOnce(date, level, random)
            val keys = questions.map { it.normalizedKey }.toSet()
            attempt++
            if (keys != previousKeys || attempt >= SET_RETRIES) {
                return questions.map { GeneratedQuestion(it, Distractors.optionsFor(it, random)) }
            }
        }
    }

    private fun generateOnce(date: LocalDate, level: PracticeLevel, random: Random): List<Question> {
        val used = HashSet<String>()
        val result = ArrayList<Question>(QUESTIONS_PER_DAY)
        for (op in operationPlan(date)) {
            val q = uniqueQuestion(op, level, random, used)
            used += q.normalizedKey
            result += q
        }
        return result.shuffled(random)
    }

    private fun uniqueQuestion(op: Operator, level: PracticeLevel, random: Random, used: Set<String>): Question {
        repeat(RANDOM_TRIES) {
            val q = randomQuestion(op, level, random)
            if (q.normalizedKey !in used) return q
        }
        // Deterministic fallback over the full candidate space for the level.
        return allQuestions(op, level).first { it.normalizedKey !in used }
    }

    fun randomQuestion(op: Operator, level: PracticeLevel, random: Random): Question {
        val r = level.addSubRange
        return when (op) {
            Operator.ADD -> Question(r.random(random), op, r.random(random))
            Operator.SUBTRACT -> {
                val x = r.random(random)
                val y = r.random(random)
                Question(maxOf(x, y), op, minOf(x, y))
            }
            Operator.MULTIPLY -> Question(level.mulRange.random(random), op, level.mulRange.random(random))
            Operator.DIVIDE -> {
                val divisor = (2..level.maxDivisor).random(random)
                val quotient = (1..(level.maxDividend / divisor)).random(random)
                Question(divisor * quotient, op, divisor)
            }
        }
    }

    fun allQuestions(op: Operator, level: PracticeLevel): Sequence<Question> {
        val r = level.addSubRange
        return when (op) {
            Operator.ADD -> r.asSequence().flatMap { a -> r.asSequence().map { b -> Question(a, op, b) } }
            Operator.SUBTRACT -> r.asSequence().flatMap { a -> r.asSequence().filter { it <= a }.map { b -> Question(a, op, b) } }
            Operator.MULTIPLY -> level.mulRange.asSequence()
                .flatMap { a -> level.mulRange.asSequence().map { b -> Question(a, op, b) } }
            Operator.DIVIDE -> (1..level.maxDivisor).asSequence().flatMap { d ->
                (1..(level.maxDividend / d)).asSequence().map { q -> Question(d * q, op, d) }
            }
        }
    }
}
