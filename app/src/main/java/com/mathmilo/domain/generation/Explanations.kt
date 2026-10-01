package com.mathmilo.domain.generation

import com.mathmilo.domain.Operator

/** Locally generated, mathematically correct explanations. */
object Explanations {

    fun explain(q: Question): String {
        val a = q.first
        val b = q.second
        val ans = q.answer
        val head = "$a ${q.operator.symbol} $b = $ans."
        return when (q.operator) {
            Operator.ADD -> "$head ${addStrategy(a, b, ans)}"
            Operator.SUBTRACT -> when {
                b == 0 -> "$head Taking away 0 leaves $a unchanged."
                ans == 0 -> "$head Taking $b away from $a leaves nothing."
                else -> "$head Taking $b away from $a leaves $ans. Check: $ans + $b = $a."
            }
            Operator.MULTIPLY -> when {
                a == 0 || b == 0 -> "$head Any number of groups of 0, or 0 groups, make 0."
                else -> "$head ${capitalize(word(a))} ${if (a == 1) "group" else "groups"} of $b ${if (a == 1) "makes" else "make"} $ans."
            }
            Operator.DIVIDE -> when {
                a == 0 -> "$head Splitting 0 into $b equal groups gives 0 in each group."
                else -> "$head Split $a into $b equal ${if (b == 1) "group" else "groups"} of $ans. Check: $b × $ans = $a."
            }
        }
    }

    private fun addStrategy(a: Int, b: Int, sum: Int): String = when {
        a == 0 || b == 0 -> "Adding 0 keeps the number the same."
        a < 10 && b < 10 && sum > 10 && a != 10 -> {
            val toTen = 10 - a
            "Add $toTen to reach 10, then add ${b - toTen} more."
        }
        a < 10 && b < 10 -> "Start at $a and count on $b more to reach $sum."
        else -> {
            val aTens = a / 10 * 10
            val bTens = b / 10 * 10
            val aOnes = a % 10
            val bOnes = b % 10
            val tens = aTens + bTens
            val ones = aOnes + bOnes
            "Add the tens: $aTens + $bTens = $tens. Add the ones: $aOnes + $bOnes = $ones. Then $tens + $ones = $sum."
        }
    }

    private val words = listOf(
        "zero", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve",
    )

    private fun word(n: Int): String = if (n in words.indices) words[n] else n.toString()
    private fun capitalize(s: String) = s.replaceFirstChar { it.uppercaseChar() }

    /** Small visual counters are offered only for small values. */
    fun counterGroups(q: Question): List<Int>? = when (q.operator) {
        Operator.ADD -> if (q.answer in 1..20) listOf(q.first, q.second) else null
        Operator.MULTIPLY -> if (q.answer in 1..30 && q.first <= 6) List(q.first) { q.second } else null
        Operator.DIVIDE -> if (q.first in 1..30 && q.second <= 6) List(q.second) { q.answer } else null
        Operator.SUBTRACT -> null
    }
}
