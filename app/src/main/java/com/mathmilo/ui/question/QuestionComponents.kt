package com.mathmilo.ui.question

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathmilo.domain.generation.Explanations
import com.mathmilo.domain.generation.Question
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.theme.ExpressionStyle
import com.mathmilo.ui.theme.IslandColors

const val FEEDBACK_CORRECT = "That’s right!"
const val FEEDBACK_INCORRECT = "Let’s work through it."

@Composable
fun ExpressionCard(question: Question, header: String?) {
    Panel(color = IslandColors.Card) {
        if (header != null) {
            Text(header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Text(question.operator.label, style = MaterialTheme.typography.bodyLarge, color = IslandColors.NavySoft)
        Text(
            "${question.expression} = ?",
            style = ExpressionStyle.copy(fontSize = 44.sp),
            color = IslandColors.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "What is ${question.spokenExpression}?" },
        )
    }
}

/**
 * Four large answer buttons. After answering they are disabled, the selected one is marked and
 * the correct one is shown — using symbols and words, not only colour.
 */
@Composable
fun AnswerOptions(
    options: List<Int>,
    correctAnswer: Int,
    selected: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val answered = selected != null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val isCorrect = option == correctAnswer
                    val isSelected = option == selected
                    val (bg, fg, mark, state) = when {
                        answered && isCorrect -> Quad(IslandColors.CorrectBg, IslandColors.Correct, "✓ ", if (isSelected) "Your answer, correct" else "Correct answer")
                        answered && isSelected -> Quad(IslandColors.IncorrectBg, IslandColors.Incorrect, "✗ ", "Your answer, not correct")
                        else -> Quad(IslandColors.Sand, IslandColors.Navy, "", "")
                    }
                    OutlinedButton(
                        onClick = { if (!answered && enabled) onSelect(option) },
                        enabled = !answered && enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp)
                            .semantics { if (state.isNotEmpty()) stateDescription = state },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) fg else IslandColors.SandDeep),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = bg,
                            contentColor = fg,
                            disabledContainerColor = bg,
                            disabledContentColor = if (answered && (isCorrect || isSelected)) fg else IslandColors.NavySoft,
                        ),
                    ) {
                        Text("$mark$option", style = ExpressionStyle.copy(fontSize = 28.sp))
                    }
                }
            }
        }
    }
}

private data class Quad(val bg: Color, val fg: Color, val mark: String, val state: String)

@Composable
fun FeedbackPanel(question: Question, correct: Boolean) {
    Panel(color = if (correct) IslandColors.CorrectBg else IslandColors.Lavender) {
        Text(
            (if (correct) "✓ " else "• ") + (if (correct) FEEDBACK_CORRECT else FEEDBACK_INCORRECT),
            style = MaterialTheme.typography.titleLarge,
            color = if (correct) IslandColors.Correct else IslandColors.Navy,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!correct) {
            Text("The answer is ${question.answer}.", style = MaterialTheme.typography.bodyLarge)
        }
        Text(Explanations.explain(question), style = MaterialTheme.typography.bodyLarge)
        Counters(question)
    }
}

/** Optional dot counters for small values; the explanation text is the accessible alternative. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Counters(question: Question) {
    val groups = Explanations.counterGroups(question) ?: return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Counters: " + groups.joinToString(" and ") { "$it" }
        },
    ) {
        groups.forEachIndexed { index, count ->
            val color = if (index % 2 == 0) IslandColors.LavenderDeep else IslandColors.WaterDeep
            DotGroup(count, color)
        }
    }
}

@Composable
private fun DotGroup(count: Int, color: Color) {
    val perRow = 5
    val rows = (count + perRow - 1) / perRow
    val dot = 12.dp
    Canvas(Modifier.size(width = dot * perRow + 4.dp * (perRow - 1), height = dot * rows + 4.dp * (rows - 1).coerceAtLeast(0))) {
        val d = dot.toPx()
        val gap = 4.dp.toPx()
        for (i in 0 until count) {
            val r = i / perRow
            val c = i % perRow
            drawCircle(color, radius = d / 2, center = androidx.compose.ui.geometry.Offset(c * (d + gap) + d / 2, r * (d + gap) + d / 2))
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = IslandColors.Navy, contentColor = Color.White),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, IslandColors.NavySoft),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = IslandColors.Card, contentColor = IslandColors.Navy),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
