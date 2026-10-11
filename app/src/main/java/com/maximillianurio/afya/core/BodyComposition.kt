package com.maximillianurio.afya.core

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Body composition from published population equations, driven only by weight, height, age and
 * sex. These are statistical estimates, not bioelectrical-impedance measurements: the raw
 * impedance the scale broadcasts is carried separately as [ScaleReading.impedanceOhms] and is
 * never fed into an equation here.
 *
 * Deurenberg et al. 1991 (body fat from BMI/age/sex), Hall's lean-body-hydration constant
 * (TBW = FFM x 0.732), Mifflin-St Jeor 1990 (resting energy), Yuki et al. 2004 (visceral area
 * from BMI and age), and the classic reference-composition fractions for protein and mineral.
 *
 * Each tissue is taken as its own published fraction of fat-free mass. They are overlapping
 * estimates, not a partition: water, protein, mineral and muscle together exceed fat-free mass,
 * because each equation counts some of the same tissue. The report prints them as four separate
 * estimates rather than as a sum that has to come back to the weight on the scale.
 */
data class BodyMetrics(
    val bmi: Double,
    val fatPercent: Double,
    val fatMassKg: Double,
    val leanMassKg: Double,
    val muscleKg: Double,
    val musclePercent: Double,
    val skeletalMuscleKg: Double,
    val skeletalMusclePercent: Double,
    val waterKg: Double,
    val waterPercent: Double,
    val proteinKg: Double,
    val proteinPercent: Double,
    val boneMassKg: Double,
    val visceralCm2: Double,
    val bmrKcal: Double,
    val metabolicAge: Int,
    val idealWeightKg: Double,
    val obesityDegree: Double,
    val healthyRangeKg: Pair<Double, Double>,
) {
    val bmiCategory: String
        get() = when {
            bmi < 18.5 -> "Underweight"
            bmi < 25 -> "Healthy"
            bmi < 30 -> "Overweight"
            else -> "Obese"
        }
}

object BodyComposition {
    private const val MIN_AGE = 12
    private const val MAX_AGE = 100

    /** Hall: water is 73.2% of fat-free mass. */
    const val HYDRATION = 0.732

    /** Reference composition: protein is about 20.7% of fat-free mass. */
    const val PROTEIN_OF_FFM = 0.207

    /** Total body mineral carried in bone, about 5.2% of fat-free mass. */
    const val BONE_OF_FFM = 0.052

    /**
     * Muscle mass as the scales and the anthropometric literature count it: about 53% of fat-free
     * mass. This is the figure `docs/HANDOFF.md` hand-verifies (73.8 kg / 178 cm / 34 / male gives
     * 31.5 kg), so it is the one the port keeps.
     */
    const val MUSCLE_OF_FFM = 0.53

    /** Skeletal muscle is the part of that you move with, about four fifths of the muscle mass. */
    const val SKELETAL_OF_MUSCLE = 0.80

    /** Midpoint of the WHO healthy BMI range, used as the ideal-weight reference. */
    const val IDEAL_BMI = 21.7

    /** Only a load in this range can be a person; anything else is an object or a food sample. */
    fun bodyPlausible(grams: Double): Boolean = grams in 20_000.0..250_000.0

    fun compute(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): BodyMetrics? {
        if (weightKg < 2.0 || weightKg > 400.0) return null
        if (heightCm < 80.0 || heightCm > 250.0) return null
        val age = ageYears.coerceIn(MIN_AGE, MAX_AGE)
        val metres = heightCm / 100.0
        val bmi = weightKg / (metres * metres)
        if (bmi.isNaN() || bmi <= 0) return null

        val sexFactor = if (sex == Sex.MALE) 1 else 0
        val fatPercent = (1.20 * bmi + 0.23 * age - 10.8 * sexFactor - 5.4).coerceIn(3.0, 60.0)
        val fatMass = weightKg * fatPercent / 100.0
        val leanMass = weightKg - fatMass
        val waterKg = leanMass * HYDRATION
        val waterPercent = (waterKg / weightKg * 100.0).coerceIn(20.0, 75.0)
        val proteinKg = leanMass * PROTEIN_OF_FFM
        val boneMassKg = leanMass * BONE_OF_FFM
        val muscleKg = leanMass * MUSCLE_OF_FFM
        val skeletalMuscleKg = muscleKg * SKELETAL_OF_MUSCLE
        val idealWeightKg = IDEAL_BMI * metres * metres

        val visceral = if (sex == Sex.MALE) {
            -11.060 + 0.180 * bmi + 0.232 * age
        } else {
            -10.952 + 0.280 * bmi + 0.154 * age
        }

        val bmr = if (sex == Sex.MALE) {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }

        val metabolicAge = (age + (bmi - 22.0) * 0.75).roundToInt().coerceIn(MIN_AGE, MAX_AGE)
        val obesityDegree = (weightKg - idealWeightKg) / idealWeightKg * 100.0

        return BodyMetrics(
            bmi = round(bmi, 1),
            fatPercent = round(fatPercent, 1),
            fatMassKg = round(fatMass, 1),
            leanMassKg = round(leanMass, 1),
            muscleKg = round(muscleKg, 1),
            musclePercent = round(muscleKg / weightKg * 100.0, 1),
            skeletalMuscleKg = round(skeletalMuscleKg, 1),
            skeletalMusclePercent = round(skeletalMuscleKg / weightKg * 100.0, 1),
            waterKg = round(waterKg, 1),
            waterPercent = round(waterPercent, 1),
            proteinKg = round(proteinKg, 1),
            proteinPercent = round(proteinKg / weightKg * 100.0, 1),
            boneMassKg = round(boneMassKg, 2),
            visceralCm2 = round(max(visceral, 10.0), 0),
            bmrKcal = round(bmr, 0),
            metabolicAge = metabolicAge,
            idealWeightKg = round(idealWeightKg, 1),
            obesityDegree = round(obesityDegree, 1),
            healthyRangeKg = round(18.5 * metres * metres, 1) to round(24.9 * metres * metres, 1),
        )
    }

