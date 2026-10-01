package com.mathmilo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mathmilo.ui.island.IslandScreen
import com.mathmilo.ui.mistakes.MistakeListScreen
import com.mathmilo.ui.mistakes.MistakeRetryScreen
import com.mathmilo.ui.progress.ProgressScreen
import com.mathmilo.ui.question.DailyQuestionScreen
import com.mathmilo.ui.question.DailyQuestionViewModel
import com.mathmilo.ui.question.ExtraPracticeScreen
import com.mathmilo.ui.results.ResultScreen
import com.mathmilo.ui.settings.PrivacyScreen
import com.mathmilo.ui.settings.SettingsScreen

object Routes {
    const val ISLAND = "island"
    const val QUESTION = "question/{setId}"
    const val RESULT = "result/{setId}"
    const val EXTRA = "extra/{practiceId}"
    const val MISTAKES = "mistakes"
    const val RETRY = "retry/{mistakeId}"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun question(setId: Long = DailyQuestionViewModel.TODAY) = "question/$setId"
    fun result(setId: Long) = "result/$setId"
    fun extra(id: Long) = "extra/$id"
    fun retry(id: Long) = "retry/$id"
}

private fun NavHostController.backToIsland() {
    if (!popBackStack(Routes.ISLAND, inclusive = false)) navigate(Routes.ISLAND)
}

/**
 * Back behaviour (predictive Back compatible, handled by Navigation Compose):
 * question → island (progress is already persisted), retry → Mistake Practice, island → exit.
 */
@Composable
fun MathMiloNavHost(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.ISLAND) {
        composable(Routes.ISLAND) {
            IslandScreen(
                onOpenDaily = { nav.navigate(Routes.question()) { launchSingleTop = true } },
                onOpenResult = { nav.navigate(Routes.result(it)) { launchSingleTop = true } },
                onOpenMistakes = { nav.navigate(Routes.MISTAKES) { launchSingleTop = true } },
                onOpenProgress = { nav.navigate(Routes.PROGRESS) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenExtraPractice = { nav.navigate(Routes.extra(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.QUESTION, arguments = listOf(navArgument("setId") { type = NavType.LongType })) {
            DailyQuestionScreen(
                onBack = { nav.backToIsland() },
                onFinished = { setId ->
                    nav.navigate(Routes.result(setId)) { popUpTo(Routes.ISLAND) }
                },
                onStartNewDay = {
                    nav.navigate(Routes.question()) { popUpTo(Routes.ISLAND) }
                },
            )
        }
        composable(Routes.RESULT, arguments = listOf(navArgument("setId") { type = NavType.LongType })) {
            ResultScreen(
                onBackToIsland = { nav.backToIsland() },
                onPracticeMistakes = { nav.navigate(Routes.MISTAKES) { popUpTo(Routes.ISLAND) } },
            )
        }
        composable(Routes.EXTRA, arguments = listOf(navArgument("practiceId") { type = NavType.LongType })) {
            ExtraPracticeScreen(onBack = { nav.backToIsland() })
        }
        composable(Routes.MISTAKES) {
            MistakeListScreen(
                onBack = { nav.backToIsland() },
                onRetry = { nav.navigate(Routes.retry(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.RETRY, arguments = listOf(navArgument("mistakeId") { type = NavType.LongType })) {
            MistakeRetryScreen(onBack = {
                if (!nav.popBackStack(Routes.MISTAKES, inclusive = false)) {
                    nav.navigate(Routes.MISTAKES) { popUpTo(Routes.ISLAND) }
                }
            })
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(onBack = { nav.backToIsland() }, onOpenResult = { nav.navigate(Routes.result(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() })
        }
    }
}
