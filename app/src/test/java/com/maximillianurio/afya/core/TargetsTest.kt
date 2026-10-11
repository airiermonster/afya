package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The plan numbers are printed as guidance, so they have to be arithmetic anyone can follow: each
 * expectation below is worked by hand from the formula named in the comment.
 */
class TargetsTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_760_000_000_000L

    @Test
    fun waterIsFatFreeMassTimesThirtyThreePlusTheAllowances() {
        // 59.4 kg of lean mass × 33 = 1960.2 → 1960, + 250 (light) + 150 (mild) = 2360.
        val w = Targets.water(59.4, ActivityLevel.LIGHT, Heat.MILD)
        assertEquals(1960, w.baseMl)
        assertEquals(250, w.activityMl)
        assertEquals(150, w.heatMl)
        assertEquals(2360, w.millilitres)
    }

    @Test
    fun waterStaysInsideTheWorkingBounds() {
        // A very small lean mass still gets the floor rather than a thimble.
        assertEquals(1_000, Targets.water(10.0, ActivityLevel.SEDENTARY, Heat.COOL).millilitres)
        // 200 kg of lean mass would ask for 8000 ml, which no guideline supports drinking.
        assertEquals(6_000, Targets.water(200.0, ActivityLevel.ATHLETE, Heat.HOT).millilitres)
    }

    @Test
    fun aWrittenGoalMovesAtHalfAKiloAWeek() {
        // 1686 × 1.55 = 2613.3 → 2613. Half a kilo is 7 700/7 × 0.5 = 550 kcal a day.
        val e = Targets.energy(1686.0, ActivityLevel.MODERATE, 73.8, 70.0, Sex.MALE)
        assertEquals(2613, e.maintenanceKcal)
        assertEquals(Targets.Direction.LOSE, e.direction)
        assertEquals(2063, e.targetKcal)
    }

    @Test
    fun energyTargetNeverFallsBelowTheSafeFloor() {
        // 1200 × 1.2 = 1440; a 550 kcal deficit would land at 890, so the floor holds.
        val e = Targets.energy(1200.0, ActivityLevel.SEDENTARY, 70.0, 60.0, Sex.FEMALE)
        assertEquals(1440, e.maintenanceKcal)
        assertEquals(Targets.Direction.LOSE, e.direction)
        assertEquals(1500, e.targetKcal)
    }

    @Test
    fun goalsThatAreAlreadyMetOrNotSetHoldAtMaintenance() {
        val noGoal = Targets.energy(1686.0, ActivityLevel.LIGHT, 73.8, 0.0, Sex.MALE)
        assertEquals(Targets.Direction.MAINTAIN, noGoal.direction)
        assertEquals(noGoal.maintenanceKcal, noGoal.targetKcal)

        val almost = Targets.energy(1686.0, ActivityLevel.LIGHT, 73.8, 73.5, Sex.MALE)
        assertEquals(Targets.Direction.MAINTAIN, almost.direction)

        val gaining = Targets.energy(1400.0, ActivityLevel.LIGHT, 60.0, 65.0, Sex.FEMALE)
        // 1400 × 1.375 = 1925, +550.
        assertEquals(Targets.Direction.GAIN, gaining.direction)
        assertEquals(2475, gaining.targetKcal)
    }

    @Test
    fun anIntentSteersThePlanWhenNoGoalWeightHasBeenWritten() {
        val intent = IntentProfile(aim = Aim.Losing, pace = Pace.Gentle)
        val e = Targets.energy(1686.0, ActivityLevel.MODERATE, 73.8, 0.0, Sex.MALE, intent)
        // A quarter of a kilo is 275 kcal a day, and the person, not the app, chose the rate.
        assertEquals(Targets.Direction.LOSE, e.direction)
        assertEquals(2338, e.targetKcal)

        // A written goal still outranks the stated aim.
        val withGoal = Targets.energy(1686.0, ActivityLevel.MODERATE, 73.8, 80.0, Sex.MALE, intent)
        assertEquals(Targets.Direction.GAIN, withGoal.direction)
    }

    @Test
    fun macrosAndMealsAreSharesOfTheSameTarget() {
        val e = Targets.energy(1686.0, ActivityLevel.MODERATE, 73.8, 70.0, Sex.MALE)
        // 50 / 25 / 25 of 2063 kcal at 4, 4 and 9 kcal per gram.
        assertEquals(257.9, e.carbsGrams, 0.05)
        assertEquals(128.9, e.proteinGrams, 0.05)
        assertEquals(57.3, e.fatGrams, 0.05)
        assertEquals(
            listOf("Breakfast", "Lunch", "Dinner", "Snack"),
            e.mealsKcal.map { it.first },
        )
        val meals = e.mealsKcal.map { it.second }
        assertTrue(meals.all { it > 0 })
        // The split is rounded per meal, so it lands within a kcal of the day it divides.
        assertEquals(e.targetKcal.toDouble(), meals.sum().toDouble(), 2.0)
    }

    @Test
    fun bodyTypeReadsTheNineCellGrid() {
        // Low BMI with fat above the male ceiling is the "skinny fat" cell.
        assertEquals(Targets.BodyType.HIDDEN_OBESITY, Targets.bodyType(17.5, 28.0, Sex.MALE))
        assertEquals(Targets.BodyType.EMACIATED, Targets.bodyType(17.5, 12.0, Sex.MALE))
        assertEquals(Targets.BodyType.HEALTHY, Targets.bodyType(22.0, 22.0, Sex.MALE))
        assertEquals(Targets.BodyType.OBESE, Targets.bodyType(32.0, 30.0, Sex.MALE))
        // The same body reads differently by sex, because the fat columns do: 22 % is inside the
        // male range and below the female one.
        assertEquals(Targets.BodyType.MUSCULAR_TYPE, Targets.bodyType(22.0, 22.0, Sex.FEMALE))
        assertEquals(Targets.BodyType.HEALTHY, Targets.bodyType(22.0, 32.0, Sex.FEMALE))
    }

    @Test
    fun projectionExtrapolatesTheRateTheReadingsShow() {
        // 78.0 → 73.8 kg over three weeks is −1.4 kg a week; 3.8 kg remain.
        val entries = listOf(Targets.ReadingsDbEntry(now - 21 * day, 78_000.0))
        val p = Targets.project(entries, 73.8, 70.0, now)!!
        assertEquals(-1.4, p.weeklyRateKg, 0.02)
        assertEquals(3, p.weeks)
        assertEquals(3.8, p.remainingKg, 0.02)
        assertEquals(now + 21 * day, p.etaDays)
        // Faster than 1 kg a week is not a rate a plan should promise.
        assertFalse(p.onTrack)

        // −2.6 kg over four weeks = −0.65 a week.
        val slow = Targets.project(
            listOf(Targets.ReadingsDbEntry(now - 28 * day, 76_400.0)),
            73.8, 70.0, now,
        )!!
        assertEquals(-0.65, slow.weeklyRateKg, 0.02)
        assertTrue(slow.onTrack)
        assertEquals(6, slow.weeks)

        // A goal above the current weight is closed by gaining, not by losing.
        val growing = Targets.project(
            listOf(Targets.ReadingsDbEntry(now - 21 * day, 57_000.0)),
            60.0, 65.0, now,
        )!!
        assertEquals(1.0, growing.weeklyRateKg, 0.02)
        assertEquals(5, growing.weeks)
        assertEquals(now + 35 * day, growing.etaDays)
        assertTrue(growing.onTrack)
    }

    @Test
    fun projectionRefusesToGuessWithoutMovementTowardTheGoal() {
        assertNull(Targets.project(emptyList(), 73.8, 70.0, now))
        // No goal set.
        assertNull(Targets.project(listOf(Targets.ReadingsDbEntry(now, 73_800.0)), 73.8, 0.0, now))
        // Already at the goal.
        assertNull(Targets.project(listOf(Targets.ReadingsDbEntry(now, 73_800.0)), 73.8, 73.75, now))
        // One reading today gives no rate at all.
        assertNull(Targets.project(listOf(Targets.ReadingsDbEntry(now, 73_800.0)), 73.8, 70.0, now))
        // Moving away from the goal has no date attached to it.
        assertNull(
            Targets.project(
                listOf(Targets.ReadingsDbEntry(now - 21 * day, 72_000.0)),
                73.8, 70.0, now,
            ),
        )
    }

    @Test
    fun drinkKindsAllCarryASensibleDefaultGlass() {
        assertEquals(7, DrinkKind.entries.size)
        assertEquals(250, DrinkKind.WATER.defaultMl)
        assertEquals(330, DrinkKind.SODA.defaultMl)
        assertTrue(DrinkKind.entries.all { it.defaultMl in 100..1000 })
        assertTrue(DrinkKind.entries.all { it.label.isNotEmpty() })
    }
}
