package com.mathmilo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathmilo.data.repository.AppContainer
import com.mathmilo.data.repository.UserPreferences
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold
import com.mathmilo.ui.common.appContainer
import com.mathmilo.ui.common.containerFactory
import com.mathmilo.ui.question.SecondaryAction
import com.mathmilo.ui.theme.IslandColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val prefs: StateFlow<UserPreferences> = container.preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setLevel(level: PracticeLevel) { viewModelScope.launch { container.preferences.setLevel(level) } }
    fun setReducedMotion(on: Boolean) { viewModelScope.launch { container.preferences.setReducedMotion(on) } }
    fun clearHistory() { viewModelScope.launch { container.calculations.clear() } }
    fun clearPractice() { viewModelScope.launch { container.clearPracticeData() } }
    fun clearAll() { viewModelScope.launch { container.clearAllData() } }
}

private enum class Destructive(val title: String, val body: String) {
    HISTORY("Clear calculation history?", "All saved calculations will be removed."),
    PRACTICE("Clear practice history and mistakes?", "Daily results, extra practice, mistakes and totals will be removed. A fresh set is made for today."),
    ALL("Clear all local data?", "Everything is removed and the level returns to Starter. A fresh set is made for today."),
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(factory = containerFactory(container) { SettingsViewModel(it) })
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<Destructive?>(null) }
    var done by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Settings", onBack = onBack, backLabel = "Back to island") {
        Panel {
            Text("Practice level", style = MaterialTheme.typography.titleMedium)
            Text(
                "A new level is used from the next daily set. Today’s set stays as it is.",
                style = MaterialTheme.typography.bodyMedium,
                color = IslandColors.NavySoft,
            )
            Column(Modifier.selectableGroup()) {
                PracticeLevel.entries.forEach { level ->
                    val selected = prefs.level == level
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, onClick = { vm.setLevel(level) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(level.title, style = MaterialTheme.typography.bodyLarge)
                            Text(levelDescription(level), style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
                        }
                    }
                }
            }
        }
        Panel {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = prefs.reducedMotion, onValueChange = vm::setReducedMotion, role = Role.Switch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reduce decorative animation", style = MaterialTheme.typography.bodyLarge)
                    Text("Bridge planks change instantly.", style = MaterialTheme.typography.bodyMedium, color = IslandColors.NavySoft)
                }
                Switch(checked = prefs.reducedMotion, onCheckedChange = null)
            }
        }
        Panel {
            Text("Grown-up area", style = MaterialTheme.typography.titleMedium)
            SecondaryAction("Clear calculation history", { pending = Destructive.HISTORY })
            SecondaryAction("Clear practice history and mistakes", { pending = Destructive.PRACTICE })
            SecondaryAction("Clear all local data", { pending = Destructive.ALL })
        }
        SecondaryAction("Privacy", onOpenPrivacy)
        done?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = it }) }
    }

    pending?.let { action ->
        AdultConfirmDialog(
            title = action.title,
            body = action.body,
            onConfirm = {
                pending = null
                when (action) {
                    Destructive.HISTORY -> vm.clearHistory()
                    Destructive.PRACTICE -> vm.clearPractice()
                    Destructive.ALL -> vm.clearAll()
                }
                done = "Done. The data was cleared."
            },
            onDismiss = { pending = null },
        )
    }
}

private fun levelDescription(l: PracticeLevel) =
    "Add & subtract ${l.addSubRange.first}–${l.addSubRange.last}, " +
        "multiply ${l.mulRange.first}–${l.mulRange.last}, divide up to ${l.maxDividend}"

/**
 * Accidental-entry safeguard for grown-ups (not secure authentication): press and hold for two
 * seconds, or — as an accessible alternative — type the word RESET.
 */
@Composable
fun AdultConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val holdMillis = 2000L

    LaunchedEffect(typed) {
        if (typed.trim().equals("RESET", ignoreCase = true)) onConfirm()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                Text("Grown-ups: press and hold the button below for 2 seconds.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Hold to confirm",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(IslandColors.Incorrect, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val job = scope.launch {
                                    val start = System.currentTimeMillis()
                                    while (true) {
                                        progress = ((System.currentTimeMillis() - start) / holdMillis.toFloat()).coerceAtMost(1f)
                                        kotlinx.coroutines.delay(16)
                                    }
                                }
                                val released = withTimeoutOrNull(holdMillis) { waitForUpOrCancellation() }
                                job.cancel()
                                progress = 0f
                                if (released == null) onConfirm()
                            }
                        }
                        .padding(16.dp),
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Or type RESET to confirm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
