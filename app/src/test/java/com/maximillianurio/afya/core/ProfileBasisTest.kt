package com.maximillianurio.afya.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A composition change is only printed between two snapshots read through the same profile. The
 * regression case is the one seen on the test device: a +2.0 kg day printed "BMI −7.5" because the
 * reading before it was filed under the old 178 cm / 34-year placeholder profile.
 */
class ProfileBasisTest {

    private fun metrics(kg: Double, cm: Double = 200.0, age: Int = 27, sex: Sex = Sex.MALE) =
        BodyComposition.compute(kg, cm, age, sex)!!

    @Test
    fun theSameProfileOnDifferentWeightsIsComparable() {
        listOf(60.0, 73.8, 121.7, 123.7, 180.0).forEach { before ->
            listOf(before - 2.3, before, before + 0.1, before + 4.0).forEach { after ->
                assertTrue(
                    "$before → $after",
                    Report.sameBasis(metrics(after), after, metrics(before), before),
                )
            }
        }
    }

    @Test
    fun theDeviceRegressionIsNotCompared() {
        val before = metrics(121.7, cm = 178.0, age = 34)
        val after = metrics(123.7, cm = 200.0, age = 27)
        assertFalse(Report.sameBasis(after, 123.7, before, 121.7))
        assertNull(Report.comparable(after, 123.7, before, 121.7))
    }

    @Test
    fun oneCentimetreOrOneYearOrTheOtherSexBreaksTheBasis() {
        val base = metrics(90.0)
        assertFalse(Report.sameBasis(metrics(90.0, cm = 201.0), 90.0, base, 90.0))
        assertFalse(Report.sameBasis(metrics(90.0, cm = 199.0), 90.0, base, 90.0))
        assertFalse(Report.sameBasis(metrics(90.0, age = 28), 90.0, base, 90.0))
        assertFalse(Report.sameBasis(metrics(90.0, sex = Sex.FEMALE), 90.0, base, 90.0))
    }

    @Test
    fun comparableHandsBackTheOlderFiguresWhenTheyMatch() {
        val before = metrics(121.7)
        assertNotNull(Report.comparable(metrics(123.7), 123.7, before, 121.7))
        assertNull(Report.comparable(null, 123.7, before, 121.7))
        assertNull(Report.comparable(metrics(123.7), 123.7, null, 121.7))
    }

    @Test
    fun aRebasedPreviousPrintsNoChangeAtAll() {
        val rows = Report.rows(
            metrics = metrics(123.7),
            weightKg = 123.7,
            ageYears = 27,
            sex = Sex.MALE,
            unit = WeightUnit.KG,
            previous = Report.comparable(metrics(123.7), 123.7, metrics(121.7, 178.0, 34), 121.7),
        )
        rows.forEach { row -> assertFalse("${row.name} printed ${row.changeText}", row.hasChange) }
    }
}
