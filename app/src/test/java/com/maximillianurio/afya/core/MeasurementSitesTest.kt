package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The site catalogue is what the backup and the database key rows against, so the keys are part of
 * the stored format: a renamed key silently strands every measurement a user already took.
 */
class MeasurementSitesTest {

    @Test
    fun sheetHasElevenRowsAndSixteenFields() {
        assertEquals(11, MeasurementSites.rowCount)
        assertEquals(16, MeasurementSites.all.size)
        assertEquals(
            listOf(
                "neck", "chest", "waist", "navel", "hip", "buttock",
                "arm_l", "arm_r", "forearm_l", "forearm_r", "wrist_l", "wrist_r",
                "thigh_l", "thigh_r", "calf_l", "calf_r",
            ),
            MeasurementSites.all.map { it.key },
        )
    }

    @Test
    fun bilateralSitesPrintTheirSide() {
        assertEquals("Waist", MeasurementSites.site("waist")!!.fieldLabel)
        assertEquals("Left upper arm", MeasurementSites.site("arm_l")!!.fieldLabel)
        assertEquals("Right upper arm", MeasurementSites.site("arm_r")!!.fieldLabel)
        assertEquals(Side.Left, MeasurementSites.site("calf_l")!!.side)
        assertEquals(Side.None, MeasurementSites.site("hip")!!.side)
    }

    @Test
    fun unknownKeysAreRejectedRatherThanStored() {
        assertTrue(MeasurementSites.keyIsValid("navel"))
        assertFalse(MeasurementSites.keyIsValid("ankle"))
        assertFalse(MeasurementSites.keyIsValid(""))
        assertNull(MeasurementSites.site("ankle"))
    }

    @Test
    fun plausibleRangeSpansAWristToAChest() {
        assertTrue(MeasurementSites.plausible(3.0))
        assertTrue(MeasurementSites.plausible(300.0))
        assertFalse(MeasurementSites.plausible(2.9))
        assertFalse(MeasurementSites.plausible(300.1))
        assertFalse(MeasurementSites.plausible(0.0))
        assertFalse(MeasurementSites.plausible(-12.0))
        assertFalse(MeasurementSites.plausible(Double.NaN))
    }

    @Test
    fun lengthsRoundTripThroughInches() {
        assertEquals("91.5" to "cm", MeasurementSites.lengthText(91.5, false))
        // 91.5 cm / 2.54 = 36.02 in
        assertEquals("36.0" to "in", MeasurementSites.lengthText(91.5, true))
        assertEquals(91.44, MeasurementSites.toCentimetres(36.0, true), 0.0)
        assertEquals(36.0, MeasurementSites.toCentimetres(36.0, false), 0.0)
        // A tape entered in inches and stored must read back as the same measurement.
        assertEquals(91.44, MeasurementSites.toCentimetres(36.0, true), 0.001)
    }
}
