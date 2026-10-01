package com.mathmilo.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.data.repository.HistoryEntry
import com.mathmilo.domain.Operator
import com.mathmilo.domain.calculator.CalculationResult
import com.mathmilo.domain.calculator.CalculatorEngine
import com.mathmilo.domain.calculator.CalculatorOutput
import com.mathmilo.domain.calculator.CalculatorState
import com.mathmilo.domain.calculator.PracticeEligibility
import com.mathmilo.domain.generation.ResultPracticeGenerator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.UUID

sealed interface CalculatorKey {
    data class Digit(val d: Int) : CalculatorKey
    data object Decimal : CalculatorKey
    data class Op(val op: Operator) : CalculatorKey
    data object Equals : CalculatorKey
    data object Clear : CalculatorKey
    data object Backspace : CalculatorKey
    data object Sign : CalculatorKey
}

class CalculatorViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    /** Row id of the history entry for the result currently on screen (null if none). */
    private var currentResultId: Long? = null
    private val _practiceHint = MutableStateFlow<String?>(null)
    val practiceHint: StateFlow<String?> = _practiceHint.asStateFlow()

    private val _openPractice = Channel<Long>(Channel.BUFFERED)
    val openPractice = _openPractice.receiveAsFlow()

    val history: StateFlow<List<HistoryEntry>> = container.calculations.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutex = Mutex()
    private var restored = false
    private var practiceBusy = false

    init {
        viewModelScope.launch {
            _state.value = CalculatorEngine.decode(container.preferences.calculatorDraft())
            restored = true
        }
    }

    fun press(key: CalculatorKey) {
        viewModelScope.launch {
            mutex.withLock {
                if (!restored) return@withLock
                val s = _state.value
                val out: CalculatorOutput = when (key) {
                    is CalculatorKey.Digit -> CalculatorEngine.digit(s, key.d)
                    CalculatorKey.Decimal -> CalculatorEngine.decimal(s)
                    is CalculatorKey.Op -> CalculatorEngine.operator(s, key.op)
                    CalculatorKey.Equals -> CalculatorEngine.equals(s)
                    CalculatorKey.Clear -> CalculatorEngine.clear(s)
                    CalculatorKey.Backspace -> CalculatorEngine.backspace(s)
                    CalculatorKey.Sign -> CalculatorEngine.toggleSign(s)
                }
                _practiceHint.value = null
                val completed = out.completed
                if (completed != null) {
                    // Each completed calculation gets its own token; repeated taps cannot duplicate it.
                    currentResultId = container.calculations.record(completed, UUID.randomUUID().toString())
                }
                if (out.state.result == null) currentResultId = null
                _state.value = out.state
                container.preferences.saveCalculatorDraft(CalculatorEngine.encode(out.state))
            }
        }
    }

    fun useHistoryResult(entry: HistoryEntry) {
        viewModelScope.launch {
            mutex.withLock {
                val s = CalculatorEngine.useValue(entry.result.value)
                currentResultId = null
                _practiceHint.value = null
                _state.value = s
                container.preferences.saveCalculatorDraft(CalculatorEngine.encode(s))
            }
        }
    }

    /** "Turn result into practice" for the result currently displayed. */
    fun practiceCurrentResult() {
        val r = _state.value.result ?: return
        startPractice(r, currentResultId)
    }

    fun practiceFromHistory(entry: HistoryEntry) = startPractice(entry.result, entry.id)

    fun isEligible(result: CalculationResult): Boolean = result.practiceValue != null

    private fun startPractice(result: CalculationResult, calcId: Long?) {
        val value = result.practiceValue
        if (value == null) {
            _practiceHint.value = PracticeEligibility.UNSUPPORTED_MESSAGE
            return
        }
        if (practiceBusy) return
        practiceBusy = true
        viewModelScope.launch {
            try {
                val source = ResultPracticeGenerator.Source(
                    first = exactInt(result.first),
                    operator = result.operator,
                    second = exactInt(result.second),
                )
                val id = container.practice.createResultPractice(value, source, calcId)
                _openPractice.send(id)
            } finally {
                practiceBusy = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { container.calculations.clear() }
    }

    private fun exactInt(v: BigDecimal): Int? =
        runCatching { v.stripTrailingZeros().intValueExact() }.getOrNull()
}
