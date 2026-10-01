package com.mathmilo.ui.question

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.progress.ExtraPracticeRecord
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.theme.IslandColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExtraPracticeViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.practice
    private val id: Long = checkNotNull(handle.get<Long>("practiceId"))
    private var answering = false

    val record: StateFlow<ExtraPracticeRecord?> =
        repo.observeExtra(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun answer(option: Int) {
        val r = record.value ?: return
        if (r.selectedAnswer != null || answering) return
        answering = true
        viewModelScope.launch {
            try { repo.answerExtra(id, option) } finally { answering = false }
        }
    }
}

/** Practice built from a calculator result. The calculator and its result are hidden here. */
@Composable
fun ExtraPracticeScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: ExtraPracticeViewModel = viewModel(
        factory = containerFactory(container) { ExtraPracticeViewModel(it, createSavedStateHandle()) },
    )
    val record by vm.record.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Result practice", onBack = onBack, backLabel = "Back to island") {
        val r = record
        if (r == null) {
            CircularProgressIndicator()
            return@ScreenScaffold
        }
        Text(
            "Extra practice — it doesn’t change today’s 10 questions.",
            style = MaterialTheme.typography.bodyMedium,
            color = IslandColors.NavySoft,
        )
        ExpressionCard(r.question, header = null)
        AnswerOptions(
            options = r.options,
            correctAnswer = r.question.answer,
            selected = r.selectedAnswer,
            enabled = true,
            onSelect = vm::answer,
        )
        if (r.selectedAnswer != null) {
            FeedbackPanel(r.question, r.isCorrect == true)
            PrimaryAction("Back to island", onBack)
        }
    }
}
