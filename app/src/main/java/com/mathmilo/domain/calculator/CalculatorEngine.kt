package com.mathmilo.domain.calculator

import com.mathmilo.domain.Operator
import java.math.BigDecimal
import java.math.RoundingMode

/** A completed, successful calculation. */
data class CalculationResult(
    val first: BigDecimal,
    val operator: Operator,
    val second: BigDecimal,
    val value: BigDecimal,
    val rounded: Boolean,
) {
    val valueText: String get() = Numbers.canonical(value)

    /** Exact integer in 0..1000 that was not rounded — eligible for "Turn result into practice". */
    val practiceValue: Int? get() = PracticeEligibility.eligibleValue(value, rounded)
}

enum class CalculatorMessage {
    DIVIDE_BY_ZERO,
    OUT_OF_RANGE,
    INPUT_LIMIT,
}

/**
 * Immutable calculator state. Operand fields hold the raw entry text ("-12.5", "0.").
 */
data class CalculatorState(
    val first: String = "",
    val operator: Operator? = null,
    val second: String = "",
    val result: CalculationResult? = null,
    val message: CalculatorMessage? = null,
) {
    /** Text shown on the small expression line. */
    val expressionText: String
        get() = when {
            result != null ->
                "${Numbers.canonical(result.first)} ${result.operator.symbol} ${Numbers.canonical(result.second)} ="
            operator != null -> "${displayOperand(first)} ${operator.symbol} ${displayOperand(second)}".trimEnd()
            else -> ""
        }

    /** Text shown on the large display line. */
    val mainText: String
        get() = when {
            result != null -> (if (result.rounded) "≈ " else "") + result.valueText
            operator != null && second.isNotEmpty() -> displayOperand(second)
            operator != null -> displayOperand(first)
            first.isNotEmpty() -> displayOperand(first)
            else -> "0"
        }

    /** Screen-reader friendly description of the display. */
    val spokenText: String
        get() = buildString {
            if (result != null) {
                append(spokenOperand(Numbers.canonical(result.first)))
                append(' ').append(result.operator.spokenName).append(' ')
                append(spokenOperand(Numbers.canonical(result.second)))
                append(if (result.rounded) " equals approximately " else " equals ")
                append(spokenOperand(result.valueText))
            } else {
                append(if (first.isEmpty()) "0" else spokenOperand(first))
                if (operator != null) {
                    append(' ').append(operator.spokenName)
                    if (second.isNotEmpty()) append(' ').append(spokenOperand(second))
                }
            }
        }

    private fun displayOperand(text: String) = text.replace("-", "−")
    private fun spokenOperand(text: String) =
        if (text.startsWith("-")) "negative " + text.drop(1) else text
}

/** Outcome of a key press: the new state, plus a record when a calculation completed. */
data class CalculatorOutput(val state: CalculatorState, val completed: CalculationResult? = null)

/**
 * Pure single-binary-operation calculator. All methods are side-effect free.
 */
object CalculatorEngine {

    fun digit(state: CalculatorState, d: Int): CalculatorOutput {
        require(d in 0..9)
        if (state.result != null) {
            return CalculatorOutput(CalculatorState(first = d.toString()))
        }
        val s = state.copy(message = null)
        return editCurrent(s) { current -> appendDigit(current, d) }
    }

    fun decimal(state: CalculatorState): CalculatorOutput {
        if (state.result != null) return CalculatorOutput(CalculatorState(first = "0."))
        val s = state.copy(message = null)
        return editCurrent(s) { current ->
            when {
                current.contains('.') -> current
                current.isEmpty() -> "0."
                current == "-" -> "-0."
                else -> "$current."
            }
        }
    }

