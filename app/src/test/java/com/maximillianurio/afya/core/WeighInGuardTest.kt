package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who the scale was lent to must not end up in the owner's records unasked. */
class WeighInGuardTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun daily(vararg kilograms: Double) =
        kilograms.mapIndexed { index, kg -> now - (index + 1) * day to kg }

    @Test
    fun withNoRecentPatternNothingIsAsked() {
        assertNull(WeighInGuard.reference(emptyList(), now))
        assertNull(WeighInGuard.reference(daily(120.0), now))
        assertFalse(WeighInGuard.needsCheck(60.0, null))
    }

    @Test
    fun readingsOutsideTheWindowDoNotCount() {
        val old = listOf(now - 20 * day to 120.0, now - 30 * day to 121.0)
        assertNull(WeighInGuard.reference(old, now))
    }

    @Test
    fun theReferenceIsTheMedianSoOneStrayCannotMoveIt() {
        assertEquals(121.7, WeighInGuard.reference(daily(121.7, 121.7, 62.0), now)!!, 1e-9)
        assertEquals(121.0, WeighInGuard.reference(daily(120.0, 122.0), now)!!, 1e-9)
    }

    @Test
    fun ordinaryDayToDayMovementIsFiledWithoutAsking() {
        val reference = WeighInGuard.reference(daily(121.7, 121.7, 122.4), now)
        assertFalse(WeighInGuard.needsCheck(123.7, reference))
        assertFalse(WeighInGuard.needsCheck(119.0, reference))
    }

    @Test
    fun aGuestIsAskedAbout() {
        val reference = WeighInGuard.reference(daily(121.7, 121.7, 122.4), now)
        assertTrue(WeighInGuard.needsCheck(68.4, reference))
        assertTrue(WeighInGuard.needsCheck(127.0, reference))
    }

    @Test
    fun theAllowanceNeverDropsBelowThreeKilograms() {
        assertEquals(3.0, WeighInGuard.allowanceKg(55.0), 1e-9)
        assertEquals(3.6, WeighInGuard.allowanceKg(120.0), 1e-9)
        val light = WeighInGuard.reference(daily(55.0, 55.2), now)
        assertFalse(WeighInGuard.needsCheck(57.9, light))
        assertTrue(WeighInGuard.needsCheck(58.3, light))
    }
}
