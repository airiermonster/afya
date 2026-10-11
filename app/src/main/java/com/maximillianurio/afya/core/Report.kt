package com.maximillianurio.afya.core

import kotlin.math.abs
import kotlin.math.pow

/** Which way a metric moved against the weigh-in before this one. */
enum class Trend(val arrow: String) {
    Up("▲"),
    Down("▼"),
    Flat("–"),
    /** No previous weigh-in to compare with. */
    First(""),
}

/**
 * One printed line of the composition report: the figure, the reference band it is read against,
 * the word for where it sits, the change since last time, and one plain sentence about what it is.
 */
data class ReportRow(
    val name: String,
    val title: String,
    val valueText: String,
    val unit: String,
    val band: Band,
    val reference: String,
    /** 0..1 along [BodyComposition.display] for this row, for the band bar. */
    val position: Float,
    val explainer: String,
    val trend: Trend,
    val changeText: String,
) {
    val bandWord: String get() = band.word
    val hasChange: Boolean get() = trend == Trend.Up || trend == Trend.Down
}

/**
 * The whole report, assembled from the metrics [BodyComposition] computed and, when one exists, the
 * metrics of the previous weigh-in. Every row is a published population estimate off weight, height,
 * age and sex — never an impedance measurement — and each explainer says what the figure is rather
 * than leaving the UI to append a disclaimer to all nineteen.
 */
object Report {

    /**
     * True when two filed snapshots were computed from the same height, age and sex, so a change
     * between them is a change in the body and not in the profile it was read through.
     *
     * Every snapshot already carries the evidence: the reference weight is 21.7 × height² and so
     * fixes the height, and resting energy minus 10 × weight is 6.25 × height − 5 × age ± the sex
     * constant, so it fixes age and sex once height is known. Both are stored rounded (0.1 kg and
     * 1 kcal), which the tolerances absorb; one centimetre or one year moves them well past it.
     * Rows filed before this check existed need no migration for the same reason.
     */
    fun sameBasis(newer: BodyMetrics, newerKg: Double, older: BodyMetrics, olderKg: Double): Boolean {
        if (abs(newer.idealWeightKg - older.idealWeightKg) > IDEAL_TOLERANCE_KG) return false
        val newerBasis = newer.bmrKcal - 10.0 * newerKg
        val olderBasis = older.bmrKcal - 10.0 * olderKg
        return abs(newerBasis - olderBasis) < BASIS_TOLERANCE_KCAL
    }

    /** [older] when it can be compared with [newer], otherwise null so no change is printed. */
    fun comparable(newer: BodyMetrics?, newerKg: Double, older: BodyMetrics?, olderKg: Double): BodyMetrics? {
        if (newer == null || older == null) return null
        return older.takeIf { sameBasis(newer, newerKg, it, olderKg) }
    }

    /**
     * The figure row [name] prints for one snapshot, as a number in the unit that row prints in, for
     * plotting a row's history. Null for rows that carry no single figure (the healthy range).
     */
    fun figureOf(name: String, metrics: BodyMetrics, unit: WeightUnit): Double? {
        fun mass(kg: Double) = kg * 1000.0 / unit.gramsPer
        return when (name) {
            "bmi" -> metrics.bmi
            "fat" -> metrics.fatPercent
            "fat_mass" -> mass(metrics.fatMassKg)
            "lean" -> mass(metrics.leanMassKg)
            "water" -> metrics.waterPercent
            "water_mass" -> mass(metrics.waterKg)
            "muscle" -> metrics.musclePercent
            "muscle_mass" -> mass(metrics.muscleKg)
            "skeletal" -> metrics.skeletalMusclePercent
            "skeletal_mass" -> mass(metrics.skeletalMuscleKg)
            "protein" -> metrics.proteinPercent
            "protein_mass" -> mass(metrics.proteinKg)
            "bone" -> mass(metrics.boneMassKg)
            "visceral" -> metrics.visceralCm2
            "bmr" -> metrics.bmrKcal
            "metabolic_age" -> metrics.metabolicAge.toDouble()
            "obesity_degree" -> metrics.obesityDegree
            "ideal_weight" -> mass(metrics.idealWeightKg)
            else -> null
        }
    }