    fun operator(state: CalculatorState, op: Operator): CalculatorOutput {
        val result = state.result
        if (result != null) {
            return CalculatorOutput(CalculatorState(first = result.valueText, operator = op))
        }
        val s = state.copy(message = null)
        if (!isValidOperand(s.first)) {
            // A minus on an empty first operand starts a negative number.
            return if (op == Operator.SUBTRACT && s.first.isEmpty()) {
                CalculatorOutput(s.copy(first = "-"))
            } else {
                CalculatorOutput(s)
            }
        }
        if (s.operator == null || s.second.isEmpty() || s.second == "-") {
            // Operator selected before the second operand replaces the current operator.
            return CalculatorOutput(s.copy(operator = op, second = ""))
        }
        // A complete expression is pending: evaluate it, then continue from its result.
        val evaluated = equals(s)
        val done = evaluated.completed ?: return evaluated
        return CalculatorOutput(
            CalculatorState(first = done.valueText, operator = op),
            completed = done,
        )
    }

    fun toggleSign(state: CalculatorState): CalculatorOutput {
        val result = state.result
        if (result != null) {
            val negated = Numbers.canonical(result.value.negate())
            return CalculatorOutput(CalculatorState(first = negated))
        }
        return editCurrent(state.copy(message = null)) { current ->
            if (current.startsWith("-")) current.drop(1) else "-$current"
        }
    }

    fun equals(state: CalculatorState): CalculatorOutput {
        if (state.result != null) return CalculatorOutput(state) // repeated Equals does nothing
        val op = state.operator ?: return CalculatorOutput(state)
        if (!isValidOperand(state.first) || !isValidOperand(state.second)) return CalculatorOutput(state)
        val a = Numbers.parse(state.first)
        val b = Numbers.parse(state.second)
        return when (val outcome = Arithmetic.compute(a, op, b)) {
            is Arithmetic.Outcome.Success -> {
                val calc = CalculationResult(a, op, b, outcome.value, outcome.rounded)
                CalculatorOutput(state.copy(result = calc, message = null), completed = calc)
            }
            Arithmetic.Outcome.DivideByZero ->
                CalculatorOutput(state.copy(message = CalculatorMessage.DIVIDE_BY_ZERO))
            Arithmetic.Outcome.OutOfRange ->
                CalculatorOutput(state.copy(message = CalculatorMessage.OUT_OF_RANGE))
        }
    }

    fun backspace(state: CalculatorState): CalculatorOutput {
        val result = state.result
        if (result != null) {
            // Backspace after a result makes that result editable.
            return CalculatorOutput(CalculatorState(first = result.valueText.dropLast(1).let(::cleanAfterDrop)))
        }
        val s = state.copy(message = null)
        return CalculatorOutput(
            when {
                s.operator != null && s.second.isNotEmpty() -> s.copy(second = cleanAfterDrop(s.second.dropLast(1)))
                s.operator != null -> s.copy(operator = null)
                else -> s.copy(first = cleanAfterDrop(s.first.dropLast(1)))
            },
        )
    }

    fun clear(@Suppress("UNUSED_PARAMETER") state: CalculatorState): CalculatorOutput =
        CalculatorOutput(CalculatorState())

    /** Replaces the calculator with a value picked from history. */
    fun useValue(value: BigDecimal): CalculatorState = CalculatorState(first = Numbers.canonical(value))

    // ---------------------------------------------------------------------------------------

    private fun editCurrent(state: CalculatorState, edit: (String) -> String): CalculatorOutput {
        val current = if (state.operator == null) state.first else state.second
        val edited = edit(current)
        if (edited != current && !withinInputLimits(edited)) {
            return CalculatorOutput(state.copy(message = CalculatorMessage.INPUT_LIMIT))
        }
        val next = if (state.operator == null) state.copy(first = edited) else state.copy(second = edited)
        return CalculatorOutput(next)
    }

    private fun appendDigit(current: String, d: Int): String {
        val negative = current.startsWith("-")
        val body = if (negative) current.drop(1) else current
        val newBody = if (body == "0") d.toString() else body + d
        return (if (negative) "-" else "") + newBody
    }

    private fun cleanAfterDrop(text: String): String = when (text) {
        "-0" -> "-0"
        else -> text
    }

    private fun isValidOperand(text: String): Boolean =
        text.isNotEmpty() && text != "-" && text.any { it.isDigit() }

    private fun withinInputLimits(text: String): Boolean {
        if (!isValidOperand(text)) return true
        val dot = text.indexOf('.')
        if (dot >= 0 && text.length - dot - 1 > Numbers.MAX_FRACTION_DIGITS) return false
        return Numbers.parse(text).abs() <= Numbers.MAX_ABS
    }

