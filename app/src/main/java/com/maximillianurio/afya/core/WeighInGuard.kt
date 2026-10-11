package com.maximillianurio.afya.core

import kotlin.math.abs
import kotlin.math.max

/**
 * The phone gets lent out. Anyone who steps on the bound scale broadcasts a perfectly good locked
 * frame, and without a check their weight would be filed as the owner's: computed with the owner's
 * height, age and sex, and drawn into the owner's trend.
 *
 * The check is deliberately narrow. It only asks when there is a recent pattern to compare with, and
 * only when the new figure is further from it than a body moves overnight.
 */
object WeighInGuard {

    /** How far back the recent pattern reaches. After a longer gap a real change is plausible. */
    const val WINDOW_MS = 14L * 86_400_000L

    /** At most this many recent weigh-ins make the reference, newest first. */
    const val SAMPLE = 7

    /** Never ask about less than this: water and a meal move a body a kilogram or two. */
    const val FLOOR_KG = 3.0

    /** ...and scale the allowance with the person, since 3 kg means more at 60 kg than at 130. */
    const val FRACTION = 0.03

    /**
     * The median of the recent weigh-ins, in kilograms, or null when fewer than two fall inside the
     * window. A median rather than a mean, so one earlier stray reading cannot move the reference.
     */
    fun reference(recent: List<Pair<Long, Double>>, nowMs: Long): Double? {
        val kilograms = recent
            .filter { (atMs, _) -> atMs <= nowMs && nowMs - atMs <= WINDOW_MS }
            .sortedByDescending { it.first }
            .take(SAMPLE)
            .map { it.second }
            .sorted()
        if (kilograms.size < 2) return null
        val middle = kilograms.size / 2
        return if (kilograms.size % 2 == 1) {
            kilograms[middle]
        } else {
            (kilograms[middle - 1] + kilograms[middle]) / 2.0
        }
    }

    /** How far from [referenceKg] a weigh-in may land before the app asks whose it is. */
    fun allowanceKg(referenceKg: Double): Double = max(FLOOR_KG, referenceKg * FRACTION)

    /** True when [kilograms] is far enough from the recent pattern to be someone else. */
    fun needsCheck(kilograms: Double, referenceKg: Double?): Boolean {
        val reference = referenceKg ?: return false
        return abs(kilograms - reference) > allowanceKg(reference)
    }
}
