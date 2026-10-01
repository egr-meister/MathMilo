package com.mathmilo.domain.generation

import com.mathmilo.domain.Operator
import com.mathmilo.domain.daily.DailyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class GenerationTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test fun dailySetHasBalancedOperationsAcrossLevelsAndSeeds() {
        for (level in PracticeLevel.entries) for (seed in 0 until 200) {
            val date = start.plusDays(seed.toLong())
            val set = DailyGenerator.generate(date, level, Random(seed))
            assertEquals(10, set.size)
            val counts = set.groupingBy { it.question.operator }.eachCount()
            for (op in Operator.entries) assertTrue("$level $seed $op", (counts[op] ?: 0) >= 2)
            val extras = DailyGenerator.extraOperations(date)
            assertEquals(2, extras.distinct().size)
            for (op in extras) assertEquals(3, counts[op])
        }
    }

    @Test fun extraOperationsRotateByDate() {
        val seen = (0L until 4L).map { DailyGenerator.extraOperations(start.plusDays(it)) }.toSet()
        assertEquals(4, seen.size)
    }

    @Test fun noDuplicatesIncludingReversedOperands() {
        for (level in PracticeLevel.entries) for (seed in 0 until 300) {
            val set = DailyGenerator.generate(start, level, Random(seed))
            assertEquals(10, set.map { it.question.normalizedKey }.toSet().size)
        }
        assertEquals(Question(3, Operator.ADD, 4).normalizedKey, Question(4, Operator.ADD, 3).normalizedKey)
        assertEquals(Question(3, Operator.MULTIPLY, 4).normalizedKey, Question(4, Operator.MULTIPLY, 3).normalizedKey)
        assertNotEquals(Question(8, Operator.SUBTRACT, 4).normalizedKey, Question(4, Operator.SUBTRACT, 8).normalizedKey)
    }

    @Test fun questionsRespectLevelRanges() {
        for (level in PracticeLevel.entries) for (seed in 0 until 300) {
            for (g in DailyGenerator.generate(start, level, Random(seed))) {
                val q = g.question
                when (q.operator) {
                    Operator.ADD -> { assertTrue(q.first in level.addSubRange); assertTrue(q.second in level.addSubRange) }
                    Operator.SUBTRACT -> {
                        assertTrue(q.first in level.addSubRange); assertTrue(q.second in level.addSubRange)
                        assertTrue(q.answer >= 0)
                    }
                    Operator.MULTIPLY -> { assertTrue(q.first in level.mulRange); assertTrue(q.second in level.mulRange) }
                    Operator.DIVIDE -> {
                        assertTrue(q.first in 1..level.maxDividend)
                        assertTrue(q.second in 1..level.maxDivisor)
                        assertEquals(0, q.first % q.second)
                        assertTrue(q.answer > 0)
                    }
                }
            }
        }
    }

    @Test fun fourDistinctOptionsWithCorrectAnswerAndSignRules() {
        for (level in PracticeLevel.entries) for (seed in 0 until 300) {
            for (g in DailyGenerator.generate(start, level, Random(seed))) {
                assertEquals(4, g.options.toSet().size)
                assertTrue(g.question.answer in g.options)
                val min = if (g.question.operator == Operator.ADD || g.question.operator == Operator.SUBTRACT) 0 else 1
                assertTrue(g.options.all { it >= min })
            }
        }
    }

    @Test fun distractorFallbackWorksForTinyAnswers() {
        val q = Question(1, Operator.DIVIDE, 1)
        repeat(50) {
            val o = Distractors.optionsFor(q, Random(it))
            assertEquals(4, o.toSet().size)
            assertTrue(o.all { v -> v >= 1 })
        }
        val zero = Question(5, Operator.SUBTRACT, 5)
        repeat(50) {
            val o = Distractors.optionsFor(zero, Random(it))
            assertEquals(4, o.toSet().size)
            assertTrue(0 in o && o.all { v -> v >= 0 })
        }
    }

    @Test fun avoidsIdenticalConsecutiveSet() {
        val first = DailyGenerator.generate(start, PracticeLevel.STARTER, Random(7))
        val keys = first.map { it.question.normalizedKey }.toSet()
        // Even with the same seed, the generator will retry when keys match the previous set.
        val second = DailyGenerator.generate(start, PracticeLevel.STARTER, Random(7), previousKeys = keys)
        assertNotEquals(keys, second.map { it.question.normalizedKey }.toSet())
    }

    // --- Result-based practice ---------------------------------------------------------

    private fun src(a: Int?, op: Operator?, b: Int?) = ResultPracticeGenerator.Source(a, op, b)

    @Test fun resultPracticeAlwaysProducesTheResultForAllValues() {
        for (r in 0..1000) for (op in Operator.entries + listOf<Operator?>(null)) {
            val g = ResultPracticeGenerator.generate(r, src(null, op, null), Random(r))
            assertEquals(r, g.question.answer)
            assertEquals(4, g.options.toSet().size)
            assertTrue(g.question.first >= 0 && g.question.second >= 0)
            if (g.question.operator == Operator.DIVIDE) assertTrue(g.question.second > 0)
        }
    }

    @Test fun resultPracticePrefersSourceOperationAndAvoidsSourceExpression() {
        val cases = listOf(
            Triple(7, Operator.ADD, 5),
            Triple(15, Operator.SUBTRACT, 7),
            Triple(6, Operator.MULTIPLY, 4),
            Triple(20, Operator.DIVIDE, 5),
        )
        for ((a, op, b) in cases) for (seed in 0 until 100) {
            val result = Question(a, op, b).answer
            val q = ResultPracticeGenerator.generate(result, src(a, op, b), Random(seed)).question
            assertEquals(result, q.answer)
            assertEquals(op, q.operator)
            assertFalse(q.first == a && q.second == b)
            if (op.isCommutative) assertFalse(q.first == b && q.second == a)
        }
    }

    @Test fun primeMultiplicationFallsBackToAdditionOrSubtraction() {
        for (seed in 0 until 50) {
            val q = ResultPracticeGenerator.generate(13, src(13, Operator.MULTIPLY, 1), Random(seed)).question
            assertEquals(13, q.answer)
            assertTrue(q.operator != Operator.MULTIPLY || (q.first >= 2 && q.second >= 2))
        }
    }

    @Test fun zeroUsesSubtraction() {
        for (seed in 0 until 50) {
            val q = ResultPracticeGenerator.generate(0, src(3, Operator.SUBTRACT, 3), Random(seed)).question
            assertEquals(Operator.SUBTRACT, q.operator)
            assertEquals(0, q.answer)
            assertFalse(q.first == 3 && q.second == 3)
        }
    }

    // --- Explanations -------------------------------------------------------------------

    private val equation = Regex("""(\d+) ([+−×÷]) (\d+) = (\d+)""")

    private fun checkAllEquations(text: String) {
        for (m in equation.findAll(text)) {
            val (a, op, b, r) = m.destructured
            val expected = when (op) {
                "+" -> a.toInt() + b.toInt()
                "−" -> a.toInt() - b.toInt()
                "×" -> a.toInt() * b.toInt()
                else -> a.toInt() / b.toInt()
            }
            assertEquals("Wrong equation in: $text", expected, r.toInt())
        }
    }

    @Test fun specExampleExplanations() {
        assertEquals("7 + 5 = 12. Add 3 to reach 10, then add 2 more.", Explanations.explain(Question(7, Operator.ADD, 5)))
        assertEquals("6 × 4 = 24. Six groups of 4 make 24.", Explanations.explain(Question(6, Operator.MULTIPLY, 4)))
        assertTrue(Explanations.explain(Question(15, Operator.SUBTRACT, 7)).startsWith("15 − 7 = 8. Taking 7 away from 15 leaves 8."))
        assertTrue(Explanations.explain(Question(20, Operator.DIVIDE, 5)).startsWith("20 ÷ 5 = 4. Split 20 into 5 equal groups of 4."))
    }

    @Test fun explanationsAreMathematicallyCorrectEverywhere() {
        for (level in PracticeLevel.entries) for (op in Operator.entries) {
            for (q in DailyGenerator.allQuestions(op, level)) {
                val text = Explanations.explain(q)
                assertTrue(text.startsWith("${q.first} ${op.symbol} ${q.second} = ${q.answer}."))
                checkAllEquations(text)
                if (op == Operator.ADD && q.first < 10 && q.second < 10 && q.answer > 10) {
                    val m = Regex("""Add (\d+) to reach 10, then add (\d+) more""").find(text)!!
                    assertEquals(10, q.first + m.groupValues[1].toInt())
                    assertEquals(q.answer, 10 + m.groupValues[2].toInt())
                }
            }
        }
        for (r in 0..1000) {
            val q = ResultPracticeGenerator.generate(r, src(null, null, null), Random(r)).question
            checkAllEquations(Explanations.explain(q))
        }
    }
}