    // ---------------------------------------------------------------------------------------
    // Draft persistence (simple, versioned, human-readable).

    fun encode(state: CalculatorState): String {
        val r = state.result
        val resultPart = if (r == null) "" else listOf(
            Numbers.canonical(r.first), r.operator.code, Numbers.canonical(r.second),
            Numbers.canonical(r.value), r.rounded.toString(),
        ).joinToString(",")
        return listOf("v1", state.first, state.operator?.code.orEmpty(), state.second, resultPart).joinToString("|")
    }

    fun decode(text: String?): CalculatorState {
        if (text.isNullOrEmpty()) return CalculatorState()
        return runCatching {
            val parts = text.split("|")
            require(parts.size == 5 && parts[0] == "v1")
            val result = parts[4].takeIf { it.isNotEmpty() }?.split(",")?.let { r ->
                CalculationResult(
                    first = BigDecimal(r[0]), operator = Operator.fromCode(r[1]), second = BigDecimal(r[2]),
                    value = BigDecimal(r[3]), rounded = r[4].toBoolean(),
                )
            }
            CalculatorState(
                first = sanitize(parts[1]),
                operator = parts[2].takeIf { it.isNotEmpty() }?.let(Operator::fromCode),
                second = sanitize(parts[3]),
                result = result,
            )
        }.getOrDefault(CalculatorState())
    }

    private fun sanitize(text: String): String =
        if (text.isEmpty() || text == "-" || Regex("-?\\d+(\\.\\d*)?").matches(text)) text else ""
}

/** Shared numeric limits and helpers. */
object Numbers {
    val MAX_ABS: BigDecimal = BigDecimal(1_000_000)
    const val MAX_FRACTION_DIGITS = 6

    fun parse(text: String): BigDecimal = BigDecimal(text.trimEnd('.').let { if (it == "-0" ) "0" else it })

    /** Canonical string without unnecessary trailing zeros (and never "-0"). */
    fun canonical(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        return value.stripTrailingZeros().toPlainString()
    }
}

/** BigDecimal arithmetic with MathMilo's limits and rounding. */
object Arithmetic {
    sealed interface Outcome {
        data class Success(val value: BigDecimal, val rounded: Boolean) : Outcome
        data object DivideByZero : Outcome
        data object OutOfRange : Outcome
    }

    fun compute(a: BigDecimal, op: Operator, b: BigDecimal): Outcome {
        if (a.abs() > Numbers.MAX_ABS || b.abs() > Numbers.MAX_ABS) return Outcome.OutOfRange
        val raw: BigDecimal
        var rounded = false
        when (op) {
            Operator.ADD -> raw = a.add(b)
            Operator.SUBTRACT -> raw = a.subtract(b)
            Operator.MULTIPLY -> raw = a.multiply(b)
            Operator.DIVIDE -> {
                if (b.signum() == 0) return Outcome.DivideByZero
                val q = a.divide(b, Numbers.MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                rounded = q.multiply(b).compareTo(a) != 0
                raw = q
            }
        }
        var value = raw
        if (value.scale() > Numbers.MAX_FRACTION_DIGITS) {
            val scaled = value.setScale(Numbers.MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
            if (scaled.compareTo(value) != 0) rounded = true
            value = scaled
        }
        if (value.abs() > Numbers.MAX_ABS) return Outcome.OutOfRange
        return Outcome.Success(value.stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it }, rounded)
    }
}

object PracticeEligibility {
    const val MAX_VALUE = 1000
    const val UNSUPPORTED_MESSAGE = "Try a whole-number result from 0 to 1,000."

    fun eligibleValue(value: BigDecimal, rounded: Boolean): Int? {
        if (rounded) return null
        val stripped = value.stripTrailingZeros()
        if (stripped.signum() != 0 && stripped.scale() > 0) return null
        val asInt = runCatching { stripped.intValueExact() }.getOrNull() ?: return null
        return asInt.takeIf { it in 0..MAX_VALUE }
    }
}
