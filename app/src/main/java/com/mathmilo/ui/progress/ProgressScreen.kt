package com.mathmilo.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.domain.progress.Accuracy
import com.mathmilo.domain.progress.DailySummary
import com.mathmilo.domain.progress.ProgressTotals
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.StatRow
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.results.formatDate
import com.mathmilo.ui.theme.IslandColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ProgressUiState(
    val today: LocalDate? = null,
    val totals: ProgressTotals = ProgressTotals(),
    val recent: List<DailySummary> = emptyList(),
) {
    val todaySummary: DailySummary? get() = recent.firstOrNull { it.set.localDate == today }
}

class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val today = container.time.today()
    val state: StateFlow<ProgressUiState> = combine(
        container.practice.observeTotals(),
        container.practice.observeRecentSummaries(),
    ) { totals, recent -> ProgressUiState(today, totals, recent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState(today))
}

@Composable
fun ProgressScreen(onBack: () -> Unit, onOpenResult: (Long) -> Unit) {
    val container = appContainer()
    val vm: ProgressViewModel = viewModel(factory = containerFactory(container) { ProgressViewModel(it) })
    val s by vm.state.collectAsStateWithLifecycle()
    val t = s.totals

    ScreenScaffold(title = "Progress Stars", onBack = onBack, backLabel = "Back to island") {
        Panel(color = IslandColors.Lavender) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            val today = s.todaySummary
            if (today == null || today.answered == 0) {
                Text(Accuracy.NO_ANSWERS, style = MaterialTheme.typography.bodyLarge)
            } else {
                StatRow("Answered", "${today.answered} of ${today.total}")
                StatRow("Correct", "${today.correct}")
                StatRow("Accuracy", today.accuracyText)
            }
        }
        Panel {
            Text("All time", style = MaterialTheme.typography.titleMedium)
            StatRow("Days completed", "${t.completedDays}")
            StatRow("Daily questions answered", "${t.dailyAnswered}")
            StatRow("Daily accuracy", Accuracy.text(t.dailyCorrect, t.dailyAnswered))
            StatRow("Extra practice answered", "${t.extraAnswered}")
            StatRow("Mistakes resolved", "${t.resolvedMistakeCount}")
        }
        Text("Recent days", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every completed day earns the same star.",
            style = MaterialTheme.typography.bodyMedium,
            color = IslandColors.NavySoft,
        )
        Panel {
            if (s.recent.isEmpty()) {
                Text("No days yet.", style = MaterialTheme.typography.bodyLarge)
            }
            s.recent.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = IslandColors.SkyDeep)
                DayRow(d, onClick = { onOpenResult(d.set.id) })
            }
        }
    }
}

@Composable
private fun DayRow(d: DailySummary, onClick: () -> Unit) {
    val status = when {
        d.isComplete -> "★ Completed · ${d.correct} of ${d.total}"
        d.answered == 0 -> "Not started"
        else -> "Incomplete · ${d.answered} of ${d.total} answered"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "Open daily result", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${formatDate(d.set.localDate)}. $status" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallStar(filled = d.isComplete)
        Column(Modifier.weight(1f)) {
            Text(formatDate(d.set.localDate), style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
        }
    }
}

@Composable
private fun SmallStar(filled: Boolean) {
    Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        val path = Path()
        for (k in 0 until 10) {
            val radius = if (k % 2 == 0) r else r * 0.45f
            val a = Math.PI / 5 * k - Math.PI / 2
            val x = c.x + (radius * Math.cos(a)).toFloat()
            val y = c.y + (radius * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (filled) drawPath(path, IslandColors.Navy) else drawPath(path, IslandColors.LavenderDeep, style = Stroke(2.dp.toPx()))
    }
}
