package com.maximillianurio.afya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Cards are generous and controls are soft: 28 dp on a card, 20 dp on an inset tile, 12 dp on a
 * chip. Nothing in the app is square, and nothing is a pill except a small selectable control.
 */
private val AfyaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AfyaLightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = BrandWash,
    onPrimaryContainer = BrandDeep,
    secondary = BrandDeep,
    onSecondary = OnBrand,
    secondaryContainer = CardTint,
    onSecondaryContainer = Ink,
    tertiary = HueCoral,
    onTertiary = OnBrand,
    tertiaryContainer = BrandWash,
    onTertiaryContainer = BrandDeep,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = BrandWash,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = CardTint,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Card,
    inverseSurface = Ink,
    inverseOnSurface = Card,
    outline = InkFaint,
    outlineVariant = Hairline,
    error = BandHigh,
    onError = OnBrand,
    errorContainer = BrandWash,
    onErrorContainer = BandHigh,
)

private val AfyaNightScheme = darkColorScheme(
    primary = NightBrand,
    onPrimary = NightOnBrand,
    primaryContainer = NightBrandWash,
    onPrimaryContainer = NightBrandDeep,
    secondary = NightBrandDeep,
    onSecondary = NightCanvas,
    secondaryContainer = NightTint,
    onSecondaryContainer = NightInk,
    tertiary = NightCoral,
    onTertiary = NightOnBrand,
    tertiaryContainer = NightBrandWash,
    onTertiaryContainer = NightBrandDeep,
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightCanvas,
    onSurface = NightInk,
    surfaceVariant = NightTint,
    onSurfaceVariant = NightInkMuted,
    surfaceContainerLow = NightTint,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightCard,
    surfaceContainerHighest = NightCard,
    inverseSurface = NightInk,
    inverseOnSurface = NightCanvas,
    outline = NightInkFaint,
    outlineVariant = NightHairline,
    error = NightHigh,
    onError = NightOnBrand,
    errorContainer = NightBrandWash,
    onErrorContainer = NightHigh,
)

internal val LocalAfyaColors = compositionLocalOf { AfyaLight }

/** The roles Material has no name for; read colour through this, never through the hex. */
val MaterialTheme.afya: AfyaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAfyaColors.current

/**
 * -1 follows the system, 0 keeps the app on mint, 1 keeps it on graphite. The setting wins over the
 * system flag because the same phone reads differently in a lit bathroom and in bed.
 */
fun afyaUsesNight(themeMode: Int, systemDark: Boolean): Boolean = when (themeMode) {
    0 -> false
    1 -> true
    else -> systemDark
}

@Composable
fun AfyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAfyaColors provides if (darkTheme) AfyaNight else AfyaLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AfyaNightScheme else AfyaLightScheme,
            typography = AfyaTypography,
            shapes = AfyaShapes,
            content = content,
        )
    }
}
