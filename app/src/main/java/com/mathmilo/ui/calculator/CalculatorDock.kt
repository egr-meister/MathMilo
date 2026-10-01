package com.mathmilo.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathmilo.data.repository.HistoryEntry
import com.mathmilo.domain.Operator
import com.mathmilo.domain.calculator.CalculatorMessage
import com.mathmilo.domain.calculator.CalculatorState
import com.mathmilo.domain.calculator.Numbers
import com.mathmilo.ui.theme.ExpressionStyle
import com.mathmilo.ui.theme.IslandColors

fun CalculatorMessage.text(): String = when (this) {
    CalculatorMessage.DIVIDE_BY_ZERO -> "Oops! We can’t divide by zero. Try another number."
    CalculatorMessage.OUT_OF_RANGE -> "That’s too big for MathMilo. Results must stay between −1,000,000 and 1,000,000."
    CalculatorMessage.INPUT_LIMIT -> "Numbers can go up to 1,000,000 with up to 6 decimal places."
}

/** The dock: compact display, large keypad, history and "Turn result into practice". */
@Composable
fun CalculatorDock(
    state: CalculatorState,
    practiceHint: String?,
    onKey: (CalculatorKey) -> Unit,
    onPractice: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(IslandColors.WaterDeep.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Calculator Dock",
            style = MaterialTheme.typography.titleMedium,
            color = IslandColors.Navy,
            modifier = Modifier.semantics { heading() },
        )
        Display(state)
        Keypad(onKey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DockAction("History", onOpenHistory, Modifier.weight(1f), IslandColors.Card)
            if (state.result != null) {
                DockAction("Turn result into practice", onPractice, Modifier.weight(1.6f), IslandColors.Lavender)
            }
        }
        if (practiceHint != null) {
            Text(
                practiceHint,
                style = MaterialTheme.typography.bodyLarge,
                color = IslandColors.Navy,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IslandColors.Sand, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun Display(state: CalculatorState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(IslandColors.Card, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = state.spokenText
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            state.expressionText.ifEmpty { " " },
            style = ExpressionStyle.copy(fontSize = 18.sp),
            color = IslandColors.NavySoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val main = state.mainText
        Text(
            main,
            style = ExpressionStyle.copy(fontSize = if (main.length > 11) 30.sp else 40.sp),
            color = IslandColors.Navy,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        state.message?.let {
            Text(it.text(), style = MaterialTheme.typography.bodyMedium, color = IslandColors.Incorrect, textAlign = TextAlign.End)
        }
        if (state.result?.rounded == true) {
            Text("≈ means the result was rounded.", style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
        }
    }
}

private data class KeySpec(val label: String, val spoken: String, val key: CalculatorKey, val kind: Kind, val weight: Float = 1f)
private enum class Kind { Digit, Operator, Action, Equals }

private fun digit(d: Int) = KeySpec(d.toString(), d.toString(), CalculatorKey.Digit(d), Kind.Digit)
private fun op(o: Operator) = KeySpec(o.symbol, o.spokenName, CalculatorKey.Op(o), Kind.Operator)

private val rows = listOf(
    listOf(
        KeySpec("C", "Clear", CalculatorKey.Clear, Kind.Action),
        KeySpec("⌫", "Backspace", CalculatorKey.Backspace, Kind.Action),
        KeySpec("±", "Change sign", CalculatorKey.Sign, Kind.Action),
        op(Operator.DIVIDE),
    ),
    listOf(digit(7), digit(8), digit(9), op(Operator.MULTIPLY)),
    listOf(digit(4), digit(5), digit(6), op(Operator.SUBTRACT)),
    listOf(digit(1), digit(2), digit(3), op(Operator.ADD)),
    listOf(
        digit(0),
        KeySpec(".", "Decimal point", CalculatorKey.Decimal, Kind.Digit),
        KeySpec("=", "Equals", CalculatorKey.Equals, Kind.Equals, weight = 2f),
    ),
)

@Composable
private fun Keypad(onKey: (CalculatorKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { spec -> KeyButton(spec, onKey) }
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(spec: KeySpec, onKey: (CalculatorKey) -> Unit) {
    val (bg, fg) = when (spec.kind) {
        Kind.Digit -> IslandColors.Sand to IslandColors.Navy
        Kind.Operator -> IslandColors.Operator to IslandColors.Navy
        Kind.Action -> IslandColors.Card to IslandColors.Navy
        Kind.Equals -> IslandColors.Navy to Color.White
    }
    Button(
        onClick = { onKey(spec.key) },
        modifier = Modifier
            .weight(spec.weight)
            .heightIn(min = 60.dp)
            .semantics { contentDescription = spec.spoken },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
    ) {
        Text(spec.label, style = ExpressionStyle.copy(fontSize = 26.sp))
    }
}

@Composable
private fun DockAction(text: String, onClick: () -> Unit, modifier: Modifier, color: Color) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = IslandColors.Navy),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------------------------------
// History sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    onDismiss: () -> Unit,
    onUse: (HistoryEntry) -> Unit,
    onPractice: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = IslandColors.Sky) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Calculation history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
            if (entries.isEmpty()) {
                Text("No calculations yet.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(entries, key = { it.id }) { e ->
                        val r = e.result
                        val text = "${Numbers.canonical(r.first)} ${r.operator.symbol} ${Numbers.canonical(r.second)} = " +
                            (if (r.rounded) "≈ " else "") + r.valueText
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            TextButton(
                                onClick = { selectedId = if (selectedId == e.id) null else e.id },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(
                                    text.replace("-", "−"),
                                    style = ExpressionStyle.copy(fontSize = 20.sp),
                                    color = IslandColors.Navy,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            }
                            if (selectedId == e.id) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DockAction("Use result", { onUse(e) }, Modifier.weight(1f), IslandColors.Sand)
                                    if (r.practiceValue != null) {
                                        DockAction("Turn result into practice", { onPractice(e) }, Modifier.weight(1.6f), IslandColors.Lavender)
                                    }
                                }
                            }
                            HorizontalDivider(color = IslandColors.SkyDeep)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear calculation history?") },
            text = { Text("All saved calculations will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
