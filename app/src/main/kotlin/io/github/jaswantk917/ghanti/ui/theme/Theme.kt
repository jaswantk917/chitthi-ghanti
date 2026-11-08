package io.github.jaswantk917.ghanti.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// the old Google Messages blue (0xFF1A73E8) with Material's Fidelity scheme, 2025 spec; tertiary swapped to Google yellow tones since Fidelity's burnt orange shouted
private val messagesLight = lightColorScheme(
    primary = Color(0xFF005BBF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A73E8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFADC7FF),
    secondary = Color(0xFF475E8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2C9FE),
    onSecondaryContainer = Color(0xFF3D5481),
    tertiary = Color(0xFF795900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDFA0),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C23),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C23),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF414754),
    outline = Color(0xFF727785),
    outlineVariant = Color(0xFFC1C6D6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3038),
    inverseOnSurface = Color(0xFFEFF0FA),
    surfaceDim = Color(0xFFD8D9E3),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FD),
    surfaceContainer = Color(0xFFECEDF7),
    surfaceContainerHigh = Color(0xFFE6E8F2),
    surfaceContainerHighest = Color(0xFFE0E2EC),
    surfaceTint = Color(0xFF005BC0),
)

private val messagesDark = darkColorScheme(
    primary = Color(0xFFADC7FF),
    onPrimary = Color(0xFF002E68),
    primaryContainer = Color(0xFF1A73E8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF005BC0),
    secondary = Color(0xFFAFC7FB),
    onSecondary = Color(0xFF15305B),
    secondaryContainer = Color(0xFF2E4673),
    onSecondaryContainer = Color(0xFF9EB5E8),
    tertiary = Color(0xFFEDC061),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDFA0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE0E2EC),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE0E2EC),
    surfaceVariant = Color(0xFF414754),
    onSurfaceVariant = Color(0xFFC1C6D6),
    outline = Color(0xFF8B909F),
    outlineVariant = Color(0xFF414754),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE0E2EC),
    inverseOnSurface = Color(0xFF2D3038),
    surfaceDim = Color(0xFF10131A),
    surfaceBright = Color(0xFF363941),
    surfaceContainerLowest = Color(0xFF0B0E15),
    surfaceContainerLow = Color(0xFF191C23),
    surfaceContainer = Color(0xFF1D2027),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
    surfaceTint = Color(0xFFADC7FF),
)

// Expressive leans on big, soft corners; the "increased" steps are what cards and sheets use
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(36.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

enum class Palette { Ghanti, Wallpaper }

private fun ColorScheme.toAmoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF1A1A1E),
    surfaceContainerHighest = Color(0xFF232328),
)

@Composable
fun GhantiTheme(palette: Palette = Palette.Ghanti, amoled: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val base = when {
        palette == Palette.Ghanti || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (dark) messagesDark else messagesLight
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    // pure black only means something in the dark
    val colorScheme = if (dark && amoled) base.toAmoled() else base
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
    ) {
        // screens draw on plain backgrounds, not Surfaces, so bare Text would otherwise fall back to black
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface, content = content)
    }
}
