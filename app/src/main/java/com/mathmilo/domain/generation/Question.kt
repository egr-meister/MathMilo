package com.mathmilo.domain.generation

import com.mathmilo.domain.Operator

/** A whole-number arithmetic question. */
data class Question(
    val first: Int,
    val operator: Operator,
    val second: Int,
) {
    val answer: Int = when (operator) {
        Operator.ADD -> first + second
        Operator.SUBTRACT -> first - second
        Operator.MULTIPLY -> first * second
        Operator.DIVIDE -> {
            require(second != 0) { "Division by zero" }
            require(first % second == 0) { "Division must have no remainder" }
            first / second
        }
    }

    val expression: String get() = "$first ${operator.symbol} $second"
    val spokenExpression: String get() = "$first ${operator.spokenName} $second"

    /** Key used for duplicate detection: reversed operands are equal for + and ×. */
    val normalizedKey: String
        get() {
            val (a, b) = if (operator.isCommutative) minOf(first, second) to maxOf(first, second) else first to second
            return "$a${operator.code}$b"
        }
}

enum class PracticeLevel(
    val code: String,
    val title: String,
    val addSubRange: IntRange,
    val mulRange: IntRange,
    val maxDividend: Int,
    val maxDivisor: Int,
) {
    STARTER("STARTER", "Starter", 1..10, 1..5, 20, 10),
    GROWING("GROWING", "Growing", 1..50, 1..10, 100, 10),
    EXPLORER("EXPLORER", "Explorer", 1..100, 1..12, 144, 12);

    companion object {
        val DEFAULT = STARTER
        fun fromCode(code: String?): PracticeLevel = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** A question together with its four shuffled answer options. */
data class GeneratedQuestion(
    val question: Question,
    val options: List<Int>,
) {
    init {
        require(options.size == 4) { "Exactly four options required" }
        require(options.toSet().size == 4) { "Options must be distinct" }
        require(question.answer in options) { "Correct answer must be an option" }
    }
}

/** Encodes option lists for storage ("12,9,13,22"). */
object OptionCodec {
    fun encode(options: List<Int>): String = options.joinToString(",")
    fun decode(text: String): List<Int> = text.split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }
}
