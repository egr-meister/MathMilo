package com.mathmilo.ui.question

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.daily.DayRules
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.domain.progress.DailySet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DailyQuestionUiState(
    val loading: Boolean = true,
    val set: DailySet? = null,
    val questions: List<DailyQuestionRecord> = emptyList(),
    val current: DailyQuestionRecord? = null,
    val canAnswer: Boolean = false,
    val newDayReady: Boolean = false,
    val finished: Boolean = false,
) {
    val answeredCount: Int get() = questions.count { it.isAnswered }
    val allAnswered: Boolean get() = questions.isNotEmpty() && questions.all { it.isAnswered }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DailyQuestionViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
) : ViewModel() {
    companion object {
        const val TODAY = -1L
        private const val KEY_SET = "resolvedSetId"
        private const val KEY_OPENED = "openedOn"
        private const val KEY_CURRENT = "currentQuestionId"
        private const val KEY_NEW_DAY = "newDayReady"
        private const val KEY_FINISHED = "finished"
    }

    private val repo = container.practice
    private val requestedSetId: Long = handle.get<Long>("setId") ?: TODAY
    private val openedOn: LocalDate =
        handle.get<String>(KEY_OPENED)?.let(LocalDate::parse) ?: container.time.today().also {
            handle[KEY_OPENED] = it.toString()
        }

    private val setId = MutableStateFlow(handle.get<Long>(KEY_SET))
    private val currentId = handle.getStateFlow<Long?>(KEY_CURRENT, null)
    private val newDay = handle.getStateFlow(KEY_NEW_DAY, false)
    private val finished = handle.getStateFlow(KEY_FINISHED, false)
    private var answering = false

    init {
        viewModelScope.launch {
            if (setId.value == null) {
                val id = if (requestedSetId == TODAY) {
                    repo.getOrCreateSet(container.time.today(), container.preferences.current().level).id
                } else {
                    requestedSetId
                }
                handle[KEY_SET] = id
                setId.value = id
            }
            // Choose the first unanswered question if none is active yet.
            if (currentId.value == null) {
                val qs = repo.observeQuestions(setId.value!!).first()
                val next = qs.firstOrNull { !it.isAnswered }
                if (next == null) handle[KEY_FINISHED] = true else handle[KEY_CURRENT] = next.id
            }
        }
    }

    private val setFlow = setId.filterNotNull().flatMapLatest { repo.observeSet(it) }
    private val questionsFlow = setId.filterNotNull().flatMapLatest { repo.observeQuestions(it) }

    val state: StateFlow<DailyQuestionUiState> = combine(
        setFlow, questionsFlow, currentId, newDay, finished,
    ) { set, qs, cur, nd, fin ->
        DailyQuestionUiState(
            loading = set == null || (cur == null && !fin),
            set = set,
            questions = qs,
            current = qs.firstOrNull { it.id == cur },
            canAnswer = set != null && DayRules.canAnswer(set.localDate, openedOn),
            newDayReady = nd,
            finished = fin,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyQuestionUiState())

    fun answer(option: Int) {
        val s = state.value
        val q = s.current ?: return
        if (q.isAnswered || !s.canAnswer || answering) return
        answering = true
        viewModelScope.launch {
            try {
                repo.answerDaily(q.id, option)
            } finally {
                answering = false
            }
        }
    }

    fun next() {
        val s = state.value
        val set = s.set ?: return
        if (DayRules.isNewDayReady(set.localDate, container.time.today())) {
            handle[KEY_NEW_DAY] = true
            return
        }
        val cur = s.current
        val ordered = s.questions.sortedBy { it.position }
        val after = ordered.filter { !it.isAnswered && (cur == null || it.position > cur.position) }
        val next = after.firstOrNull() ?: ordered.firstOrNull { !it.isAnswered }
        if (next == null) handle[KEY_FINISHED] = true else handle[KEY_CURRENT] = next.id
    }
}
