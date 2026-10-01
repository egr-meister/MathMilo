package com.mathmilo.ui.question

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.theme.IslandColors

@Composable
fun DailyQuestionScreen(
    onBack: () -> Unit,
    onFinished: (setId: Long) -> Unit,
    onStartNewDay: () -> Unit,
) {
    val container = appContainer()
    val vm: DailyQuestionViewModel = viewModel(
        factory = containerFactory(container) { DailyQuestionViewModel(it, createSavedStateHandle()) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished, state.set?.id) {
        val id = state.set?.id
        if (state.finished && id != null && !state.newDayReady) onFinished(id)
    }

    val current = state.current
    ScreenScaffold(
        title = "Daily Challenge",
        onBack = onBack,
        backLabel = "Back to island",
    ) {
        when {
            state.newDayReady -> NewDayPanel(onStartNewDay, onBack)
            state.loading || current == null -> CircularProgressIndicator()
            else -> {
                val total = state.questions.size
                Text(
                    "Today: ${state.answeredCount} of $total answered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IslandColors.NavySoft,
                )
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else state.answeredCount / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "${state.answeredCount} of $total answered" },
                    color = IslandColors.LavenderDeep,
                    trackColor = IslandColors.Lavender,
                )
                ExpressionCard(current.question, header = "Question ${current.position} of $total")
                if (!state.canAnswer && !current.isAnswered) {
                    Panel(color = IslandColors.Sand) {
                        Text("This set belongs to an earlier day.", style = MaterialTheme.typography.bodyLarge)
                    }
                    SecondaryAction("Back to island", onBack)
                } else {
                    AnswerOptions(
                        options = current.options,
                        correctAnswer = current.question.answer,
                        selected = current.selectedAnswer,
                        enabled = state.canAnswer,
                        onSelect = vm::answer,
                    )
                }
                if (current.isAnswered) {
                    FeedbackPanel(current.question, current.isCorrect == true)
                    PrimaryAction(
                        if (state.allAnswered) "See today’s result" else "Next question",
                        vm::next,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewDayPanel(onStart: () -> Unit, onBack: () -> Unit) {
    Panel(color = IslandColors.Lavender) {
        Text("A new day is ready", style = MaterialTheme.typography.titleLarge)
        Text(
            "Yesterday’s answers are saved. A fresh set of 10 questions is waiting.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    PrimaryAction("Start today’s questions", onStart)
    SecondaryAction("Back to island", onBack)
}
