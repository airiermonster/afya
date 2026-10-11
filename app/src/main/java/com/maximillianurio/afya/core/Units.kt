package com.maximillianurio.afya.core

import kotlin.math.abs

enum class Sex { MALE, FEMALE }

enum class WeightUnit(val label: String, val gramsPer: Double) {
    KG("kg", 1000.0),
    LB("lb", 453.59238),
    ST_LB("st:lb", 6350.29318),
    JIN("jin", 500.0),
}

object Units {
    fun gramsToKg(grams: Double): Double = grams / 1000.0

    fun kgToGrams(kg: Double): Double = kg * 1000.0

    /** Short display value, e.g. "68.4 kg" or "10 12" for 10 st 12 lb. */
    fun format(grams: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.ST_LB -> {
            val totalPounds = grams / WeightUnit.LB.gramsPer
            val stones = (totalPounds / 14).toInt()
            val pounds = totalPounds - stones * 14
            if (stones < 1) "${"%.1f".format(totalPounds)} lb" else "$stones ${"%.0f".format(pounds)}"
        }

        else -> "${"%.1f".format(grams / unit.gramsPer)} ${unit.label}"
    }

    /** Numeric part only, for the big hero readout. */
    fun valueText(grams: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.ST_LB -> {
            val totalPounds = grams / WeightUnit.LB.gramsPer
            val stones = (totalPounds / 14).toInt()
            "${"%.1f".format(stones + (totalPounds - stones * 14) / 14)}"
        }

        else -> "%.1f".format(grams / unit.gramsPer)
    }

    fun toGrams(value: Double, unit: WeightUnit): Double = value * unit.gramsPer

    /** True when the scale already broadcasts in the unit the profile asks for. */
    fun isChosenUnit(scale: ScaleUnit, chosen: WeightUnit): Boolean = scale.preference == chosen
}

/** Split a mass in kg into the number/unit pair the tiles and deltas use for the profile unit. */
fun massParts(kg: Double, unit: WeightUnit): Pair<String, String> = when (unit) {
    WeightUnit.LB -> "%.1f".format(kg * 1000 / WeightUnit.LB.gramsPer) to "lb"
    WeightUnit.JIN -> "%.1f".format(kg * 2) to "jin"
    WeightUnit.ST_LB -> Units.valueText(kg * 1000, unit) to "st"
    else -> "%.1f".format(kg) to "kg"
}

/**
 * Signed change against a previous reading, split into the direction and the figure. The UI draws
 * the direction with its own glyph instead of printing a arrow character.
 */
fun deltaParts(changeKg: Double, unit: WeightUnit): Pair<Trend, String> {
    val trend = when {
        changeKg > 0.05 -> Trend.Up
        changeKg < -0.05 -> Trend.Down
        else -> Trend.Flat
    }
    if (trend == Trend.Flat) return Trend.Flat to "steady"
    val value = massParts(abs(changeKg), unit)
    return trend to "${if (changeKg < 0) "−" else "+"}${value.first} ${value.second}"
}
