package com.maximillianurio.afya.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maximillianurio.afya.core.ActivityLevel
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.DrinkKind
import com.maximillianurio.afya.core.Heat
import com.maximillianurio.afya.core.MeasurementSites
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backup is the only way history leaves the phone, so it has to survive a round trip through
 * itself: export, delete the database, import into the empty file, and get the same sheet back.
 * Runs on the device because [Backup] reads and writes real preferences and a real database.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val timestamp = 1_760_000_000_000L
    private val grams = 73_800.0

    /** Both stores are per-app singletons, so every test starts from an emptied phone. */
    private fun freshDb(): ReadingsDb {
        context.deleteDatabase("readings.db")
        context.deleteSharedPreferences("afya_scale")
        return ReadingsDb(context)
    }

    private fun seed(db: ReadingsDb, store: LocalStore) {
        store.boundMac = "80:F1:16:90:6A:1E"
        store.setKey("80:F1:16:90:6A:1E", "1A2B3C")
        store.sex = Sex.FEMALE
        store.ageYears = 28
        store.heightCm = 165.0
        store.targetKg = 58.0
        store.unit = WeightUnit.LB
        store.activity = ActivityLevel.MODERATE
        store.heat = Heat.HOT
        store.waterGoalMl = 3_100
        store.calorieGoalKcal = null
        store.lengthInches = true
        store.themeMode = 0

        val metrics = BodyComposition.compute(grams / 1000.0, 165.0, 28, Sex.FEMALE)!!
        db.insert(db.entryOf(timestamp, "80:F1:16:90:6A:1E", grams, 600.0, metrics, "0xC0", 2577))
        db.insertMeasurement("neck", 30.0, timestamp)
        db.insertMeasurement("chest", 91.5, timestamp)
        db.insertMeasurement("waist", 74.0, timestamp + 1)
        db.insertMeasurement("thigh_l", 55.0, timestamp + 2)
        db.insertDrink(DrinkKind.WATER.name, 250, timestamp)
        db.insertDrink(DrinkKind.COFFEE.name, 200, timestamp + 60_000)
    }

    @Test
    fun exportThenImportIntoAnEmptyDatabaseRestoresEverything() {
        val db = freshDb()
        val store = LocalStore(context)
        seed(db, store)
        val json = Backup.exportJson(store, db)

        val wiped = freshDb()
        val restored = LocalStore(context)
        val summary = Backup.importJson(restored, wiped, json)

        assertEquals(1, summary.readings)
        assertEquals(0, summary.skipped)
        assertEquals(4, summary.measurements)
        assertEquals(2, summary.drinks)

        val row = wiped.all().single()
        assertEquals(timestamp, row.timestampMs)
        assertEquals(grams, row.grams, 0.0)
        assertEquals("80:F1:16:90:6A:1E", row.mac)
        assertEquals(600.0, row.impedanceOhms!!, 0.0)
        // The composition snapshot has to come back field for field, not be recomputed.
        val expected = BodyComposition.compute(grams / 1000.0, 165.0, 28, Sex.FEMALE)!!
        assertEquals(expected.bmi, row.bmi!!, 0.0)
        assertEquals(expected.fatPercent, row.fatPercent!!, 0.0)
        assertEquals(expected.fatMassKg, row.fatMassKg!!, 0.0)
        assertEquals(expected.muscleKg, row.muscleKg!!, 0.0)
        assertEquals(expected.musclePercent, row.musclePercent!!, 0.0)
        assertEquals(expected.skeletalMuscleKg, row.skeletalKg!!, 0.0)
        assertEquals(expected.skeletalMusclePercent, row.skeletalPercent!!, 0.0)
        assertEquals(expected.waterKg, row.waterKg!!, 0.0)
        assertEquals(expected.waterPercent, row.waterPercent!!, 0.0)
        assertEquals(expected.proteinKg, row.proteinKg!!, 0.0)
        assertEquals(expected.proteinPercent, row.proteinPercent!!, 0.0)
        assertEquals(expected.boneMassKg, row.boneKg!!, 0.0)
        assertEquals(expected.visceralCm2, row.visceralCm2!!, 0.0)
        assertEquals(expected.bmrKcal, row.bmrKcal!!, 0.0)
        assertEquals(expected.metabolicAge, row.metabolicAge)
        assertEquals(expected.obesityDegree, row.obesityDegree!!, 0.0)
        assertEquals(expected.idealWeightKg, row.idealKg!!, 0.0)
        assertEquals("0xC0", row.frameName)
        assertEquals(2577, row.productId)

        val measures = wiped.latestBySite()
        assertEquals(4, measures.size)
        assertEquals(30.0, measures.getValue("neck").centimetres, 0.0)
        assertEquals(91.5, measures.getValue("chest").centimetres, 0.0)
        assertEquals(55.0, measures.getValue("thigh_l").centimetres, 0.0)
        assertEquals(timestamp, measures.getValue("neck").timestampMs)

        val drinks = wiped.allDrinks()
        assertEquals(2, drinks.size)
        assertEquals(450, wiped.drinkTotalMl(timestamp - 1, timestamp + 86_400_000))

        assertEquals("80:F1:16:90:6A:1E", restored.boundMac)
        assertEquals("1A2B3C", restored.keyFor("80:F1:16:90:6A:1E"))
        assertEquals(Sex.FEMALE, restored.sex)
        assertEquals(28, restored.ageYears)
        assertEquals(165.0, restored.heightCm, 0.0)
        assertEquals(58.0, restored.targetKg, 0.0)
        assertEquals(WeightUnit.LB, restored.unit)
        assertEquals(ActivityLevel.MODERATE, restored.activity)
        assertEquals(Heat.HOT, restored.heat)
        assertEquals(3_100, restored.waterGoalMl)
        // An unset goal stays unset rather than becoming zero, which would read as a goal of none.
        assertNull(restored.calorieGoalKcal)
        assertTrue(restored.lengthInches)
        assertEquals(0, restored.themeMode)
    }

    @Test
    fun importingTheSameFileTwiceDoesNotDuplicateRows() {
        val db = freshDb()
        seed(db, LocalStore(context))
        val json = Backup.exportJson(LocalStore(context), db)

        val target = freshDb()
        Backup.importJson(LocalStore(context), target, json)
        val again = Backup.importJson(LocalStore(context), target, json)

        assertEquals(0, again.readings)
        assertEquals(1, again.skipped)
        assertEquals(0, again.measurements)
        assertEquals(0, again.drinks)
        assertEquals(1, target.count())
        assertEquals(4, target.allMeasurements().size)
        assertEquals(2, target.allDrinks().size)
    }

    /** A backup from a build that measured a site this one does not know must not orphan a row. */
    @Test
    fun unknownMeasurementSiteAndNonPositiveDrinkAreSkipped() {
        val legacy = """
            {
              "app": "afya", "schema": 3, "settings": {},
              "readings": [],
              "measurements": [
                {"ts": $timestamp, "site": "ankle", "cm": 24.0},
                {"ts": $timestamp, "site": "wrist_l", "cm": 16.5}
              ],
              "drinks": [
                {"ts": $timestamp, "kind": "WATER", "ml": 0},
                {"ts": $timestamp, "kind": "TEA", "ml": 150}
              ]
            }
        """.trimIndent()

        val db = freshDb()
        val summary = Backup.importJson(LocalStore(context), db, legacy)

        assertEquals(1, summary.measurements)
        assertEquals(1, summary.drinks)
        assertEquals("wrist_l", db.allMeasurements().single().site)
        assertEquals("TEA", db.allDrinks().single().kind)
        assertTrue(MeasurementSites.keyIsValid("wrist_l"))
        assertTrue(!MeasurementSites.keyIsValid("ankle"))
    }

    /**
     * A schema-2 export from the first Compose builds: no fat-mass, skeletal, protein or measurement
     * arrays, and the old settings keys. It has to import rather than throw.
     */
    @Test
    fun olderSchemaTwoExportStillImports() {
        val legacy = """
            {
              "app": "afya",
              "schema": 2,
              "settings": {"bound_mac": "80:F1:16:90:6A:1E", "sex": "MALE", "age_years": 34,
                            "height_cm": 178.0, "target_kg": 72.0, "unit": "KG",
                            "keys": {"80:F1:16:90:6A:1E": "ABC"}},
              "readings": [
                {"ts": $timestamp, "mac": "80:F1:16:90:6A:1E", "grams": $grams,
                 "impedance": 600.0, "bmi": 23.3, "fat_pct": 19.6, "muscle_kg": 31.5,
                 "water_pct": 58.9, "bone_kg": 3.1, "visceral_cm2": 10, "bmr_kcal": 1686,
                 "metabolic_age": 35, "frame": "0xC0", "product_id": 2577}
              ]
            }
        """.trimIndent()

        val db = freshDb()
        val store = LocalStore(context)
        val summary = Backup.importJson(store, db, legacy)

        assertEquals(1, summary.readings)
        assertEquals(0, summary.measurements)
        assertEquals(0, summary.drinks)
        val row = db.all().single()
        assertEquals(23.3, row.bmi!!, 0.0)
        assertEquals(31.5, row.muscleKg!!, 0.0)
        assertNull(row.fatMassKg)
        assertNull(row.proteinKg)
        assertEquals(35, row.metabolicAge)
        assertEquals(34, store.ageYears)
        assertEquals("ABC", store.keyFor("80:F1:16:90:6A:1E"))
        // Goals the old file never carried are unset, not zero.
        assertNull(store.waterGoalMl)
        assertNull(store.calorieGoalKcal)
        assertEquals(ActivityLevel.LIGHT, store.activity)
    }

    @Test
    fun csvHeaderAndRowCarryEverySnapshotColumn() {
        val db = freshDb()
        seed(db, LocalStore(context))
        val lines = db.asCsv().trim().lines()
        val header = lines.first().split(",")
        val row = lines[1].split(",")
        assertEquals(header.size, row.size)
        assertTrue(header.contains("skeletal_pct"))
        assertTrue(header.contains("obesity_degree"))
        assertTrue(header.contains("ideal_kg"))
    }
}
