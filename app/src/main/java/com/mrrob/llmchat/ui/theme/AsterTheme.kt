package com.mrrob.llmchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.R

/**
 * The Aster design system, extracted from the HTML design exports:
 * warm neutral canvas, white cards with hairline borders, indigo accent.
 */
object AsterColors {
    // Light palette
    val Bg = Color(0xFFF6F6F4)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFFE9E9E5)
    val TextPrimary = Color(0xFF1D1D1F)
    val TextSecondary = Color(0xFF78787F)
    val TextMuted = Color(0xFFADADB4)
    val Accent = Color(0xFF5B5BD6)
    val AccentDeep = Color(0xFF6E6EE6)
    val AccentTint = Color(0xFFECECFB)
    val Fill = Color(0xFFF0F0ED)
    val Success = Color(0xFF2E9E5B)
    val SuccessTint = Color(0xFFE7F4EC)
    val Warning = Color(0xFFB97F24)
    val WarningTint = Color(0xFFF9F1E1)
    val Danger = Color(0xFFD64545)
    val DangerTint = Color(0xFFFBECEC)
    val CodeBg = Color(0xFF232329)
    val CodeFg = Color(0xFFD6D6DC)
    val CodePurple = Color(0xFFC678DD)
    val CodeGreen = Color(0xFF98C379)
    val CodeRed = Color(0xFFE06C75)
    val CodeOrange = Color(0xFFD19A66)
    val CodeComment = Color(0xFF7F848E)
    val Shadow7 = Color(0x12000000)
    val Shadow12 = Color(0x1F000000)
    val Shadow15 = Color(0x26000000)

    // Dark counterparts
    val BgDark = Color(0xFF101012)
    val CardDark = Color(0xFF1B1B1E)
    val BorderDark = Color(0xFF2C2C30)
    val TextPrimaryDark = Color(0xFFF2F2EF)
    val TextSecondaryDark = Color(0xFFA5A5AC)
    val TextMutedDark = Color(0xFF6F6F77)
    val AccentTintDark = Color(0xFF272740)
    val FillDark = Color(0xFF232327)
    val SuccessTintDark = Color(0xFF16301F)
    val WarningTintDark = Color(0xFF32260F)
    val DangerTintDark = Color(0xFF351818)
}

@Immutable
data class AsterScheme(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentTint: Color,
    val fill: Color,
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val codeBg: Color,
    val codeFg: Color
)

val LightScheme = AsterScheme(
    dark = false,
    bg = AsterColors.Bg,
    card = AsterColors.Card,
    border = AsterColors.Border,
    textPrimary = AsterColors.TextPrimary,
    textSecondary = AsterColors.TextSecondary,
    textMuted = AsterColors.TextMuted,
    accent = AsterColors.Accent,
    accentDeep = AsterColors.AccentDeep,
    accentTint = AsterColors.AccentTint,
    fill = AsterColors.Fill,
    success = AsterColors.Success,
    successTint = AsterColors.SuccessTint,
    warning = AsterColors.Warning,
    warningTint = AsterColors.WarningTint,
    danger = AsterColors.Danger,
    dangerTint = AsterColors.DangerTint,
    codeBg = AsterColors.CodeBg,
    codeFg = AsterColors.CodeFg
)

val DarkScheme = AsterScheme(
    dark = true,
    bg = AsterColors.BgDark,
    card = AsterColors.CardDark,
    border = AsterColors.BorderDark,
    textPrimary = AsterColors.TextPrimaryDark,
    textSecondary = AsterColors.TextSecondaryDark,
    textMuted = AsterColors.TextMutedDark,
    accent = Color(0xFF7B7BE8),
    accentDeep = Color(0xFF8D8DED),
    accentTint = AsterColors.AccentTintDark,
    fill = AsterColors.FillDark,
    success = Color(0xFF41B370),
    successTint = AsterColors.SuccessTintDark,
    warning = Color(0xFFD9A044),
    warningTint = AsterColors.WarningTintDark,
    danger = Color(0xFFF0605D),
    dangerTint = AsterColors.DangerTintDark,
    codeBg = Color(0xFF0D0D10),
    codeFg = AsterColors.CodeFg
)

