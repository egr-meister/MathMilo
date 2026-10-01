package com.mathmilo.domain

/**
 * The four arithmetic operations used by the calculator and by practice questions.
 *
 * [code] is the stable persisted value; never change it once released.
 */
enum class Operator(
    val code: String,
    val symbol: String,
    val spokenName: String,
    val label: String,
) {
    ADD("ADD", "+", "plus", "Addition"),
    SUBTRACT("SUB", "−", "minus", "Subtraction"),
    MULTIPLY("MUL", "×", "times", "Multiplication"),
    DIVIDE("DIV", "÷", "divided by", "Division");

    val isCommutative: Boolean get() = this == ADD || this == MULTIPLY

    companion object {
        fun fromCode(code: String): Operator =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown operator code: $code")
    }
}
