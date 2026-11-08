package io.github.jaswantk917.ghanti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import io.github.jaswantk917.chitthi.R

private val FlexWeights = listOf(400, 450, 500, 600, 650, 700, 750, 800, 850, 900)

// Google Sans Flex is a single variable file (instanced down to the wght, ROND and opsz axes);
// Compose resolves a family by weight, so every weight needs its own Font entry
@OptIn(ExperimentalTextApi::class)
private fun flexFamily(roundness: Float) = FontFamily(
    FlexWeights.map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    }
)

val SansFlex = flexFamily(roundness = 0f)
val RoundedFlex = flexFamily(roundness = 100f)

private fun TextStyle.with(family: FontFamily, weight: Int, letterSpacingEm: Float? = null) = copy(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacingEm?.em ?: letterSpacing,
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.with(RoundedFlex, 700, -0.02f),
    displayMedium = base.displayMedium.with(RoundedFlex, 700, -0.02f),
    displaySmall = base.displaySmall.with(RoundedFlex, 700, -0.01f),
    headlineLarge = base.headlineLarge.with(RoundedFlex, 700, -0.01f),
    headlineMedium = base.headlineMedium.with(RoundedFlex, 650),
    headlineSmall = base.headlineSmall.with(RoundedFlex, 650),
    titleLarge = base.titleLarge.with(RoundedFlex, 600),
    titleMedium = base.titleMedium.with(SansFlex, 600),
    titleSmall = base.titleSmall.with(SansFlex, 600),
    bodyLarge = base.bodyLarge.with(SansFlex, 400),
    bodyMedium = base.bodyMedium.with(SansFlex, 400),
    bodySmall = base.bodySmall.with(SansFlex, 450),
    labelLarge = base.labelLarge.with(SansFlex, 600),
    labelMedium = base.labelMedium.with(SansFlex, 600),
    labelSmall = base.labelSmall.with(SansFlex, 600),
    displayLargeEmphasized = base.displayLargeEmphasized.with(RoundedFlex, 900, -0.02f),
    displayMediumEmphasized = base.displayMediumEmphasized.with(RoundedFlex, 900, -0.02f),
    displaySmallEmphasized = base.displaySmallEmphasized.with(RoundedFlex, 850, -0.01f),
    headlineLargeEmphasized = base.headlineLargeEmphasized.with(RoundedFlex, 850, -0.01f),
    headlineMediumEmphasized = base.headlineMediumEmphasized.with(RoundedFlex, 800),
    headlineSmallEmphasized = base.headlineSmallEmphasized.with(RoundedFlex, 800),
    titleLargeEmphasized = base.titleLargeEmphasized.with(RoundedFlex, 750),
    titleMediumEmphasized = base.titleMediumEmphasized.with(RoundedFlex, 750),
    titleSmallEmphasized = base.titleSmallEmphasized.with(SansFlex, 750),
    bodyLargeEmphasized = base.bodyLargeEmphasized.with(SansFlex, 600),
    bodyMediumEmphasized = base.bodyMediumEmphasized.with(SansFlex, 600),
    bodySmallEmphasized = base.bodySmallEmphasized.with(SansFlex, 600),
    labelLargeEmphasized = base.labelLargeEmphasized.with(SansFlex, 750),
    labelMediumEmphasized = base.labelMediumEmphasized.with(SansFlex, 750),
    labelSmallEmphasized = base.labelSmallEmphasized.with(SansFlex, 750),
)
