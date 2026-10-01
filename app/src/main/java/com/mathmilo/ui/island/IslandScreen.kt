package com.mathmilo.ui.island

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.ui.calculator.CalculatorDock
import com.mathmilo.ui.calculator.CalculatorViewModel
import com.mathmilo.ui.calculator.HistorySheet
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.theme.IslandColors
import com.mathmilo.ui.theme.LocalMotionPrefs
import kotlinx.coroutines.delay

@Composable
fun IslandScreen(
    onOpenDaily: () -> Unit,
    onOpenResult: (setId: Long) -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExtraPractice: (practiceId: Long) -> Unit,
) {
    val container = appContainer()
    val islandVm: IslandViewModel = viewModel(factory = containerFactory(container) { IslandViewModel(it) })
    val calcVm: CalculatorViewModel = viewModel(factory = containerFactory(container) { CalculatorViewModel(it) })
    val island by islandVm.state.collectAsStateWithLifecycle()
    val calc by calcVm.state.collectAsStateWithLifecycle()
    val hint by calcVm.practiceHint.collectAsStateWithLifecycle()
    val history by calcVm.history.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Check the local date whenever the island is visible, and once a minute while it stays open.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                islandVm.refreshDay()
                delay(60_000)
            }
        }
    }
    LaunchedEffect(calcVm) {
        calcVm.openPractice.collect { id ->
            showHistory = false
            onOpenExtraPractice(id)
        }
    }

    val openBridge = {
        val set = island.set
        if (set != null && island.allAnswered) onOpenResult(set.id) else onOpenDaily()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(IslandColors.Sky),
    ) {
        IslandBackdrop(Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth >= 600.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(onOpenSettings)
                val dock: @Composable (Modifier) -> Unit = { m ->
                    CalculatorDock(
                        state = calc,
                        practiceHint = hint,
                        onKey = calcVm::press,
                        onPractice = calcVm::practiceCurrentResult,
                        onOpenHistory = { showHistory = true },
                        modifier = m,
                    )
                }
                val landmarks: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BridgeLandmark(island, openBridge)
                        MistakeSign(island.activeMistakes, onOpenMistakes)
                        ProgressStars(island.completedDays, onOpenProgress)
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        dock(Modifier.weight(1f).widthIn(max = 520.dp))
                        landmarks(Modifier.weight(1f))
                    }
                } else {
                    dock(Modifier)
                    landmarks(Modifier)
                }
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            entries = history,
            onDismiss = { showHistory = false },
            onUse = { calcVm.useHistoryResult(it); showHistory = false },
            onPractice = { calcVm.practiceFromHistory(it) },
            onClear = calcVm::clearHistory,
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "MathMilo Island",
            style = MaterialTheme.typography.titleLarge,
            color = IslandColors.Navy,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = IslandColors.Navy)
        }
    }
}

/** Soft illustrated island: sky, water band, mint island shapes, geometric trees. Static. */
@Composable
private fun IslandBackdrop(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        drawRect(IslandColors.Water.copy(alpha = 0.55f), topLeft = Offset(0f, h * 0.35f), size = Size(w, h * 0.65f))
        drawOval(IslandColors.Mint, topLeft = Offset(-w * 0.15f, h * 0.30f), size = Size(w * 1.3f, h * 0.62f))
        drawOval(IslandColors.MintDeep.copy(alpha = 0.5f), topLeft = Offset(w * 0.05f, h * 0.86f), size = Size(w * 0.9f, h * 0.08f))
        fun tree(x: Float, y: Float, s: Float) {
            drawRect(IslandColors.Trunk, topLeft = Offset(x - s * 0.08f, y), size = Size(s * 0.16f, s * 0.35f))
            val p = Path().apply {
                moveTo(x, y - s * 0.9f)
                lineTo(x - s * 0.45f, y + s * 0.05f)
                lineTo(x + s * 0.45f, y + s * 0.05f)
                close()
            }
            drawPath(p, IslandColors.MintDeep)
        }
        val s = 28.dp.toPx()
        tree(w * 0.06f, h * 0.40f, s)
        tree(w * 0.94f, h * 0.48f, s * 1.1f)
        tree(w * 0.10f, h * 0.80f, s * 0.9f)
        tree(w * 0.90f, h * 0.84f, s)
    }
}

