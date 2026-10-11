package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every expectation here is computed by hand from the equation named in `docs/HANDOFF.md`, so the
 * code has to agree with arithmetic rather than with itself. The worked example the handoff
 * documents — 73.8 kg / 178 cm / 34 years / male — is the anchor case.
 */
class BodyCompositionTest {

    private fun anchor(sex: Sex = Sex.MALE) = BodyComposition.compute(73.8, 178.0, 34, sex)!!

    private val woman = BodyComposition.compute(60.0, 165.0, 28, Sex.FEMALE)!!

    @Test
    fun anchorCaseMatchesTheHandoffFigures() {
        val m = anchor()
        // BMI 73.8 / 1.78² = 23.291…
        assertEquals(23.3, m.bmi, 0.0)
        // Deurenberg: 1.20·23.2919 + 0.23·34 − 10.8 − 5.4 = 19.570…
        assertEquals(19.6, m.fatPercent, 0.0)
        assertEquals(14.4, m.fatMassKg, 0.0)
        assertEquals(59.4, m.leanMassKg, 0.0)
        // Hall: 59.3551 × 0.732 = 43.448 l, and 43.448 / 73.8 = 58.87 %
        assertEquals(43.4, m.waterKg, 0.0)
        assertEquals(58.9, m.waterPercent, 0.0)
        // The figure the handoff verified against the old app: muscle is 53 % of fat-free mass.
        assertEquals(31.5, m.muscleKg, 0.0)
        assertEquals(42.6, m.musclePercent, 0.0)
    }

    @Test
    fun addedMetricsFollowTheirPublishedFractions() {
        val m = anchor()
        assertEquals(25.2, m.skeletalMuscleKg, 0.0)      // 31.458 × 0.80
        assertEquals(34.1, m.skeletalMusclePercent, 0.0) // 25.166 / 73.8
        assertEquals(12.3, m.proteinKg, 0.0)             // 59.3551 × 0.207
        assertEquals(16.6, m.proteinPercent, 0.0)
        assertEquals(3.09, m.boneMassKg, 0.0)            // 59.3551 × 0.052
        assertEquals(68.8, m.idealWeightKg, 0.0)         // 21.7 × 1.78²
        assertEquals(7.3, m.obesityDegree, 0.0)          // (73.8 − 68.754) / 68.754
        assertEquals(58.6 to 78.9, m.healthyRangeKg)     // 18.5 and 24.9 × 1.78²
    }

    @Test
    fun massRowsAreFractionsOfTheSameLeanMass() {
        // Guards the regression where muscle was printed as fat-free mass minus mineral, which put
        // every muscle row far above its own band.
        val m = anchor()
        assertTrue("muscle is not nearly all of lean mass", m.muscleKg < m.leanMassKg * 0.6)
        assertTrue("skeletal muscle is part of muscle", m.skeletalMuscleKg < m.muscleKg)
        assertTrue("the muscle band is a percentage of weight", m.musclePercent in 30.0..55.0)
        assertEquals(Band.Healthy, BodyComposition.label("muscle", m.musclePercent, Sex.MALE))
    }

    @Test
    fun restingEnergyUsesMifflinStJeorForBothSexes() {
        // Male: 10·73.8 + 6.25·178 − 5·34 + 5 = 1685.5
        assertEquals(1686.0, anchor().bmrKcal, 0.0)
        // Female: 10·60 + 6.25·165 − 5·28 − 161 = 1330.25
        assertEquals(1330.0, woman.bmrKcal, 0.0)
    }

    @Test
    fun bodyAgeMovesWithBmiAndStaysInsideTheCalendarAge() {
        // 34 + (23.2919 − 22) × 0.75 = 34.97
        assertEquals(35, anchor().metabolicAge)
        // An age outside the range the equations are fitted for is pulled back inside, not rejected.
        assertEquals(12, BodyComposition.compute(50.0, 178.0, 7, Sex.MALE)!!.metabolicAge)
        assertEquals(100, BodyComposition.compute(150.0, 178.0, 140, Sex.MALE)!!.metabolicAge)
    }

