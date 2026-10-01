package com.mathmilo.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.domain.progress.DailySet
import com.mathmilo.domain.progress.DailySummary
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.StatRow
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.question.PrimaryAction
import com.mathmilo.ui.question.SecondaryAction
import com.mathmilo.ui.theme.ExpressionStyle
import com.mathmilo.ui.theme.IslandColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ResultUiState(val set: DailySet? = null, val questions: List<DailyQuestionRecord> = emptyList())

class ResultViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val setId: Long = checkNotNull(handle.get<Long>("setId"))
    val state: StateFlow<ResultUiState> = combine(
        container.practice.observeSet(setId),
        container.practice.observeQuestions(setId),
    ) { set, qs -> ResultUiState(set, qs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState())
}

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH))

@Composable
fun ResultScreen(onBackToIsland: () -> Unit, onPracticeMistakes: () -> Unit) {
    val container = appContainer()
    val vm: ResultViewModel = viewModel(factory = containerFactory(container) { ResultViewModel(it, createSavedStateHandle()) })
    val state by vm.state.collectAsStateWithLifecycle()
    val set = state.set

    ScreenScaffold(title = "Daily result", onBack = onBackToIsland, backLabel = "Back to island") {
        if (set == null) {
            CircularProgressIndicator()
            return@ScreenScaffold
        }
        val summary = DailySummary.of(set, state.questions)
        Panel(color = IslandColors.Lavender) {
            Text(formatDate(set.localDate), style = MaterialTheme.typography.titleMedium)
            Text(
                "${summary.correct} of ${summary.total} correct",
                style = MaterialTheme.typography.headlineMedium,
            )
            StatRow("Accuracy", summary.accuracyText)
            StatRow("Level", set.level.title)
            if (!summary.isComplete) {
                StatRow("Status", "Incomplete — ${summary.answered} of ${summary.total} answered")
            }
        }
        Text("Question review", style = MaterialTheme.typography.titleMedium)
        Panel {
            state.questions.forEachIndexed { index, q ->
                if (index > 0) HorizontalDivider(color = IslandColors.SkyDeep)
                ReviewRow(q)
            }
        }
        PrimaryAction("Practice mistakes", onPracticeMistakes)
        SecondaryAction("Back to island", onBackToIsland)
    }
}

@Composable
private fun ReviewRow(q: DailyQuestionRecord) {
    val status = when (q.isCorrect) {
        true -> "✓ Correct"
        false -> "✗ Answered ${q.selectedAnswer}"
        null -> "– Not answered"
    }
    val color = when (q.isCorrect) {
        true -> IslandColors.Correct
        false -> IslandColors.Incorrect
        null -> IslandColors.NavySoft
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Question ${q.position}: ${q.question.spokenExpression} equals ${q.question.answer}. $status"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${q.position}.  ${q.question.expression} = ${q.question.answer}", style = ExpressionStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
        }
        Text(status, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