    private const val IDEAL_TOLERANCE_KG = 0.06
    private const val BASIS_TOLERANCE_KCAL = 2.5

    fun rows(
        metrics: BodyMetrics,
        weightKg: Double,
        ageYears: Int,
        sex: Sex,
        unit: WeightUnit,
        previous: BodyMetrics? = null,
    ): List<ReportRow> {
        fun bandOf(name: String, value: Double): Band = BodyComposition.label(name, value, sex)

        // Mass rows carry no band of their own: they are read against their percentage sibling.
        fun massRow(
            name: String,
            title: String,
            kg: Double,
            bandName: String?,
            explainer: String,
            previousKg: Double?,
            referenceOverride: String? = null,
        ): ReportRow {
            val parts = massParts(kg, unit)
            val bandValue = when (bandName) {
                null -> Band.Unknown
                else -> bandOf(bandName, percentOf(bandName, metrics))
            }
            val bandUnit = if (bandName == "bone") "kg" else "%"
            val reference = referenceOverride
                ?: referenceFor(bandName ?: name, if (bandName == null) "" else bandUnit, sex)
            return ReportRow(
                name = name,
                title = title,
                valueText = parts.first,
                unit = parts.second,
                band = bandValue,
                reference = reference,
                position = positionOfMass(bandName, metrics, sex),
                explainer = explainer,
                trend = trendOfMass(kg, previousKg),
                changeText = changeOfMass(kg, previousKg, unit),
            )
        }

        return listOf(
            numRow(
                name = "bmi",
                title = "Body mass index",
                value = metrics.bmi,
                unit = "",
                bandName = "bmi",
                sex = sex,
                explainer = "Weight against height squared. A screening figure for the " +
                    "population, not a measurement of your tissue.",
                previous = previous?.bmi,
            ),
            pctRow(
                name = "fat",
                title = "Body fat",
                value = metrics.fatPercent,
                bandName = "fat",
                sex = sex,
                explainer = "Share of your weight that is fat tissue, from the Deurenberg " +
                    "equation for your sex and age.",
                previous = previous?.fatPercent,
            ),
            massRow(
                name = "fat_mass",
                title = "Fat mass",
                kg = metrics.fatMassKg,
                bandName = "fat",
                explainer = "How much of your weight is fat tissue, at the percentage above.",
                previousKg = previous?.fatMassKg,
            ),
            massRow(
                name = "lean",
                title = "Fat-free mass",
                kg = metrics.leanMassKg,
                bandName = null,
                explainer = "Everything that is not fat: water, protein, mineral and muscle " +
                    "together. The rows below split it up.",
                previousKg = previous?.leanMassKg,
                referenceOverride = "weight − fat mass",
            ),
            pctRow(
                name = "water",
                title = "Total body water",
                value = metrics.waterPercent,
                bandName = "water",
                sex = sex,
                explainer = "Share of your weight that is water. Lean tissue holds almost all of " +
                    "it, so this tracks muscle rather than drink.",
                previous = previous?.waterPercent,
            ),
            massRow(
                name = "water_mass",
                title = "Water",
                kg = metrics.waterKg,
                bandName = "water",
                explainer = "Total water across every tissue, taken as 73.2% of fat-free mass.",
                previousKg = previous?.waterKg,
            ),
            massRow(
                name = "muscle_mass",
                title = "Muscle mass",
                kg = metrics.muscleKg,
                bandName = "muscle",
                explainer = "Muscle counted the way body scales count it: about 53% of fat-free " +
                    "mass. It follows your weight, not your training — nothing here measures " +
                    "strength.",
                previousKg = previous?.muscleKg,
            ),
            pctRow(
                name = "muscle",
                title = "Muscle",
                value = metrics.musclePercent,
                bandName = "muscle",
                sex = sex,
                explainer = "Share of your weight that is muscle tissue.",
                previous = previous?.musclePercent,
            ),
            massRow(
                name = "skeletal_mass",
                title = "Skeletal muscle mass",
                kg = metrics.skeletalMuscleKg,
                bandName = "skeletal",
                explainer = "The muscles you move with, taken as just over half of total muscle.",
                previousKg = previous?.skeletalMuscleKg,
            ),
            pctRow(
                name = "skeletal",
                title = "Skeletal muscle",
                value = metrics.skeletalMusclePercent,
                bandName = "skeletal",
                sex = sex,
                explainer = "Share of your weight that is skeletal muscle.",
                previous = previous?.skeletalMusclePercent,
            ),
            massRow(
                name = "protein_mass",
                title = "Protein",
                kg = metrics.proteinKg,
                bandName = "protein",
                explainer = "The protein in your tissues, taken as about a fifth of fat-free mass.",
                previousKg = previous?.proteinKg,
            ),
            pctRow(
                name = "protein",
                title = "Protein",
                value = metrics.proteinPercent,
                bandName = "protein",
                sex = sex,
                explainer = "Share of your weight that is body protein, which sits a little lower " +
                    "for women than for men.",
                previous = previous?.proteinPercent,
            ),
            massRow(
                name = "bone",
                title = "Bone mineral",
                kg = metrics.boneMassKg,
                bandName = "bone",
                explainer = "Mineral carried in bone, taken as about 5.2% of fat-free mass. Not a " +
                    "bone-density measurement.",
                previousKg = previous?.boneMassKg,
            ),
            numRow(
                name = "visceral",
                title = "Visceral fat area",
                value = metrics.visceralCm2,
                unit = "cm²",
                bandName = "visceral",
                sex = sex,
                decimals = 0,
                explainer = "Estimated area of fat around the organs, from BMI and age. Guideline " +
                    "concern starts above 10 cm².",
                previous = previous?.visceralCm2,
            ),
            numRow(
                name = "bmr",
                title = "Resting energy",
                value = metrics.bmrKcal,
                unit = "kcal",
                bandName = null,
                sex = sex,
                decimals = 0,
                reference = "Mifflin-St Jeor",
                explainer = "Energy your body would use at rest, from the Mifflin-St Jeor " +
                    "equation. Your daily need is this multiplied up by how active you are.",
                previous = previous?.bmrKcal,
            ),
            ageRow(
                value = metrics.metabolicAge,
                ageYears = ageYears,
                sex = sex,
                previous = previous?.metabolicAge,
            ),
            pctRow(
                name = "obesity_degree",
                title = "Over reference weight",
                value = metrics.obesityDegree,
                bandName = "obesity_degree",
                sex = sex,
                explainer = "How far your weight is above the mid-range weight for your height. " +
                    "A negative figure means it is below it.",
                previous = previous?.obesityDegree,
            ),
            massRow(
                name = "ideal_weight",
                title = "Reference weight",
                kg = metrics.idealWeightKg,
                bandName = null,
                explainer = "The midpoint weight for your height. It is what the row above is " +
                    "worked from, not a target set for you.",
                previousKg = previous?.idealWeightKg,
                referenceOverride = "BMI 21.7",
            ),
            rangeRow(
                metrics = metrics,
                weightKg = weightKg,
                unit = unit,
                sex = sex,
            ),
        )
    }

