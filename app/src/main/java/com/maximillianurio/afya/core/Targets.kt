package com.maximillianurio.afya.core

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Self-reported movement, used for the water goal and the energy target. `label` is what the control
 * shows and `note` the frequency it stands for, kept apart so a row of choices stays scannable.
 */
enum class ActivityLevel(val label: String, val note: String, val factor: Double, val extraWaterMl: Int) {
    SEDENTARY("Little or none", "Under 1 day a week", 1.2, 0),
    LIGHT("Light", "1-3 days a week", 1.375, 250),
    MODERATE("Moderate", "3-5 days a week", 1.55, 500),
    VIGOROUS("Vigorous", "6-7 days a week", 1.725, 750),
    ATHLETE("Very hard", "Daily, or a physical job", 1.9, 1000),
}

/** How hot and humid it is where you are; both push the water goal up. */
enum class Heat(val label: String, val extraWaterMl: Int) {
    COOL("Cool", 0),
    MILD("Mild", 150),
    HOT("Hot", 400),
}

/**
 * What a logged drink is. Every kind counts toward the water tally — the volume is what the glass
 * held, not a hydration score — so the tally stays a record of what was drunk.
 */
enum class DrinkKind(val label: String, val defaultMl: Int) {
    WATER("Water", 250),
    COFFEE("Coffee", 200),
    TEA("Tea", 200),
    JUICE("Juice", 250),
    SODA("Soda", 330),
    MILK("Milk", 250),
    OTHER("Other", 200),
}

/**
 * What the body asks for, derived only from the metrics already on the record sheet. Every number
 * here is a population-level guideline, not a prescription: the UI says so wherever it prints one.
 */
object Targets {

    /** 30-35 ml per kg of fat-free mass is the usual working range; 33 is the middle of it. */
    const val WATER_PER_KG_FFM = 33.0

    /** The working figure for a kilo of body tissue, the same in either direction. */
    const val KILOCAL_PER_KG = 7_700

    data class Water(
        val millilitres: Int,
        val baseMl: Int,
        val activityMl: Int,
        val heatMl: Int,
    )

    fun water(ffmKg: Double, activity: ActivityLevel, heat: Heat): Water {
        val base = (ffmKg * WATER_PER_KG_FFM).roundToInt()
        val total = (base + activity.extraWaterMl + heat.extraWaterMl).coerceIn(1_000, 6_000)
        return Water(total, base, activity.extraWaterMl, heat.extraWaterMl)
    }

    data class Energy(
        val maintenanceKcal: Int,
        val targetKcal: Int,
        val direction: Direction,
        val carbsGrams: Double,
        val proteinGrams: Double,
        val fatGrams: Double,
        val mealsKcal: List<Pair<String, Int>>,
    )

    enum class Direction { LOSE, MAINTAIN, GAIN }

    /**
     * Energy target from resting metabolism scaled by activity. The direction comes from the goal
     * weight when one has been written in, from the stated intent when it has not, and is "hold" when
     * neither exists — the app never picks a goal weight on its own. The move is priced at the usual
     * 7 700 kcal per kilo, capped by the floor the guidelines consider safe.
     */
    fun energy(
        bmrKcal: Double,
        activity: ActivityLevel,
        currentKg: Double,
        targetKg: Double,
        sex: Sex,
        intent: IntentProfile? = null,
    ): Energy {
        val maintenance = (bmrKcal * activity.factor).roundToInt()
        val gap = targetKg - currentKg
        val goalDirection = when {
            targetKg <= 0 || abs(gap) < 0.5 -> null
            gap < 0 -> Direction.LOSE
            else -> Direction.GAIN
        }
        val direction = goalDirection ?: intent?.aim?.let {
            when (it) {
                Aim.Losing -> Direction.LOSE
                Aim.Gaining -> Direction.GAIN
                Aim.Maintaining -> Direction.MAINTAIN
            }
        } ?: Direction.MAINTAIN
        // A written goal keeps the steady half-kilo rate; a stated intent moves at the rate chosen.
        val weekKg = if (goalDirection != null) 0.5 else intent?.pace?.weekKg ?: 0.5
        val move = (weekKg * KILOCAL_PER_KG / 7.0).roundToInt()
        val delta = when (direction) {
            Direction.MAINTAIN -> 0
            Direction.LOSE -> -move
            Direction.GAIN -> move
        }
        val floor = if (sex == Sex.MALE) 1_800 else 1_500
        val target = if (delta < 0) max(maintenance + delta, floor) else maintenance + delta

        // Acceptable Macronutrient Distribution Ranges, taken at the middle: 50 / 25 / 25.
        val carbs = target * 0.50 / 4.0
        val protein = target * 0.25 / 4.0
        val fat = target * 0.25 / 9.0
        val meals = listOf(
            "Breakfast" to (target * 0.25).roundToInt(),
            "Lunch" to (target * 0.35).roundToInt(),
            "Dinner" to (target * 0.30).roundToInt(),
            "Snack" to (target * 0.10).roundToInt(),
        )
        return Energy(
            maintenanceKcal = maintenance,
            targetKcal = target,
            direction = direction,
            carbsGrams = round1(carbs),
            proteinGrams = round1(protein),
            fatGrams = round1(fat),
            mealsKcal = meals,
        )
    }