val LocalScheme = staticCompositionLocalOf { LightScheme }

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_regular, FontWeight.Normal),
    Font(R.font.jetbrains_medium, FontWeight.Medium)
)

/** The type scale from the design (sp ≈ px). [scale] shifts it for font-size settings. */
@Immutable
data class AsterType(
    val largeTitle: TextStyle,
    val pageTitle: TextStyle,
    val heroTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val bodyTight: TextStyle,
    val desc: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val tiny: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle
)

fun buildType(scale: Float): AsterType {
    fun s(v: Float) = (v * scale).sp
    fun t(size: Float, weight: FontWeight, line: Float? = null, tracking: Float = 0f, family: FontFamily = InterFamily) =
        TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = s(size),
            lineHeight = if (line != null) s(line) else TextStyle.Unspecified.lineHeight,
            letterSpacing = s(tracking)
        )
    return AsterType(
        largeTitle = t(28f, FontWeight.Bold, 34f, -0.5f),
        pageTitle = t(25f, FontWeight.Bold, 31f, -0.4f),
        heroTitle = t(23f, FontWeight.Bold, 28f, -0.3f),
        sectionTitle = t(17f, FontWeight.Bold, 21f),
        cardTitle = t(16f, FontWeight.Bold, 20f),
        rowTitle = t(15f, FontWeight.Medium, 20f),
        body = t(14.5f, FontWeight.Normal, 21f),
        bodyTight = t(14f, FontWeight.Normal, 20f),
        desc = t(13f, FontWeight.Normal, 18f),
        caption = t(12f, FontWeight.Normal, 16f),
        micro = t(11f, FontWeight.Normal, 15f),
        tiny = t(10f, FontWeight.Normal, 13f),
        button = t(16f, FontWeight.SemiBold, 20f),
        mono = t(11f, FontWeight.Normal, 17f, family = MonoFamily),
        monoSmall = t(10.5f, FontWeight.Medium, 16f, family = MonoFamily)
    )
}

@Stable
data class AsterDensity(
    val listRowV: Dp,
    val messageGap: Dp,
    val bubbleH: Dp,
    val bubbleV: Dp,
    val cardPad: Dp,
    val sectionGap: Dp,
    val composerV: Dp
)

val DensityComfortable = AsterDensity(
    listRowV = 10.dp,
    messageGap = 18.dp,
    bubbleH = 16.dp,
    bubbleV = 12.dp,
    cardPad = 18.dp,
    sectionGap = 14.dp,
    composerV = 10.dp
)

val DensityCompact = AsterDensity(
    listRowV = 7.dp,
    messageGap = 12.dp,
    bubbleH = 14.dp,
    bubbleV = 9.dp,
    cardPad = 14.dp,
    sectionGap = 10.dp,
    composerV = 7.dp
)

val LocalType = staticCompositionLocalOf { buildType(1f) }
val LocalDensity = compositionLocalOf { DensityComfortable }

/** Convenience accessor: `val c = AsterTheme.scheme()`. */
@Composable
fun asterScheme(): AsterScheme = LocalScheme.current

@Composable
fun asterType(): AsterType = LocalType.current

@Composable
fun asterDensity(): AsterDensity = LocalDensity.current

@Composable
fun AsterTheme(
    themeMode: String, // "system" | "light" | "dark"
    fontScaleValue: String, // "small" | "medium" | "large"
    compactDensity: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scale = when (fontScaleValue) {
        "small" -> 0.92f
        "large" -> 1.1f
        else -> 1f
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalScheme provides if (dark) DarkScheme else LightScheme,
        LocalType provides buildType(scale),
        LocalDensity provides if (compactDensity) DensityCompact else DensityComfortable
    ) {
        content()
    }
}