    /** The band printed in the margin, carrying the row's own unit so it reads on its own. */
    private fun referenceFor(name: String, unit: String, sex: Sex): String =
        BodyComposition.referenceText(name, sex)?.let { if (unit.isEmpty()) it else "$it $unit" }
            ?: "—"

    private fun pctRow(
        name: String,
        title: String,
        value: Double,
        bandName: String,
        sex: Sex,
        explainer: String,
        previous: Double?,
    ): ReportRow = numRow(
        name = name,
        title = title,
        value = value,
        unit = "%",
        bandName = bandName,
        sex = sex,
        explainer = explainer,
        previous = previous,
    )

    private fun numRow(
        name: String,
        title: String,
        value: Double,
        unit: String,
        bandName: String?,
        sex: Sex,
        decimals: Int = BodyComposition.decimals(name),
        reference: String? = null,
        explainer: String,
        previous: Double?,
    ): ReportRow = ReportRow(
        name = name,
        title = title,
        valueText = "%.${decimals}f".format(value),
        unit = unit,
        band = bandName?.let { BodyComposition.label(it, value, sex) } ?: Band.Unknown,
        reference = reference ?: referenceFor(bandName ?: name, unit, sex),
        position = BodyComposition.position(name, value, sex),
        explainer = explainer,
        trend = trendOf(value, previous, decimals),
        changeText = changeOf(value, previous, decimals, unit),
    )

