package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report is what the sheet prints, so these check the printed strings rather than the
 * arithmetic: the same anchor case as [BodyCompositionTest], read the way the weigh-in screen will
 * read it.
 */
class ReportTest {

    private val anchor = BodyComposition.compute(73.8, 178.0, 34, Sex.MALE)!!

    private fun rows(
        metrics: BodyMetrics = anchor,
        weightKg: Double = 73.8,
        ageYears: Int = 34,
        sex: Sex = Sex.MALE,
        unit: WeightUnit = WeightUnit.KG,
        previous: BodyMetrics? = null,
    ) = Report.rows(metrics, weightKg, ageYears, sex, unit, previous)

    private fun row(name: String, source: List<ReportRow> = rows()) =
        source.single { it.name == name }

    @Test
    fun theScheduleIsNineteenRowsAndEveryNameIsUsedOnce() {
        val all = rows()
        assertEquals(19, all.size)
        assertEquals(19, all.map { it.name }.distinct().size)
        all.forEach { row ->
            assertTrue("${row.name} needs a title", row.title.isNotBlank())
            assertTrue("${row.name} needs a figure", row.valueText.isNotBlank())
            assertTrue("${row.name} needs a reference", row.reference.isNotBlank())
            assertTrue("${row.name} needs an explanation", row.explainer.length > 30)
            assertTrue("${row.name} bar sits on its axis", row.position in 0f..1f)
        }
    }

    @Test
    fun printedFiguresMatchTheHandComputedAnchor() {
        assertEquals("23.3", row("bmi").valueText)
        assertEquals("", row("bmi").unit)
        assertEquals("19.6", row("fat").valueText)
        assertEquals("%", row("fat").unit)
        assertEquals("14.4", row("fat_mass").valueText)
        assertEquals("kg", row("fat_mass").unit)
        assertEquals("59.4", row("lean").valueText)
        assertEquals("31.5", row("muscle_mass").valueText)
        assertEquals("42.6", row("muscle").valueText)
        assertEquals("25.2", row("skeletal_mass").valueText)
        assertEquals("34.1", row("skeletal").valueText)
        assertEquals("58.9", row("water").valueText)
        assertEquals("43.4", row("water_mass").valueText)
        assertEquals("16.6", row("protein").valueText)
        assertEquals("12.3", row("protein_mass").valueText)
        assertEquals("3.1", row("bone").valueText)
        assertEquals("10", row("visceral").valueText)
        assertEquals("cm²", row("visceral").unit)
        assertEquals("1686", row("bmr").valueText)
        assertEquals("kcal", row("bmr").unit)
        assertEquals("35", row("metabolic_age").valueText)
        assertEquals("7.3", row("obesity_degree").valueText)
        assertEquals("68.8", row("ideal_weight").valueText)
        assertEquals("58.6–78.9", row("healthy_range").valueText)
    }

    @Test
    fun eachRowCarriesTheWordForItsBandNotOnlyAColour() {
        assertEquals("In range", row("bmi").bandWord)
        assertEquals("18.5–24.9", row("bmi").reference)
        assertEquals("Above range", row("fat").bandWord)
        assertEquals("8–19 %", row("fat").reference)
        assertEquals("In range", row("muscle").bandWord)
        assertEquals("42–47.5 %", row("muscle").reference)
        assertEquals("In range", row("bone").bandWord)
        assertEquals("2.9–4.2 kg", row("bone").reference)
        // A fat-mass row has no band of its own: it inherits the verdict of the percentage above it.
        assertEquals(row("fat").band, row("fat_mass").band)
        assertEquals(row("muscle").band, row("muscle_mass").band)
        assertEquals(row("skeletal").band, row("skeletal_mass").band)
        assertEquals(row("water").band, row("water_mass").band)
        // The rows that are arithmetic, not reference data, say so instead of guessing.
        assertEquals(Band.Unknown, row("lean").band)
        assertEquals("weight − fat mass", row("lean").reference)
        assertEquals(Band.Unknown, row("bmr").band)
        assertEquals("Mifflin-St Jeor", row("bmr").reference)
        assertEquals(Band.Unknown, row("ideal_weight").band)
        assertEquals("BMI 21.7", row("ideal_weight").reference)
    }

    @Test
    fun bodyAgeRowIsReadAgainstTheCalendarAge() {
        val row = row("metabolic_age")
        assertEquals("calendar age 34", row.reference)
        assertEquals(Band.Above, row.band)
        val younger = Report.rows(
            metrics = BodyComposition.compute(68.0, 178.0, 34, Sex.MALE)!!,
            weightKg = 68.0,
            ageYears = 34,
            sex = Sex.MALE,
            unit = WeightUnit.KG,
        ).single { it.name == "metabolic_age" }
        assertEquals("calendar age 34", younger.reference)
        assertEquals(Band.Healthy, younger.band)
    }