@Composable
private fun BridgeLandmark(state: IslandUiState, onOpen: () -> Unit) {
    val total = state.questions.size.takeIf { it > 0 } ?: 10
    val summary = "Today: ${state.answered} of $total answered"
    val action = when {
        state.allAnswered -> "See today’s result"
        state.answered == 0 -> "Start today’s questions"
        else -> "Continue today’s questions"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(IslandColors.Card.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "Daily Challenge Bridge. $summary. $action."
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Daily Challenge Bridge", style = MaterialTheme.typography.titleMedium, color = IslandColors.Navy)
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = IslandColors.Navy)
        Box(
            Modifier
                .fillMaxWidth()
                .background(IslandColors.Water.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val records: List<DailyQuestionRecord?> =
                    if (state.questions.isEmpty()) List(10) { null } else state.questions.sortedBy { it.position }
                records.chunked(5).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, q -> Plank(rowIndex * 5 + i + 1, q, Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text("✓ correct   ✗ to review   ○ not yet", style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(IslandColors.Navy, RoundedCornerShape(16.dp))
                .padding(14.dp),
        )
    }
}

@Composable
private fun Plank(number: Int, q: DailyQuestionRecord?, modifier: Modifier) {
    val reduced = LocalMotionPrefs.current.reduced
    val (target, symbol) = when (q?.isCorrect) {
        true -> IslandColors.CorrectBg to "✓"
        false -> IslandColors.IncorrectBg to "✗"
        null -> IslandColors.Plank to "○"
    }
    val color by animateColorAsState(target, animationSpec = if (reduced) snap() else tween(300), label = "plank")
    Column(
        modifier
            .heightIn(min = 52.dp)
            .background(color, RoundedCornerShape(8.dp))
            .border(1.dp, IslandColors.Trunk.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$number", style = MaterialTheme.typography.bodyMedium, color = IslandColors.Navy)
        Text(symbol, style = MaterialTheme.typography.bodyLarge, color = IslandColors.Navy)
    }
}

@Composable
private fun MistakeSign(count: Int, onOpen: () -> Unit) {
    val text = if (count == 0) "Nothing to practice right now" else "$count to try again"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(IslandColors.Sand, RoundedCornerShape(16.dp))
            .border(2.dp, IslandColors.Trunk, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Open Mistake Practice", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "Mistake Practice sign. $text." }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Mistake Practice", style = MaterialTheme.typography.titleMedium, color = IslandColors.Navy, modifier = Modifier.weight(1f))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = IslandColors.Navy)
    }
}

@Composable
private fun ProgressStars(completedDays: Int, onOpen: () -> Unit) {
    val text = when (completedDays) {
        0 -> "Complete a day to light your first star"
        1 -> "1 day completed"
        else -> "$completedDays days completed"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(IslandColors.Lavender, RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Open progress", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "Progress Stars. $text." }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Progress Stars", style = MaterialTheme.typography.titleMedium, color = IslandColors.Navy)
        Constellation(minOf(completedDays, 7), Modifier.fillMaxWidth().height(48.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = IslandColors.Navy)
    }
}

/** A small, static constellation: lit stars for completed days (max 7 drawn), outlines otherwise. */
@Composable
fun Constellation(lit: Int, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val n = 7
        val pts = (0 until n).map { i ->
            val x = size.width * (0.08f + 0.84f * i / (n - 1))
            val y = size.height * (if (i % 2 == 0) 0.62f else 0.32f)
            Offset(x, y)
        }
        for (i in 0 until n - 1) {
            drawLine(IslandColors.LavenderDeep.copy(alpha = 0.5f), pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
        }
        pts.forEachIndexed { i, p -> drawStar(p, 9.dp.toPx(), filled = i < lit) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(c: Offset, r: Float, filled: Boolean) {
    val path = Path()
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) r else r * 0.45f
        val angle = Math.PI / 5 * k - Math.PI / 2
        val x = c.x + (radius * Math.cos(angle)).toFloat()
        val y = c.y + (radius * Math.sin(angle)).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (filled) {
        drawPath(path, IslandColors.Navy)
    } else {
        drawPath(path, IslandColors.LavenderDeep, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
    }
}