    @Test
    fun visceralAreaIsSexSpecificAndFlooredAtTen() {
        // Male: −11.060 + 0.180·23.2919 + 0.232·34 = 1.02, so the floor is what prints.
        assertEquals(10.0, anchor().visceralCm2, 0.0)
        // Female: −10.952 + 0.280·22.0386 + 0.154·28 = −0.47, floored too.
        assertEquals(10.0, woman.visceralCm2, 0.0)
        // Old enough and heavy enough for the model itself to clear the floor.
        // −11.060 + 0.180·35.9184 + 0.232·75 = 12.81
        assertEquals(13.0, BodyComposition.compute(110.0, 175.0, 75, Sex.MALE)!!.visceralCm2, 0.0)
    }

    @Test
    fun bodyFatClampsInsteadOfGoingNegative() {
        // 1.20·10.24 + 0.23·12 − 10.8 − 5.4 = −1.15, which is not a body-fat percentage.
        val tallBoy = BodyComposition.compute(64.0, 250.0, 12, Sex.MALE)!!
        assertEquals(10.2, tallBoy.bmi, 0.0)
        assertEquals(3.0, tallBoy.fatPercent, 0.0)
        // An extreme load clamps at the top instead.
        assertEquals(60.0, BodyComposition.compute(200.0, 130.0, 100, Sex.FEMALE)!!.fatPercent, 0.0)
    }

    @Test
    fun rejectsLoadsThatAreNotAPerson() {
        assertNull(BodyComposition.compute(1.9, 178.0, 34, Sex.MALE))
        assertNull(BodyComposition.compute(400.1, 178.0, 34, Sex.MALE))
        assertNull(BodyComposition.compute(73.8, 79.0, 34, Sex.MALE))
        assertNull(BodyComposition.compute(73.8, 250.1, 34, Sex.MALE))
        assertFalse(BodyComposition.bodyPlausible(19_999.9))
        assertTrue(BodyComposition.bodyPlausible(20_000.0))
        assertTrue(BodyComposition.bodyPlausible(250_000.0))
        assertFalse(BodyComposition.bodyPlausible(250_000.1))
    }

    @Test
    fun bandsAreSexSpecific() {
        assertEquals(8.0 to 19.0, BodyComposition.band("fat", Sex.MALE))
        assertEquals(21.0 to 32.0, BodyComposition.band("fat", Sex.FEMALE))
        assertEquals(42.0 to 47.5, BodyComposition.band("muscle", Sex.MALE))
        assertEquals(36.0 to 42.0, BodyComposition.band("muscle", Sex.FEMALE))
        assertEquals(16.0 to 20.0, BodyComposition.band("protein", Sex.MALE))
        assertEquals(14.0 to 18.0, BodyComposition.band("protein", Sex.FEMALE))
        assertEquals(2.9 to 4.2, BodyComposition.band("bone", Sex.MALE))
        assertEquals(2.2 to 3.3, BodyComposition.band("bone", Sex.FEMALE))
        assertEquals(18.5 to 24.9, BodyComposition.band("bmi", Sex.MALE))
        assertNull(BodyComposition.band("nonsense", Sex.MALE))
    }

    /**
     * The band edges, printed exactly as [BodyComposition.label] reads them. The overshoot either
     * side is 35 % of the band's own width, so a figure a little outside the range says "below"
     * rather than sounding like a diagnosis.
     */
    @Test
    fun bandEdgesSitWhereTheOvershootRuleSays() {
        val sex = Sex.MALE
        // bmi band 18.5–24.9, spread 6.4 → Low below 16.26, High up to 32.58.
        assertEquals(Band.Low, BodyComposition.label("bmi", 16.0, sex))
        assertEquals(Band.Below, BodyComposition.label("bmi", 16.3, sex))
        assertEquals(Band.Healthy, BodyComposition.label("bmi", 18.5, sex))
        assertEquals(Band.Healthy, BodyComposition.label("bmi", 24.9, sex))
        assertEquals(Band.Above, BodyComposition.label("bmi", 25.0, sex))
        assertEquals(Band.Above, BodyComposition.label("bmi", 27.1, sex))
        assertEquals(Band.High, BodyComposition.label("bmi", 27.2, sex))
        assertEquals(Band.High, BodyComposition.label("bmi", 32.5, sex))
        assertEquals(Band.Obese, BodyComposition.label("bmi", 32.6, sex))
        // A metric with no accepted band has no verdict to print.
        assertEquals(Band.Unknown, BodyComposition.label("nonsense", 5.0, sex))
    }