    /**
     * Nine-cell body type: BMI up the side, body fat across the bottom. The cell is a description,
     * not a diagnosis, and says so.
     */
    enum class BodyType(val label: String) {
        EMACIATED("Emaciated"),
        SLIGHTLY_UNDERWEIGHT("Slightly underweight"),
        HIDDEN_OBESITY("Hidden obesity"),
        MUSCULAR_TYPE("Muscular type"),
        HEALTHY("Healthy type"),
        SLIGHTLY_OVERWEIGHT("Slightly overweight"),
        ATHLETIC("Athlete body type"),
        MUSCULAR_OBESITY("Muscular obesity"),
        OBESE("Obesity"),
    }

    fun bodyType(bmi: Double, fatPercent: Double, sex: Sex): BodyType {
        val fatCeilings = if (sex == Sex.MALE) 20.0 to 26.0 else 30.0 to 36.0
        val fatColumn = when {
            fatPercent < fatCeilings.first -> 0
            fatPercent <= fatCeilings.second -> 1
            else -> 2
        }
        val bmiRow = when {
            bmi < 18.5 -> 0
            bmi <= 25.0 -> 1
            else -> 2
        }
        return BodyType.values()[bmiRow * 3 + fatColumn]
    }

    data class Projection(
        val weeklyRateKg: Double,
        val weeks: Int,
        val etaDays: Long,
        val remainingKg: Double,
        val onTrack: Boolean,
    )

    /** How far back the rate is read. Older readings describe a body that has since moved on. */
    const val PROJECTION_WINDOW_DAYS = 28L

    /**
     * Extrapolates the goal date from the rate the readings actually show, so the plan moves when
     * the user does. Null when there is not enough history or the trend points away from the goal.
     *
     * The rate is a least-squares line through every reading of the last four weeks and the current
     * weight, not the gap between the first reading ever and today, so one heavy morning a month ago
     * cannot set the date. With nothing inside the window the newest older reading anchors the line.
     */
    fun project(
        entries: List<ReadingsDbEntry>,
        currentKg: Double,
        targetKg: Double,
        nowMs: Long,
    ): Projection? {
        if (targetKg <= 0 || entries.isEmpty()) return null
        val remaining = targetKg - currentKg
        if (abs(remaining) < 0.2) return null
        val windowStart = nowMs - TimeUnit.DAYS.toMillis(PROJECTION_WINDOW_DAYS)
        val inside = entries.filter { it.timestampMs in windowStart..nowMs }
        val basis = inside.ifEmpty {
            listOfNotNull(entries.filter { it.timestampMs < windowStart }.maxByOrNull { it.timestampMs })
        }
        val points = basis.map { it.timestampMs to it.grams / 1000.0 } + (nowMs to currentKg)
        val rate = weeklySlope(points) ?: return null
        if (rate == 0.0) return null
        // The rate has to close the gap: only a gain counts down to a goal above the current
        // weight, and only a loss counts down to one below it. Moving away is still information.
        if ((rate > 0) != (remaining > 0)) return null
        val weeks = (remaining / rate).roundToInt().coerceAtLeast(1)
        return Projection(
            weeklyRateKg = round2(rate),
            weeks = weeks,
            etaDays = nowMs + TimeUnit.DAYS.toMillis((weeks * 7.0).roundToInt().toLong()),
            remainingKg = round2(abs(remaining)),
            onTrack = abs(rate) <= 1.0,
        )
    }

    /**
     * Kilograms per week along the least-squares line through (time, kg) points. Null when the points
     * span less than a day: same-day readings carry no rate.
     */
    fun weeklySlope(points: List<Pair<Long, Double>>): Double? {
        if (points.size < 2) return null
        val t0 = points.minOf { it.first }
        val span = points.maxOf { it.first } - t0
        if (span < TimeUnit.DAYS.toMillis(1)) return null
        val weekMs = TimeUnit.DAYS.toMillis(7).toDouble()
        val xs = points.map { (it.first - t0) / weekMs }
        val ys = points.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        var covariance = 0.0
        var variance = 0.0
        for (i in xs.indices) {
            covariance += (xs[i] - meanX) * (ys[i] - meanY)
            variance += (xs[i] - meanX) * (xs[i] - meanX)
        }
        return if (variance == 0.0) null else covariance / variance
    }

    /** The narrow shape [project] needs, kept here so the core layer does not import the database. */
    data class ReadingsDbEntry(val timestampMs: Long, val grams: Double)

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0
}
