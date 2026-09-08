package com.mrrob.llmchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Apple system palette
val IosBlue = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)
val IosGrayBg = Color(0xFFF2F2F7)
val IosGrayBgDark = Color(0xFF000000)
val IosLabel = Color(0xFF1C1C1E)
val IosLabelDark = Color(0xFFFFFFFF)
val IosSecondaryLabel = Color(0xFF6C6C70)
val IosSecondaryLabelDark = Color(0xFFA1A1A8)
val IosSeparator = Color(0x1F000000)
val IosSeparatorDark = Color(0x33FFFFFF)
val IosFill = Color(0xFFE9E9EB)
val IosFillDark = Color(0xFF1C1C1E)
val IosGreen = Color(0xFF34C759)
val IosRed = Color(0xFFFF3B30)

private val LightColors = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    background = IosGrayBg,
    onBackground = IosLabel,
    surface = Color.White,
    onSurface = IosLabel,
    surfaceVariant = IosFill,
    onSurfaceVariant = IosSecondaryLabel,
    secondary = IosBlue,
    onSecondary = Color.White,
    outline = IosSeparator,
    error = IosRed
)

private val DarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    background = IosGrayBgDark,
    onBackground = IosLabelDark,
    surface = Color(0xFF1C1C1E),
    onSurface = IosLabelDark,
    surfaceVariant = IosFillDark,
    onSurfaceVariant = IosSecondaryLabelDark,
    secondary = IosBlueDark,
    onSecondary = Color.White,
    outline = IosSeparatorDark,
    error = Color(0xFFFF453A)
)

/**
 * App theme in the Apple iOS style: a light, airy palette built around
 * system gray backgrounds, white cards and the iOS system blue accent.
 * On Android 12+ the accent follows the user's wallpaper colors.
 */
@Composable
fun LlmChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LlmTypography,
        content = content
    )
}