    /**
     * Reference band for a metric, so the UI can show where the value sits without inventing a
     * diagnosis. Returns null for metrics with no accepted band.
     */
    fun band(name: String, sex: Sex): Pair<Double, Double>? = when (name) {
        "bmi" -> 18.5 to 24.9
        "fat" -> if (sex == Sex.MALE) 8.0 to 19.0 else 21.0 to 32.0
        "skeletal" -> if (sex == Sex.MALE) 33.0 to 39.0 else 28.0 to 33.0
        "muscle" -> if (sex == Sex.MALE) 42.0 to 47.5 else 36.0 to 42.0
        "water" -> if (sex == Sex.MALE) 55.0 to 65.0 else 45.0 to 60.0
        "protein" -> if (sex == Sex.MALE) 16.0 to 20.0 else 14.0 to 18.0
        // Bone mineral in kilograms, so this band is read against the mass row, not a percentage.
        "bone" -> if (sex == Sex.MALE) 2.9 to 4.2 else 2.2 to 3.3
        "visceral" -> 0.0 to 10.0
        "obesity_degree" -> 0.0 to 10.0
        else -> null
    }

    /** The word printed beside a value. Deliberately not colour alone: the band must read aloud. */
    fun label(name: String, value: Double, sex: Sex): Band {
        val range = band(name, sex) ?: return Band.Unknown
        val (low, high) = range
        val spread = (high - low).coerceAtLeast(0.001)
        return when {
            value < low - spread * 0.35 -> Band.Low
            value < low -> Band.Below
            value <= high -> Band.Healthy
            value <= high + spread * 0.35 -> Band.Above
            value <= high + spread * 1.2 -> Band.High
            else -> Band.Obese
        }
    }

    /** The healthy range printed in the margin of a row, in the unit the band is defined in. */
    fun referenceText(name: String, sex: Sex): String? = band(name, sex)?.let {
        "${fmtEdge(it.first)}–${fmtEdge(it.second)}"
    }

    /** Band edges are reference figures, not measurements: 18.5 prints as 18.5, 10 as 10. */
    private fun fmtEdge(value: Double): String =
        "%.2f".format(value).trimEnd('0').trimEnd('.')

    /**
     * Metabolic age has no population band: it is read against the calendar age it was computed
     * from. Anything at or below that is the healthy case by construction.
     */
    fun labelMetabolicAge(value: Int, ageYears: Int): Band = when {
        value <= ageYears -> Band.Healthy
        value <= ageYears + 3 -> Band.Above
        value <= ageYears + 8 -> Band.High
        else -> Band.Obese
    }

    fun position(name: String, value: Double, sex: Sex): Float {
        val (low, high) = display(name, sex) ?: return 0.5f
        return ((value - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    }

    /** Axis the row bar is drawn against, wider than the healthy band so a band edge sits mid-bar. */
    fun display(name: String, sex: Sex): Pair<Double, Double>? = when (name) {
        "bmi" -> 14.0 to 40.0
        "fat" -> if (sex == Sex.MALE) 4.0 to 34.0 else 10.0 to 45.0
        "fat_mass" -> 2.0 to 50.0
        "skeletal" -> if (sex == Sex.MALE) 25.0 to 45.0 else 20.0 to 40.0
        "muscle" -> 25.0 to 55.0
        "water" -> 30.0 to 75.0
        "protein" -> 8.0 to 26.0
        "bone" -> 1.2 to 6.0
        "visceral" -> 1.0 to 30.0
        "bmr" -> 900.0 to 2800.0
        "metabolic_age" -> 12.0 to 85.0
        "obesity_degree" -> -20.0 to 120.0
        else -> null
    }

    /** How many decimal places this metric is printed with. */
    fun decimals(name: String): Int = if (name == "bone") 2 else 1

    private fun round(value: Double, places: Int): Double {
        val factor = 10.0.pow(places)
        return (value * factor).roundToInt() / factor
    }
}

/** Six steps between "far below" and "obese", so a value never has to be read off a colour. */
enum class Band(val word: String) {
    Low("Very low"),
    Below("Below range"),
    Healthy("In range"),
    Above("Above range"),
    High("High"),
    Obese("Very high"),
    Unknown("No range");

    val isGood: Boolean get() = this == Healthy
    val isWarn: Boolean get() = this == Below || this == Above
    val isBad: Boolean get() = this == Low || this == High || this == Obese
}
