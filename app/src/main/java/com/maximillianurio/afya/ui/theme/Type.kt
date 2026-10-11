package com.maximillianurio.afya.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextStyle.Companion.Default
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maximillianurio.afya.R

/**
 * One entry of the variable file at one weight, with the `wght` axis pinned so the platform
 * instances the outline instead of synthesising a bold.
 */
@OptIn(ExperimentalTextApi::class)
private fun manropeFont(weight: Int): Font = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * Manrope carries the whole interface: a geometric humanist face whose numerals are wide enough to
 * be read at a glance standing on a scale. It ships as one variable file, so the family lists that
 * file once per weight. SIL Open Font License 1.1, Copyright 2018 The Manrope Project Authors.
 */
val Manrope = FontFamily(
    manropeFont(400),
    manropeFont(500),
    manropeFont(600),
    manropeFont(700),
    manropeFont(800),
)

/** Spline Sans Mono stays where monospace earns its place: hex, MACs and raw frames. */
val SheetMono = FontFamily(
    Font(R.font.spline_regular, FontWeight.Normal),
    Font(R.font.spline_medium, FontWeight.Medium),
    Font(R.font.spline_semibold, FontWeight.SemiBold),
    Font(R.font.spline_bold, FontWeight.Bold),
)

/** Tabular figures, so two readings line up digit for digit wherever they are printed. */
private const val Tabular = "tnum"

private fun manrope(weight: Int, size: Int, line: Int, tracking: Float = 0f) = Default.copy(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** The headline figure of a card: the value itself, set large and tabular. */
val FigureNumber = manrope(800, 34, 38, -0.8f).copy(fontFeatureSettings = Tabular)

/** The scale's own LCD value, the largest thing the app draws. */
val HeroNumber = manrope(800, 64, 66, -2.0f).copy(fontFeatureSettings = Tabular)

/** A value beside its unit inside a card. */
val TileValue = manrope(700, 22, 26, -0.4f).copy(fontFeatureSettings = Tabular)

/** The unit or qualifier printed under a figure. */
val FigureUnit = manrope(600, 13, 18, 0.2f)

/** A grouped section header. Uppercased at the call site: this build has no text-case style. */
val SectionHeader = manrope(700, 12, 16, 0.7f)

/** MAC addresses, raw frames and vendor keys are read as hex, not prose. */
val MonoBody = TextStyle(
    fontFamily = SheetMono,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

private val AppTypography = Typography(
    displayLarge = manrope(800, 44, 48, -1.4f),
    displayMedium = manrope(800, 36, 42, -1.0f),
    displaySmall = manrope(700, 30, 36, -0.7f),
    headlineLarge = manrope(700, 26, 34, -0.5f),
    headlineMedium = manrope(700, 22, 30, -0.4f),
    headlineSmall = manrope(600, 19, 26, -0.2f),
    titleLarge = manrope(600, 18, 26, -0.2f),
    titleMedium = manrope(600, 16, 24),
    titleSmall = manrope(600, 14, 20),
    bodyLarge = manrope(400, 16, 26, 0.1f),
    bodyMedium = manrope(400, 14, 22, 0.1f),
    bodySmall = manrope(400, 13, 19, 0.1f),
    labelLarge = manrope(600, 15, 20, 0.1f),
    labelMedium = manrope(500, 13, 18, 0.2f),
    labelSmall = manrope(600, 12, 16, 0.4f),
)

/** The Material roles, bound to Manrope. */
internal val AfyaTypography = AppTypography
