package com.mathmilo.domain.calculator

import com.mathmilo.domain.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private fun press(keys: String, start: CalculatorState = CalculatorState()): Pair<CalculatorState, List<CalculationResult>> {
        var s = start
        val done = mutableListOf<CalculationResult>()
        for (k in keys) {
            val out = when (k) {
                in '0'..'9' -> CalculatorEngine.digit(s, k - '0')
                '.' -> CalculatorEngine.decimal(s)
                '+' -> CalculatorEngine.operator(s, Operator.ADD)
                '-' -> CalculatorEngine.operator(s, Operator.SUBTRACT)
                '*' -> CalculatorEngine.operator(s, Operator.MULTIPLY)
                '/' -> CalculatorEngine.operator(s, Operator.DIVIDE)
                '=' -> CalculatorEngine.equals(s)
                '<' -> CalculatorEngine.backspace(s)
                'C' -> CalculatorEngine.clear(s)
                'n' -> CalculatorEngine.toggleSign(s)
                ' ' -> continue
                else -> error("bad key $k")
            }
            s = out.state
            out.completed?.let { done += it }
        }
        return s to done
    }

    @Test fun addition() {
        val (s, done) = press("12+30=")
        assertEquals("42", s.result!!.valueText)
        assertEquals(1, done.size)
    }

    @Test fun decimalAdditionIsExact() {
        assertEquals("0.3", press("0.1+0.2=").first.result!!.valueText)
    }

    @Test fun divisionRoundsHalfUpToSixDigitsAndMarksApproximation() {
        val r = press("2/3=").first.result!!
        assertEquals("0.666667", r.valueText)
        assertTrue(r.rounded)
        assertTrue(press("2/3=").first.mainText.startsWith("≈"))
    }

    @Test fun exactDivisionIsNotRoundedAndTrailingZerosRemoved() {
        val r = press("10/4=").first.result!!
        assertEquals("2.5", r.valueText)
        assertFalse(r.rounded)
    }

    @Test fun multiplicationBeyondSixDecimalsIsRounded() {
        val r = press("0.001*0.0005=").first.result!!
        assertEquals("0.000001", r.valueText)
        assertTrue(r.rounded)
    }

    @Test fun divisionByZeroShowsMessageAndNoHistory() {
        val (s, done) = press("5/0=")
        assertEquals(CalculatorMessage.DIVIDE_BY_ZERO, s.message)
        assertNull(s.result)
        assertTrue(done.isEmpty())
    }

    @Test fun resultOutOfRangeIsAnError() {
        val (s, done) = press("1000000+1=")
        assertEquals(CalculatorMessage.OUT_OF_RANGE, s.message)
        assertTrue(done.isEmpty())
        assertEquals(CalculatorMessage.OUT_OF_RANGE, press("1000*1001=").first.message)
    }

    @Test fun maxValueIsAllowed() {
        assertEquals("1000000", press("999999+1=").first.result!!.valueText)
        assertEquals("-1000000", press("-1000000*1=").first.result!!.valueText)
    }

    @Test fun operandInputLimitBlocksOverflowAndExtraFractionDigits() {
        val (s, _) = press("10000000")
        assertEquals("1000000", s.first)
        assertEquals(CalculatorMessage.INPUT_LIMIT, s.message)
        assertEquals("0.123456", press("0.1234567").first.first)
    }

    @Test fun multipleDecimalPointsPrevented() {
        assertEquals("1.25", press("1..2.5").first.first)
    }

    @Test fun leadingZerosNormalized() {
        assertEquals("7", press("0007").first.first)
        assertEquals("0.07", press("00.07").first.first)
    }

    @Test fun decimalOnEmptyOperandGivesZeroPoint() {
        assertEquals("0.", press(".").first.first)
        assertEquals("0.", press("5+.").first.second)
    }

    @Test fun operatorBeforeSecondOperandReplacesOperator() {
        val (s, _) = press("8+*")
        assertEquals(Operator.MULTIPLY, s.operator)
        assertEquals("32", press("8+*4=").first.result!!.valueText)
    }

    @Test fun equalsRequiresTwoOperands() {
        val (s, done) = press("8+=")
        assertNull(s.result)
        assertTrue(done.isEmpty())
        assertNull(press("=").first.result)
    }

    @Test fun repeatedEqualsDoesNotRepeat() {
        val (s, done) = press("2+3===")
        assertEquals("5", s.result!!.valueText)
        assertEquals(1, done.size)
    }

    @Test fun digitAfterResultStartsNewCalculation() {
        val s = press("2+3=7").first
        assertNull(s.result)
        assertNull(s.operator)
        assertEquals("7", s.first)
    }

    @Test fun operatorAfterResultContinues() {
        val (s, done) = press("2+3=*4=")
        assertEquals("20", s.result!!.valueText)
        assertEquals(2, done.size)
    }

    @Test fun backspaceEditsCurrentOperandAndAfterResult() {
        assertEquals("12", press("123<").first.first)
        assertEquals("4", press("12+45<").first.second)
        val s = press("12+30=<").first
        assertNull(s.result)
        assertEquals("4", s.first)
    }

    @Test fun clearResets() {
        assertEquals(CalculatorState(), press("12+3C").first)
    }

    @Test fun negativeOperandsAndResults() {
        assertEquals("-5", press("-2-3=").first.result!!.valueText)
        assertEquals("-6", press("2*3n=").first.result!!.valueText)
        assertEquals("1", press("3-2=").first.result!!.valueText)
    }

    @Test fun zeroResultIsCanonical() {
        assertEquals("0", press("0.5-0.5=").first.result!!.valueText)
    }

    @Test fun chainingEvaluatesPendingExpression() {
        val (s, done) = press("2+3*")
        assertEquals("5", s.first)
        assertEquals(Operator.MULTIPLY, s.operator)
        assertEquals(1, done.size)
    }

    @Test fun draftRoundTrip() {
        val s = press("12.5*3=").first
        val restored = CalculatorEngine.decode(CalculatorEngine.encode(s))
        assertEquals(s.result!!.valueText, restored.result!!.valueText)
        assertEquals(CalculatorState(), CalculatorEngine.decode("garbage"))
    }

    @Test fun practiceEligibility() {
        assertEquals(12, PracticeEligibility.eligibleValue(BigDecimal("12"), false))
        assertEquals(0, PracticeEligibility.eligibleValue(BigDecimal("0"), false))
        assertEquals(1000, PracticeEligibility.eligibleValue(BigDecimal("1000"), false))
        assertEquals(4, PracticeEligibility.eligibleValue(BigDecimal("4.000"), false))
        assertNull(PracticeEligibility.eligibleValue(BigDecimal("1001"), false))
        assertNull(PracticeEligibility.eligibleValue(BigDecimal("-3"), false))
        assertNull(PracticeEligibility.eligibleValue(BigDecimal("2.5"), false))
        assertNull(PracticeEligibility.eligibleValue(BigDecimal("1"), true))
        assertNotNull(press("6*4=").first.result!!.practiceValue)
        assertNull(press("1/3=").first.result!!.practiceValue)
    }
}
