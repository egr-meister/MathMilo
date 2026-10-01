package com.mathmilo.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mathmilo.ui.common.Panel
import com.mathmilo.ui.common.ScreenScaffold

private val sections = listOf(
    "Everything stays on this device" to
        "Calculations, daily questions, answers, mistakes and progress are stored only in MathMilo’s private storage on this device.",
    "No internet" to
        "MathMilo does not request internet access. It has no accounts, ads, analytics, cloud sync, payments or external links.",
    "No personal details" to
        "MathMilo never asks for a name, age, email or any other personal identifier. It uses no camera, microphone, location or contacts.",
    "No backups or transfers" to
        "App data is excluded from cloud backup and device-to-device transfer, so it is not copied off this device.",
    "Removing data" to
        "A grown-up can clear calculation history, practice history or all local data in Settings. Uninstalling MathMilo also removes all of its data.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Privacy", onBack = onBack, backLabel = "Back to settings") {
        sections.forEach { (title, body) ->
            Panel {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