    /** Metabolic age is read against the calendar age it was computed from, not a fixed band. */
    private fun ageRow(
        value: Int,
        ageYears: Int,
        sex: Sex,
        previous: Int?,
    ): ReportRow = ReportRow(
        name = "metabolic_age",
        title = "Body age",
        valueText = "$value",
        unit = "yrs",
        band = BodyComposition.labelMetabolicAge(value, ageYears),
        reference = "calendar age $ageYears",
        position = BodyComposition.position("metabolic_age", value.toDouble(), sex),
        explainer = "Resting energy compared with the age group it is typical for. At or below " +
            "your calendar age is the good case.",
        trend = trendOf(value.toDouble(), previous?.toDouble(), 0),
        changeText = changeOf(value.toDouble(), previous?.toDouble(), 0, "yrs"),
    )

    /** The healthy weight span for this height: a range on one line, so it has no band itself. */
    private fun rangeRow(
        metrics: BodyMetrics,
        weightKg: Double,
        unit: WeightUnit,
        sex: Sex,
    ): ReportRow {
        val (low, high) = metrics.healthyRangeKg
        return ReportRow(
            name = "healthy_range",
            title = "Healthy weight",
            valueText = "${massParts(low, unit).first}–${massParts(high, unit).first}",
            unit = massParts(high, unit).second,
            band = if (weightKg in low..high) Band.Healthy else Band.Unknown,
            reference = "BMI 18.5–24.9",
            position = BodyComposition.position("bmi", metrics.bmi, sex),
            explainer = "The weights that sit inside the BMI band at your height. A stable height " +
                "always gives the same span.",
            trend = Trend.First,
            changeText = "",
        )
    }

    /** The percentage a mass row is read against, so both rows land on the same band. */
    private fun percentOf(bandName: String, metrics: BodyMetrics): Double = when (bandName) {
        "fat" -> metrics.fatPercent
        "water" -> metrics.waterPercent
        "muscle" -> metrics.musclePercent
        "skeletal" -> metrics.skeletalMusclePercent
        "protein" -> metrics.proteinPercent
        "bone" -> metrics.boneMassKg
        else -> 0.0
    }

    private fun positionOfMass(bandName: String?, metrics: BodyMetrics, sex: Sex): Float {
        if (bandName == null) return 0.5f
        val display = if (bandName == "bone") "bone" else bandName
        return BodyComposition.position(display, percentOf(bandName, metrics), sex)
    }

    /** Half a step of the printed precision: 50 g is not a change in a 0.1 kg figure. */
    private fun trendOfMass(kg: Double, previousKg: Double?): Trend {
        if (previousKg == null) return Trend.First
        val delta = kg - previousKg
        return when {
            abs(delta) < 0.05 -> Trend.Flat
            delta > 0 -> Trend.Up
            else -> Trend.Down
        }
    }

    private fun changeOfMass(kg: Double, previousKg: Double?, unit: WeightUnit): String {
        val before = previousKg ?: return ""
        val delta = kg - before
        if (abs(delta) < 0.05) return "no change"
        val parts = massParts(abs(delta), unit)
        return "${if (delta > 0) "+" else "−"}${parts.first} ${parts.second}"
    }

    private fun trendOf(value: Double, previous: Double?, decimals: Int): Trend {
        if (previous == null) return Trend.First
        val delta = value - previous
        val epsilon = 0.5 / 10.0.pow(decimals)
        return when {
            abs(delta) < epsilon -> Trend.Flat
            delta > 0 -> Trend.Up
            else -> Trend.Down
        }
    }

    private fun changeOf(value: Double, previous: Double?, decimals: Int, unit: String): String {
        val before = previous ?: return ""
        val delta = value - before
        if (abs(delta) < 0.5 / 10.0.pow(decimals)) return "no change"
        val amount = "%.${decimals}f".format(abs(delta))
        val sign = if (delta > 0) "+" else "−"
        return if (unit.isEmpty()) "$sign$amount" else "$sign$amount $unit"
    }
}