    @Test
    fun healthyWeightRowJudgesTheLoadOnThePlatform() {
        assertEquals(Band.Healthy, row("healthy_range").band)
        val obese = rows(metrics = BodyComposition.compute(100.0, 178.0, 34, Sex.MALE)!!, weightKg = 100.0)
        assertEquals(Band.Unknown, row("healthy_range", obese).band)
    }

    @Test
    fun massRowsPrintInTheUnitTheProfileAskedFor() {
        val pounds = rows(unit = WeightUnit.LB)
        // The stored 14.4 kg of fat mass is 31.75 lb.
        assertEquals("31.7", row("fat_mass", pounds).valueText)
        assertEquals("lb", row("fat_mass", pounds).unit)
        assertEquals("131.0", row("lean", pounds).valueText)
        // Percentages do not move with the unit.
        assertEquals(row("fat").valueText, row("fat", pounds).valueText)
        val jin = rows(unit = WeightUnit.JIN)
        assertEquals("28.8", row("fat_mass", jin).valueText)
        assertEquals("jin", row("fat_mass", jin).unit)
    }

    @Test
    fun withoutAPreviousWeighInNothingClaimsADirection() {
        rows().forEach { row ->
            assertEquals(row.name, Trend.First, row.trend)
            assertEquals(row.name, "", row.changeText)
            assertFalse(row.hasChange)
        }
    }

    @Test
    fun directionAndChangeComeFromTheWeighInBeforeThisOne() {
        val heavier = BodyComposition.compute(75.3, 178.0, 34, Sex.MALE)!!
        val lighter = BodyComposition.compute(72.1, 178.0, 34, Sex.MALE)!!

        val down = row("bmi", rows(previous = heavier))
        assertEquals(Trend.Down, down.trend)
        assertEquals("−0.5", down.changeText)
        assertEquals("▼", down.trend.arrow)

        val up = row("bmi", rows(previous = lighter))
        assertEquals(Trend.Up, up.trend)
        assertEquals("+0.5", up.changeText)

        val mass = row("fat_mass", rows(previous = heavier))
        assertEquals(Trend.Down, mass.trend)
        // 15.2 kg of fat mass at 75.3 kg, 14.4 kg at 73.8 kg.
        assertEquals("−0.8 kg", mass.changeText)

        // Mass rows and percentage rows agree about which way the body moved.
        assertEquals(mass.trend, row("fat", rows(previous = heavier)).trend)
        assertEquals(Trend.Down, row("muscle_mass", rows(previous = heavier)).trend)
        assertEquals(Trend.Down, row("water_mass", rows(previous = heavier)).trend)
        assertEquals(Trend.Up, row("obesity_degree", rows(previous = lighter)).trend)
    }

    @Test
    fun aChangeBelowThePrintedPrecisionIsReportedAsNoChange() {
        val same = BodyComposition.compute(73.8, 178.0, 34, Sex.MALE)!!
        rows(previous = same).filter { it.name != "healthy_range" }.forEach { row ->
            assertEquals(row.name, Trend.Flat, row.trend)
            assertEquals(row.name, "no change", row.changeText)
            assertFalse(row.hasChange)
        }
        // The healthy span for a stable height never moves, so it is not printed as a change.
        assertEquals(Trend.First, row("healthy_range", rows(previous = same)).trend)
        // 20 g moves BMI by 0.01 and bone mineral by nothing at all, so neither is printed as a
        // change — while fat mass, which stores 14.5 against 14.4, is allowed to move.
        val barely = BodyComposition.compute(73.82, 178.0, 34, Sex.MALE)!!
        val against = rows(previous = barely)
        assertEquals(Trend.Flat, row("bmi", against).trend)
        assertEquals(Trend.Flat, row("bone", against).trend)
        assertEquals(Trend.Down, row("fat_mass", against).trend)
    }

    @Test
    fun explainersHedgeTheEstimateWithoutHedgingTheNumber() {
        val notes = rows().map { it.explainer }
        notes.forEach { assertTrue(it.contains('.')) }
        assertTrue(notes.any { it.contains("Deurenberg") })
        assertTrue(notes.any { it.contains("Mifflin-St Jeor") })
        // Nothing here pretends to have measured anything with impedance.
        assertFalse(notes.any { it.contains("impedance") || it.contains("measured") })
    }
}