    @Test
    fun everyAnchorRowSitsWhereAHealthyThirtyFourYearOldShouldBe() {
        val m = anchor()
        assertEquals(Band.Healthy, BodyComposition.label("bmi", m.bmi, Sex.MALE))
        assertEquals(Band.Above, BodyComposition.label("fat", m.fatPercent, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("water", m.waterPercent, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("muscle", m.musclePercent, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("skeletal", m.skeletalMusclePercent, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("protein", m.proteinPercent, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("bone", m.boneMassKg, Sex.MALE))
        assertEquals(Band.Healthy, BodyComposition.label("obesity_degree", m.obesityDegree, Sex.MALE))
    }

    @Test
    fun sameCaseForAWomanReadsWithHerBands() {
        assertEquals(22.0, woman.bmi, 0.0)
        assertEquals(27.5, woman.fatPercent, 0.0)
        assertEquals(53.1, woman.waterPercent, 0.0)
        assertEquals(38.4, woman.musclePercent, 0.0)
        assertEquals(30.7, woman.skeletalMusclePercent, 0.0)
        assertEquals(15.0, woman.proteinPercent, 0.0)
        assertEquals(2.26, woman.boneMassKg, 0.0)
        assertEquals(Band.Healthy, BodyComposition.label("fat", woman.fatPercent, Sex.FEMALE))
        assertEquals(Band.Healthy, BodyComposition.label("muscle", woman.musclePercent, Sex.FEMALE))
        assertEquals(Band.Healthy, BodyComposition.label("protein", woman.proteinPercent, Sex.FEMALE))
        assertEquals(Band.Healthy, BodyComposition.label("bone", woman.boneMassKg, Sex.FEMALE))
    }

    @Test
    fun bodyAgeIsReadAgainstTheCalendarAge() {
        assertEquals(Band.Healthy, BodyComposition.labelMetabolicAge(30, 34))
        assertEquals(Band.Healthy, BodyComposition.labelMetabolicAge(34, 34))
        assertEquals(Band.Above, BodyComposition.labelMetabolicAge(35, 34))
        assertEquals(Band.Above, BodyComposition.labelMetabolicAge(37, 34))
        assertEquals(Band.High, BodyComposition.labelMetabolicAge(42, 34))
        assertEquals(Band.Obese, BodyComposition.labelMetabolicAge(43, 34))
    }

    @Test
    fun referenceTextPrintsBandEdgesWithoutPadding() {
        assertEquals("18.5–24.9", BodyComposition.referenceText("bmi", Sex.MALE))
        assertEquals("8–19", BodyComposition.referenceText("fat", Sex.MALE))
        assertEquals("42–47.5", BodyComposition.referenceText("muscle", Sex.MALE))
        assertEquals("0–10", BodyComposition.referenceText("visceral", Sex.MALE))
        assertEquals("21–32", BodyComposition.referenceText("fat", Sex.FEMALE))
        assertNull(BodyComposition.referenceText("nonsense", Sex.MALE))
    }

    @Test
    fun positionSitsOnTheDisplayAxisAndClamps() {
        // bmi axis 14–40: 23.3 is 35 % along it.
        assertEquals(0.35f, BodyComposition.position("bmi", 23.3, Sex.MALE), 0.01f)
        assertEquals(0f, BodyComposition.position("bmi", 5.0, Sex.MALE), 0.0f)
        assertEquals(1f, BodyComposition.position("bmi", 90.0, Sex.MALE), 0.0f)
        // A metric with no axis lands mid-bar rather than at an edge.
        assertEquals(0.5f, BodyComposition.position("nonsense", 3.0, Sex.MALE), 0f)
    }

    @Test
    fun bmiCategoryUsesTheFourStandardCuts() {
        assertEquals("Underweight", BodyComposition.compute(50.0, 178.0, 34, Sex.MALE)!!.bmiCategory)
        assertEquals("Healthy", anchor().bmiCategory)
        assertEquals("Overweight", BodyComposition.compute(85.0, 178.0, 34, Sex.MALE)!!.bmiCategory)
        assertEquals("Obese", BodyComposition.compute(100.0, 178.0, 34, Sex.MALE)!!.bmiCategory)
    }
}
