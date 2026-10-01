package com.mathmilo.ui.island

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.domain.progress.DailySet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IslandUiState(
    val today: LocalDate? = null,
    val set: DailySet? = null,
    val questions: List<DailyQuestionRecord> = emptyList(),
    val activeMistakes: Int = 0,
    val completedDays: Int = 0,
) {
    val answered: Int get() = questions.count { it.isAnswered }
    val allAnswered: Boolean get() = questions.isNotEmpty() && questions.all { it.isAnswered }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IslandViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.practice
    private val today = MutableStateFlow<LocalDate?>(null)

    init {
        refreshDay()
    }

    /** Re-checks the local date (on resume and periodically) and ensures today's set exists. */
    fun refreshDay() {
        viewModelScope.launch {
            val now = container.time.today()
            repo.getOrCreateSet(now, container.preferences.current().level)
            if (today.value != now) today.value = now
        }
    }

    private val setFlow = today.flatMapLatest { d -> if (d == null) flowOf(null) else repo.observeSetForDate(d) }
    private val questionsFlow = setFlow.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.observeQuestions(s.id) }

    val state: StateFlow<IslandUiState> = combine(
        today, setFlow, questionsFlow, repo.observeActiveMistakeCount(), repo.observeTotals(),
    ) { d, s, qs, mistakes, totals ->
        IslandUiState(d, s, qs, mistakes, totals.completedDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IslandUiState())
}
