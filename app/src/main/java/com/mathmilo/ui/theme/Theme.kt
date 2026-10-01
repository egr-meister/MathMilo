package com.mathmilo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Calm adventure palette. All text colours meet WCAG AA against their backgrounds. */
object IslandColors {
    val Sky = Color(0xFFE6F2FA)
    val SkyDeep = Color(0xFFD3E7F4)
    val Mint = Color(0xFFCDEBD9)
    val MintDeep = Color(0xFF9FD3B5)
    val Water = Color(0xFF9DBFD6)
    val WaterDeep = Color(0xFF7EA8C4)
    val Sand = Color(0xFFF2DFB8)
    val SandDeep = Color(0xFFE3C88F)
    val Lavender = Color(0xFFD9D0F2)
    val LavenderDeep = Color(0xFF8E7CC3)
    val Navy = Color(0xFF1C2742)
    val NavySoft = Color(0xFF3B4A6B)
    val Card = Color(0xFFFCFDFE)
    val Trunk = Color(0xFF8A6B4A)
    val Plank = Color(0xFFD9B88A)
    val CorrectBg = Color(0xFFD5F0DE)
    val Correct = Color(0xFF1D6B3F)
    val IncorrectBg = Color(0xFFFBE0D9)
    val Incorrect = Color(0xFF9C2F17)
    val Operator = Color(0xFFC9DCEB)
}

data class MotionPrefs(val reduced: Boolean)

val LocalMotionPrefs = compositionLocalOf { MotionPrefs(reduced = false) }

private val scheme = lightColorScheme(
    primary = IslandColors.Navy,
    onPrimary = Color.White,
    primaryContainer = IslandColors.Lavender,
    onPrimaryContainer = IslandColors.Navy,
    secondary = IslandColors.NavySoft,
    onSecondary = Color.White,
    secondaryContainer = IslandColors.Mint,
    onSecondaryContainer = IslandColors.Navy,
    tertiaryContainer = IslandColors.Sand,
    onTertiaryContainer = IslandColors.Navy,
    background = IslandColors.Sky,
    onBackground = IslandColors.Navy,
    surface = IslandColors.Card,
    onSurface = IslandColors.Navy,
    surfaceVariant = IslandColors.SkyDeep,
    onSurfaceVariant = IslandColors.NavySoft,
    error = IslandColors.Incorrect,
    onError = Color.White,
    outline = IslandColors.NavySoft,
)

private val base = Typography()

private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, color = IslandColors.Navy),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

/** Monospaced-digit style for expressions so numbers do not jump while typing. */
val ExpressionStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

@Composable
fun MathMiloTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalMotionPrefs provides MotionPrefs(reducedMotion),
            content = content,
        )
    }
}
