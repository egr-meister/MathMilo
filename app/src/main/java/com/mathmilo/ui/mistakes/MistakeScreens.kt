package com.mathmilo.ui.mistakes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.Operator
import com.mathmilo.domain.mistakes.MistakeRules
import com.mathmilo.domain.progress.MistakeRecord
import com.mathmilo.domain.progress.MistakeRetryRecord
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.question.AnswerOptions
import com.mathmilo.ui.question.ExpressionCard
import com.mathmilo.ui.question.FeedbackPanel
import com.mathmilo.ui.question.PrimaryAction
import com.mathmilo.ui.theme.ExpressionStyle
import com.mathmilo.ui.theme.IslandColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// ------------------------------------------------------------------------------------------
// List

class MistakeListViewModel(container: AppContainer) : ViewModel() {
    val groups: StateFlow<Map<Operator, List<MistakeRecord>>?> = container.practice.observeActiveMistakes()
        .map { MistakeRules.groupActive(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun MistakeListScreen(onBack: () -> Unit, onRetry: (mistakeId: Long) -> Unit) {
    val container = appContainer()
    val vm: MistakeListViewModel = viewModel(factory = containerFactory(container) { MistakeListViewModel(it) })
    val groups by vm.groups.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Mistake Practice", onBack = onBack, backLabel = "Back to island") {
        val g = groups
        when {
            g == null -> CircularProgressIndicator()
            g.isEmpty() -> Panel(color = IslandColors.Mint) {
                Text(MistakeRules.EMPTY_STATE, style = MaterialTheme.typography.titleMedium)
            }
            else -> {
                Text(
                    "Questions to try again. Take your time.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = IslandColors.NavySoft,
                )
                g.forEach { (op, items) ->
                    Text(
                        "${op.label} (${items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Panel {
                        items.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = IslandColors.SkyDeep)
                            MistakeRow(m, onRetry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeRow(m: MistakeRecord, onRetry: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            // The correct answer is intentionally not shown here.
            Text(
                "${m.question.expression} = ?",
                style = ExpressionStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                modifier = Modifier.semantics { contentDescription = m.question.spokenExpression },
            )
            Text(m.question.operator.label, style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
        }
        Button(
            onClick = { onRetry(m.id) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Try again: ${m.question.spokenExpression}" },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IslandColors.Navy, contentColor = Color.White),
        ) { Text("Try again") }
    }
}

// ------------------------------------------------------------------------------------------
// Retry

data class RetryUiState(val mistake: MistakeRecord? = null, val attempt: MistakeRetryRecord? = null)

class MistakeRetryViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.practice
    private val mistakeId: Long = checkNotNull(handle.get<Long>("mistakeId"))
    private val attemptId: String = handle.get<String>("attemptId") ?: UUID.randomUUID().toString().also {
        handle["attemptId"] = it
    }
    private val _state = MutableStateFlow(RetryUiState())
    val state: StateFlow<RetryUiState> = _state.asStateFlow()
    private var answering = false

    init {
        viewModelScope.launch {
            val m = repo.mistake(mistakeId)
            val attempt = if (m != null) repo.getOrCreateRetry(attemptId, mistakeId) else null
            _state.value = RetryUiState(m, attempt)
        }
    }

    fun answer(option: Int) {
        val a = _state.value.attempt ?: return
        if (a.selectedAnswer != null || answering) return
        answering = true
        viewModelScope.launch {
            try {
                repo.answerRetry(attemptId, option)
                // Refresh from storage: the attempt is the source of truth for the feedback shown.
                _state.value = _state.value.copy(attempt = repo.retry(attemptId))
            } finally {
                answering = false
            }
        }
    }
}

@Composable
fun MistakeRetryScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: MistakeRetryViewModel = viewModel(
        factory = containerFactory(container) { MistakeRetryViewModel(it, createSavedStateHandle()) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Try again", onBack = onBack, backLabel = "Back to Mistake Practice") {
        val m = state.mistake
        val a = state.attempt
        if (m == null || a == null) {
            CircularProgressIndicator()
            return@ScreenScaffold
        }
        ExpressionCard(m.question, header = null)
        AnswerOptions(
            options = a.options,
            correctAnswer = m.question.answer,
            selected = a.selectedAnswer,
            enabled = true,
            onSelect = vm::answer,
        )
        if (a.selectedAnswer != null) {
            val correct = a.isCorrect == true
            FeedbackPanel(m.question, correct)
            Text(
                if (correct) "This one is done — it has left your practice list."
                else "It stays in your practice list for another try later.",
                style = MaterialTheme.typography.bodyLarge,
            )
            PrimaryAction("Back to Mistake Practice", onBack)
        }
    }
}
