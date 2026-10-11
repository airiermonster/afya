package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrendWeightTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test
    fun aSteadyWeightHasASteadyTrend() {
        val points = (0 until 20).map { now + it * day to 90.0 }
        TrendWeight.series(points).forEach { assertEquals(90.0, it.second, 1e-9) }
    }

    @Test
    fun oneHeavyMorningBarelyMovesIt() {
        val points = (0 until 14).map { now + it * day to 90.0 } + (now + 14 * day to 92.5)
        val trend = TrendWeight.latest(points)!!
        assertTrue("trend $trend", trend < 90.3)
    }

    @Test
    fun readingsMinutesApartCountAsOne() {
        val points = listOf(now to 121.7, now + 60_000 to 121.7, now + 120_000 to 123.7)
        val trend = TrendWeight.latest(points)!!
        assertTrue("trend $trend", abs(trend - 121.7) < 0.01)
    }

    @Test
    fun aRealLossIsFollowed() {
        // 0.1 kg a day for 60 days: the trend lags but ends within a kilogram of the readings.
        val points = (0 until 60).map { now + it * day to 100.0 - it * 0.1 }
        val trend = TrendWeight.latest(points)!!
        assertTrue("trend $trend", trend in 94.1..95.2)
    }

    @Test
    fun aSingleReadingHasNoTrend() {
        assertNull(TrendWeight.latest(listOf(now to 80.0)))
        assertTrue(TrendWeight.series(emptyList()).isEmpty())
    }

    @Test
    fun theProjectionReadsTheRecentRateNotTheFirstReadingEver() {
        // A year-old reading at 130 kg would make the old endpoint rate look like a crash diet. The
        // last four weeks lose 0.5 kg a week with daily noise of ±0.8 kg.
        val old = Targets.ReadingsDbEntry(now - 365 * day, 130_000.0)
        val recent = (1..28).map { back ->
            val noise = if (back % 2 == 0) 0.8 else -0.8
            Targets.ReadingsDbEntry(now - back * day, (100.0 + back * 0.5 / 7.0 + noise) * 1000.0)
        }
        val p = Targets.project(listOf(old) + recent, 100.0, 95.0, now)
        assertNotNull(p)
        assertEquals(-0.5, p!!.weeklyRateKg, 0.15)
        assertTrue(p.onTrack)
    }
}
