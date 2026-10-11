package com.maximillianurio.afya.core

import kotlin.math.exp

/**
 * The weight underneath the daily noise. A body swings a kilogram or two from water, salt and the
 * last meal, so a single weigh-in says less than the run it belongs to. This is an exponentially
 * weighted average with a time constant rather than a fixed step, so a three-day gap counts as three
 * days and two readings a minute apart barely move it.
 */
object TrendWeight {

    /** About ten days of memory: the same smoothing a 10%-a-day moving average gives. */
    const val TAU_DAYS = 10.0

    private const val DAY_MS = 86_400_000.0

    /**
     * The trend at every point of [points] (time, value), which must be in ascending time order. The
     * first trend value is the first reading; each later one moves toward its reading by the share of
     * [TAU_DAYS] that has passed.
     */
    fun series(points: List<Pair<Long, Double>>, tauDays: Double = TAU_DAYS): List<Pair<Long, Double>> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Long, Double>>(points.size)
        var trend = points.first().second
        var at = points.first().first
        out.add(at to trend)
        for (i in 1 until points.size) {
            val (time, value) = points[i]
            val days = ((time - at) / DAY_MS).coerceAtLeast(0.0)
            val alpha = 1.0 - exp(-days / tauDays)
            trend += alpha * (value - trend)
            at = time
            out.add(time to trend)
        }
        return out
    }

    /** The trend as of the newest point, or null with fewer than two readings to smooth. */
    fun latest(points: List<Pair<Long, Double>>, tauDays: Double = TAU_DAYS): Double? =
        if (points.size < 2) null else series(points, tauDays).last().second

    /** Fewer readings than this, or a shorter span, and the "trend" is just the last reading again. */
    const val MIN_POINTS = 3
    const val MIN_SPAN_DAYS = 3

    /** True when [points] carry enough history for a trend to say something a reading does not. */
    fun meaningful(points: List<Pair<Long, Double>>): Boolean {
        if (points.size < MIN_POINTS) return false
        val span = points.maxOf { it.first } - points.minOf { it.first }
        return span >= MIN_SPAN_DAYS * DAY_MS
    }
}
