package com.maximillianurio.afya.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.maximillianurio.afya.core.Band

/**
 * Mint under daylight, graphite under lamplight. The ground is a pale mint wash so the white cards
 * read as lifted rather than outlined; the only saturated fill is the pine-teal brand. Eight metric
 * hues give each figure a colour of its own, and the four band colours say how that figure sits —
 * identity and status never share a channel, so a colour-blind reader still has the band word.
 */

// ---- light

internal val Canvas = Color(0xFFE8F1EE)
internal val Card = Color(0xFFFFFFFF)
internal val CardTint = Color(0xFFF2F8F6)
internal val Ink = Color(0xFF0E211E)
internal val InkMuted = Color(0xFF526B64)
internal val InkFaint = Color(0xFF7C938C)
internal val Hairline = Color(0xFFD7E5E0)

internal val Brand = Color(0xFF0F6B62)
internal val OnBrand = Color(0xFFFFFFFF)
internal val BrandDeep = Color(0xFF08453F)
internal val BrandWash = Color(0xFFDCEDE8)

internal val BandLow = Color(0xFF0A7FA0)
internal val BandHealthy = Color(0xFF2E7D46)
internal val BandWarn = Color(0xFFA0630A)
internal val BandHigh = Color(0xFFBE4235)

internal val HueTeal = Color(0xFF0F6B62)
internal val HueAqua = Color(0xFF0A7FA0)
internal val HueGrass = Color(0xFF2E7D46)
internal val HueAmber = Color(0xFFB4750C)
internal val HueCoral = Color(0xFFC2453A)
internal val HuePlum = Color(0xFF6A4BA0)
internal val HueSlate = Color(0xFF3A5FA8)
internal val HueRose = Color(0xFFA8437A)

// ---- dark: deep graphite-teal, its own colour set rather than an inversion

internal val NightCanvas = Color(0xFF0B1412)
internal val NightCard = Color(0xFF14201D)
internal val NightTint = Color(0xFF1C2A26)
internal val NightInk = Color(0xFFE4F0EC)
internal val NightInkMuted = Color(0xFF9BB1AA)
internal val NightInkFaint = Color(0xFF758983)
internal val NightHairline = Color(0xFF273833)

internal val NightBrand = Color(0xFF37B89E)
internal val NightOnBrand = Color(0xFF04120F)
internal val NightBrandDeep = Color(0xFF7FDCC6)
internal val NightBrandWash = Color(0xFF16332D)

internal val NightLow = Color(0xFF5FC0DC)
internal val NightHealthy = Color(0xFF77CE93)
internal val NightWarn = Color(0xFFE0A94E)
internal val NightHigh = Color(0xFFEE8578)

internal val NightTeal = Color(0xFF63C9B4)
internal val NightAqua = Color(0xFF5FC0DC)
internal val NightGrass = Color(0xFF77CE93)
internal val NightAmber = Color(0xFFE0A94E)
internal val NightCoral = Color(0xFFEE8578)
internal val NightPlum = Color(0xFFA98BE8)
internal val NightSlate = Color(0xFF8FADE8)
internal val NightRose = Color(0xFFE08FB8)

/**
 * The roles Material 3 has no name for. Read them through `MaterialTheme.afya` so light and night
 * stay in step; never drop to the constants above inside a screen.
 */
@Immutable
data class AfyaColors(
    val canvas: Color,
    val card: Color,
    val cardTint: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val brand: Color,
    val onBrand: Color,
    val brandDeep: Color,
    val brandWash: Color,
    val bandLow: Color,
    val bandHealthy: Color,
    val bandWarn: Color,
    val bandHigh: Color,
    val hueTeal: Color,
    val hueAqua: Color,
    val hueGrass: Color,
    val hueAmber: Color,
    val hueCoral: Color,
    val huePlum: Color,
    val hueSlate: Color,
    val hueRose: Color,
    /** The tint every card shadow is drawn in, so depth stays cool instead of going neutral grey. */
    val shadow: Color,
    /** How strongly a metric hue stains the tile behind its glyph. */
    val tileAlpha: Float,
) {
    /**
     * The hue a metric is drawn in. It identifies the figure and never judges it — the band does
     * that — so the same metric keeps its colour whether it reads healthy or high.
     */
    fun hue(name: String): Color = when (name) {
        "weight", "bmi" -> hueTeal
        "water", "water_mass" -> hueAqua
        "muscle", "muscle_mass", "skeletal", "skeletal_mass", "lean" -> hueGrass
        "fat", "fat_mass" -> hueAmber
        "visceral", "obesity_degree" -> hueCoral
        "bone", "metabolic_age" -> huePlum
        "protein", "protein_mass" -> hueSlate
        "bmr" -> hueRose
        else -> hueTeal
    }

    /**
     * The one colour a metric's status is drawn in. Every caller pairs it with `Band.word`, so the
     * band survives without colour.
     */
    fun band(band: Band): Color = when (band) {
        Band.Low -> bandLow
        Band.Below, Band.Above -> bandWarn
        Band.Healthy -> bandHealthy
        Band.High, Band.Obese -> bandHigh
        Band.Unknown -> inkFaint
    }
}

internal val AfyaLight = AfyaColors(
    canvas = Canvas,
    card = Card,
    cardTint = CardTint,
    ink = Ink,
    inkMuted = InkMuted,
    inkFaint = InkFaint,
    hairline = Hairline,
    brand = Brand,
    onBrand = OnBrand,
    brandDeep = BrandDeep,
    brandWash = BrandWash,
    bandLow = BandLow,
    bandHealthy = BandHealthy,
    bandWarn = BandWarn,
    bandHigh = BandHigh,
    hueTeal = HueTeal,
    hueAqua = HueAqua,
    hueGrass = HueGrass,
    hueAmber = HueAmber,
    hueCoral = HueCoral,
    huePlum = HuePlum,
    hueSlate = HueSlate,
    hueRose = HueRose,
    shadow = Color(0x240A2E28),
    tileAlpha = 0.13f,
)

internal val AfyaNight = AfyaColors(
    canvas = NightCanvas,
    card = NightCard,
    cardTint = NightTint,
    ink = NightInk,
    inkMuted = NightInkMuted,
    inkFaint = NightInkFaint,
    hairline = NightHairline,
    brand = NightBrand,
    onBrand = NightOnBrand,
    brandDeep = NightBrandDeep,
    brandWash = NightBrandWash,
    bandLow = NightLow,
    bandHealthy = NightHealthy,
    bandWarn = NightWarn,
    bandHigh = NightHigh,
    hueTeal = NightTeal,
    hueAqua = NightAqua,
    hueGrass = NightGrass,
    hueAmber = NightAmber,
    hueCoral = NightCoral,
    huePlum = NightPlum,
    hueSlate = NightSlate,
    hueRose = NightRose,
    shadow = Color(0x80000000),
    tileAlpha = 0.22f,
)
