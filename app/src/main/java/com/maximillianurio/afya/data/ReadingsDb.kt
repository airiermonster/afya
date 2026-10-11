package com.maximillianurio.afya.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.maximillianurio.afya.core.BodyMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device history. One row per recorded weigh-in, carrying the composition the app computed at
 * that moment rather than only the raw load: the profile changes, and a reading has to stay
 * readable as the sheet it was printed on.
 */
class ReadingsDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "readings.db", null, DB_VERSION) {

    data class Entry(
        val id: Long = 0,
        val timestampMs: Long,
        val mac: String,
        val grams: Double,
        val impedanceOhms: Double? = null,
        val bmi: Double? = null,
        val fatPercent: Double? = null,
        val fatMassKg: Double? = null,
        val muscleKg: Double? = null,
        val musclePercent: Double? = null,
        val skeletalKg: Double? = null,
        val skeletalPercent: Double? = null,
        val waterKg: Double? = null,
        val waterPercent: Double? = null,
        val proteinKg: Double? = null,
        val proteinPercent: Double? = null,
        val boneKg: Double? = null,
        val visceralCm2: Double? = null,
        val bmrKcal: Double? = null,
        val metabolicAge: Int? = null,
        val obesityDegree: Double? = null,
        val idealKg: Double? = null,
        val frameName: String = "",
        val productId: Int = 0,
    ) {
        val kilograms: Double get() = grams / 1000.0
    }

    data class Measurement(
        val id: Long = 0,
        val timestampMs: Long,
        val site: String,
        val centimetres: Double,
    )

    data class Drink(
        val id: Long = 0,
        val timestampMs: Long,
        val kind: String,
        val millilitres: Int,
    )

    /**
     * One eaten thing, as the person wrote it. [amount] is their own words for the portion, because
     * a household measure is what someone actually has in front of them; [kilocalories] is theirs too
     * — the app never guesses an energy figure it was not given.
     */
    data class Meal(
        val id: Long = 0,
        val timestampMs: Long,
        val food: String,
        val amount: String,
        val kilocalories: Int? = null,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(READINGS)
        db.execSQL("CREATE INDEX idx_readings_ts ON readings (ts DESC)")
        db.execSQL(MEASUREMENTS)
        db.execSQL("CREATE INDEX idx_measure_site_ts ON measurements (site, ts DESC)")
        db.execSQL(DRINKS)
        db.execSQL("CREATE INDEX idx_drinks_ts ON drinks (ts DESC)")
        db.execSQL(MEALS)
        db.execSQL("CREATE INDEX idx_meals_ts ON meals (ts DESC)")
    }

    /**
     * v1 stored a pre-formatted display string and had no composition columns; v2 added those.
     * Anything older than that may differ again, so read whatever columns actually exist before
     * rebuilding the table. Later versions only ever add columns or tables.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val preserved = runCatching {
                val rows = mutableListOf<Entry>()
                db.rawQuery("SELECT * FROM readings", null).use { cursor ->
                    fun number(name: String): Double? {
                        val index = cursor.getColumnIndex(name)
                        if (index < 0 || cursor.isNull(index)) return null
                        return cursor.getDouble(index)
                    }

                    fun text(name: String): String? =
                        cursor.getColumnIndex(name).takeIf { it >= 0 }?.let { cursor.getString(it) }

                    while (cursor.moveToNext()) {
                        val timestamp = (number("ts") ?: continue).toLong()
                        val grams = number("grams") ?: continue
                        rows.add(
                            Entry(
                                timestampMs = timestamp,
                                mac = text("mac") ?: "",
                                grams = grams,
                                frameName = text("frame_name") ?: "",
                                productId = (number("product_id") ?: 0.0).toInt(),
                            )
                        )
                    }
                }
                rows
            }.getOrDefault(emptyList())

            db.execSQL("DROP TABLE IF EXISTS readings")
            db.execSQL(READINGS)
            preserved.forEach { insertRow(db, it) }
        }
        if (oldVersion < 3) {
            val existing = columns(db, "readings")
            ADD_V3.forEach { (name, type) ->
                if (name !in existing) db.execSQL("ALTER TABLE readings ADD COLUMN $name $type")
            }
            db.execSQL(MEASUREMENTS)
            db.execSQL("CREATE INDEX idx_measure_site_ts ON measurements (site, ts DESC)")
            db.execSQL(DRINKS)
            db.execSQL("CREATE INDEX idx_drinks_ts ON drinks (ts DESC)")
        }
        if (oldVersion < 4) {
            db.execSQL(MEALS)
            db.execSQL("CREATE INDEX idx_meals_ts ON meals (ts DESC)")
        }
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    fun insert(entry: Entry): Long = insertRow(writableDatabase, entry)

    private fun insertRow(db: SQLiteDatabase, entry: Entry): Long = db.insert(
        "readings",
        null,
        ContentValues().apply {
            put("ts", entry.timestampMs)
            put("mac", entry.mac)
            put("grams", entry.grams)
            entry.impedanceOhms?.let { put("impedance", it) }
            entry.bmi?.let { put("bmi", it) }
            entry.fatPercent?.let { put("fat_pct", it) }
            entry.fatMassKg?.let { put("fat_kg", it) }
            entry.muscleKg?.let { put("muscle_kg", it) }
            entry.musclePercent?.let { put("muscle_pct", it) }
            entry.skeletalKg?.let { put("skeletal_kg", it) }
            entry.skeletalPercent?.let { put("skeletal_pct", it) }
            entry.waterKg?.let { put("water_kg", it) }
            entry.waterPercent?.let { put("water_pct", it) }
            entry.proteinKg?.let { put("protein_kg", it) }
            entry.proteinPercent?.let { put("protein_pct", it) }
            entry.boneKg?.let { put("bone_kg", it) }
            entry.visceralCm2?.let { put("visceral_cm2", it) }
            entry.bmrKcal?.let { put("bmr_kcal", it) }
            entry.metabolicAge?.let { put("metabolic_age", it) }
            entry.obesityDegree?.let { put("obesity_degree", it) }
            entry.idealKg?.let { put("ideal_kg", it) }
            put("frame_name", entry.frameName)
            put("product_id", entry.productId)
        },
    )

    /** Snapshot the composition onto the row so a sheet from last March still reads complete. */
    fun entryOf(
        timestampMs: Long,
        mac: String,
        grams: Double,
        impedanceOhms: Double?,
        metrics: BodyMetrics?,
        frameName: String,
        productId: Int,
    ): Entry = Entry(
        timestampMs = timestampMs,
        mac = mac,
        grams = grams,
        impedanceOhms = impedanceOhms,
        bmi = metrics?.bmi,
        fatPercent = metrics?.fatPercent,
        fatMassKg = metrics?.fatMassKg,
        muscleKg = metrics?.muscleKg,
        musclePercent = metrics?.musclePercent,
        skeletalKg = metrics?.skeletalMuscleKg,
        skeletalPercent = metrics?.skeletalMusclePercent,
        waterKg = metrics?.waterKg,
        waterPercent = metrics?.waterPercent,
        proteinKg = metrics?.proteinKg,
        proteinPercent = metrics?.proteinPercent,
        boneKg = metrics?.boneMassKg,
        visceralCm2 = metrics?.visceralCm2,
        bmrKcal = metrics?.bmrKcal,
        metabolicAge = metrics?.metabolicAge,
        obesityDegree = metrics?.obesityDegree,
        idealKg = metrics?.idealWeightKg,
        frameName = frameName,
        productId = productId,
    )

    fun recent(limit: Int): List<Entry> = query(READ_SELECT + " ORDER BY ts DESC LIMIT $limit")

    fun all(): List<Entry> = query(READ_SELECT + " ORDER BY ts DESC")

    /** Newest first, every row filed at or after [fromMs]. */
    fun since(fromMs: Long): List<Entry> = query(READ_SELECT + " WHERE ts >= $fromMs ORDER BY ts DESC")

    /** Oldest-first, for the trend chart. */
    fun series(limit: Int): List<Entry> =
        query(READ_SELECT + " ORDER BY ts DESC LIMIT $limit").reversed()

    fun previousTo(timestampMs: Long): Entry? =
        query(READ_SELECT + " WHERE ts < $timestampMs ORDER BY ts DESC LIMIT 1").firstOrNull()

    fun byId(id: Long): Entry? = query(READ_SELECT + " WHERE id = $id").firstOrNull()

    fun delete(id: Long) {
        writableDatabase.delete("readings", "id = ?", arrayOf(id.toString()))
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM readings", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun query(sql: String): List<Entry> {
        val rows = mutableListOf<Entry>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    Entry(
                        id = cursor.getLong(0),
                        timestampMs = cursor.getLong(1),
                        mac = cursor.getString(2),
                        grams = cursor.getDouble(3),
                        impedanceOhms = cursor.doubleOrNull(4),
                        bmi = cursor.doubleOrNull(5),
                        fatPercent = cursor.doubleOrNull(6),
                        fatMassKg = cursor.doubleOrNull(7),
                        muscleKg = cursor.doubleOrNull(8),
                        musclePercent = cursor.doubleOrNull(9),
                        skeletalKg = cursor.doubleOrNull(10),
                        skeletalPercent = cursor.doubleOrNull(11),
                        waterKg = cursor.doubleOrNull(12),
                        waterPercent = cursor.doubleOrNull(13),
                        proteinKg = cursor.doubleOrNull(14),
                        proteinPercent = cursor.doubleOrNull(15),
                        boneKg = cursor.doubleOrNull(16),
                        visceralCm2 = cursor.doubleOrNull(17),
                        bmrKcal = cursor.doubleOrNull(18),
                        metabolicAge = if (cursor.isNull(19)) null else cursor.getInt(19),
                        obesityDegree = cursor.doubleOrNull(20),
                        idealKg = cursor.doubleOrNull(21),
                        frameName = cursor.getString(22) ?: "",
                        productId = cursor.getInt(23),
                    )
                )
            }
        }
        return rows
    }

    private fun Cursor.doubleOrNull(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    fun insertMeasurement(site: String, centimetres: Double, timestampMs: Long): Long =
        writableDatabase.insert(
            "measurements",
            null,
            ContentValues().apply {
                put("ts", timestampMs)
                put("site", site)
                put("cm", centimetres)
            },
        )

    fun measurements(site: String): List<Measurement> {
        val rows = mutableListOf<Measurement>()
        readableDatabase.rawQuery(
            "SELECT id, ts, site, cm FROM measurements WHERE site = ? ORDER BY ts ASC",
            arrayOf(site),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(Measurement(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getDouble(3)))
            }
        }
        return rows
    }

    fun latestMeasurement(site: String): Measurement? =
        measurements(site).lastOrNull()

    /** Newest value per site, for the measurement grid. Unknown sites are kept, not dropped. */
    fun latestBySite(): Map<String, Measurement> =
        // [allMeasurements] comes back newest first, and distinctBy keeps the first row per key.
        allMeasurements().distinctBy { it.site }.associateBy { it.site }

    fun allMeasurements(): List<Measurement> =
        readMeasurements("SELECT id, ts, site, cm FROM measurements ORDER BY ts DESC")

    private fun readMeasurements(sql: String): List<Measurement> {
        val rows = mutableListOf<Measurement>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    Measurement(
                        id = cursor.getLong(0),
                        timestampMs = cursor.getLong(1),
                        site = cursor.getString(2),
                        centimetres = cursor.getDouble(3),
                    )
                )
            }
        }
        return rows
    }

    fun deleteMeasurement(id: Long) {
        writableDatabase.delete("measurements", "id = ?", arrayOf(id.toString()))
    }

    fun insertDrink(kind: String, millilitres: Int, timestampMs: Long): Long = writableDatabase.insert(
        "drinks",
        null,
        ContentValues().apply {
            put("ts", timestampMs)
            put("kind", kind)
            put("ml", millilitres)
        },
    )

    fun drinks(fromMs: Long, toMs: Long): List<Drink> {
        val rows = mutableListOf<Drink>()
        readableDatabase.rawQuery(
            "SELECT id, ts, kind, ml FROM drinks WHERE ts >= ? AND ts < ? ORDER BY ts DESC",
            arrayOf(fromMs.toString(), toMs.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(Drink(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getInt(3)))
            }
        }
        return rows
    }

    fun deleteDrink(id: Long) {
        writableDatabase.delete("drinks", "id = ?", arrayOf(id.toString()))
    }

    /** One logged drink by row id, so a delete can hand back exactly what it removed. */
    fun drinkById(id: Long): Drink? {
        readableDatabase.rawQuery(
            "SELECT id, ts, kind, ml FROM drinks WHERE id = ?", arrayOf(id.toString()),
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                Drink(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getInt(3))
            } else {
                null
            }
        }
    }

    /** Millilitres logged in a window, for the tally row on the day's sheet. */
    fun drinkTotalMl(fromMs: Long, toMs: Long): Int = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(ml), 0) FROM drinks WHERE ts >= $fromMs AND ts < $toMs",
        null,
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun allDrinks(): List<Drink> {
        val rows = mutableListOf<Drink>()
        readableDatabase.rawQuery(
            "SELECT id, ts, kind, ml FROM drinks ORDER BY ts DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(Drink(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getInt(3)))
            }
        }
        return rows
    }

    fun insertMeal(food: String, amount: String, kilocalories: Int?, timestampMs: Long): Long =
        writableDatabase.insert(
            "meals",
            null,
            ContentValues().apply {
                put("ts", timestampMs)
                put("food", food)
                put("amount", amount)
                kilocalories?.let { put("kcal", it) }
            },
        )

    fun meals(fromMs: Long, toMs: Long): List<Meal> =
        readMeals("SELECT id, ts, food, amount, kcal FROM meals WHERE ts >= $fromMs AND ts < $toMs ORDER BY ts DESC")

    fun allMeals(): List<Meal> =
        readMeals("SELECT id, ts, food, amount, kcal FROM meals ORDER BY ts DESC")

    private fun readMeals(sql: String): List<Meal> {
        val rows = mutableListOf<Meal>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    Meal(
                        id = cursor.getLong(0),
                        timestampMs = cursor.getLong(1),
                        food = cursor.getString(2),
                        amount = cursor.getString(3),
                        kilocalories = if (cursor.isNull(4)) null else cursor.getInt(4),
                    )
                )
            }
        }
        return rows
    }

    fun deleteMeal(id: Long) {
        writableDatabase.delete("meals", "id = ?", arrayOf(id.toString()))
    }

    /** One logged meal by row id, so a delete can hand back exactly what it removed. */
    fun mealById(id: Long): Meal? =
        readMeals("SELECT id, ts, food, amount, kcal FROM meals WHERE id = $id").firstOrNull()

    /** Energy written against today's entries. Null until someone has typed a figure. */
    fun mealTotalKcal(fromMs: Long, toMs: Long): Int? = readableDatabase.rawQuery(
        "SELECT COUNT(kcal), COALESCE(SUM(kcal), 0) FROM meals WHERE ts >= $fromMs AND ts < $toMs",
        null,
    ).use {
        if (!it.moveToFirst()) return@use null
        if (it.getInt(0) == 0) null else it.getInt(1)
    }

    /** Start afresh: every recorded row goes, the profile and the binding stay. */
    fun clearAll() {
        writableDatabase.apply {
            delete("readings", null, null)
            delete("measurements", null, null)
            delete("drinks", null, null)
            delete("meals", null, null)
        }
    }

    /** How much is about to be dropped, so the confirmation can say it in numbers. */
    data class Counts(
        val readings: Int,
        val measurements: Int,
        val drinks: Int,
        val meals: Int,
    ) {
        val total: Int get() = readings + measurements + drinks + meals
    }

    fun counts(): Counts = Counts(
        readings = countTable("readings"),
        measurements = countTable("measurements"),
        drinks = countTable("drinks"),
        meals = countTable("meals"),
    )

    private fun countTable(table: String): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM $table", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun asCsv(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val builder = StringBuilder()
        builder.append(CSV_HEADER).append('\n')
        for (row in all()) {
            builder.append(
                listOf(
                    stamp.format(Date(row.timestampMs)),
                    row.mac,
                    row.grams.toString(),
                    "%.2f".format(row.kilograms),
                    text(row.bmi), text(row.fatPercent), text(row.fatMassKg),
                    text(row.muscleKg), text(row.musclePercent),
                    text(row.skeletalKg), text(row.skeletalPercent),
                    text(row.waterKg), text(row.waterPercent),
                    text(row.proteinKg), text(row.proteinPercent),
                    text(row.boneKg), text(row.visceralCm2), text(row.bmrKcal),
                    row.metabolicAge?.toString() ?: "",
                    text(row.obesityDegree), text(row.idealKg),
                    text(row.impedanceOhms), row.frameName, row.productId.toString(),
                ).joinToString(",")
            )
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun text(value: Double?): String = value?.let { "%.2f".format(it) } ?: ""

    private companion object {
        const val DB_VERSION = 4

        const val READINGS = """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                mac TEXT NOT NULL,
                grams REAL NOT NULL,
                impedance REAL,
                bmi REAL,
                fat_pct REAL,
                fat_kg REAL,
                muscle_kg REAL,
                muscle_pct REAL,
                skeletal_kg REAL,
                skeletal_pct REAL,
                water_kg REAL,
                water_pct REAL,
                protein_kg REAL,
                protein_pct REAL,
                bone_kg REAL,
                visceral_cm2 REAL,
                bmr_kcal REAL,
                metabolic_age INTEGER,
                obesity_degree REAL,
                ideal_kg REAL,
                frame_name TEXT,
                product_id INTEGER
            )
        """

        const val MEASUREMENTS = """
            CREATE TABLE IF NOT EXISTS measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                site TEXT NOT NULL,
                cm REAL NOT NULL
            )
        """

        const val DRINKS = """
            CREATE TABLE IF NOT EXISTS drinks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                kind TEXT NOT NULL,
                ml INTEGER NOT NULL
            )
        """

        const val MEALS = """
            CREATE TABLE IF NOT EXISTS meals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                food TEXT NOT NULL,
                amount TEXT NOT NULL,
                kcal INTEGER
            )
        """

        val ADD_V3 = listOf(
            "fat_kg" to "REAL",
            "muscle_pct" to "REAL",
            "skeletal_kg" to "REAL",
            "skeletal_pct" to "REAL",
            "water_kg" to "REAL",
            "protein_kg" to "REAL",
            "protein_pct" to "REAL",
            "obesity_degree" to "REAL",
            "ideal_kg" to "REAL",
        )

        const val READ_SELECT = "SELECT id, ts, mac, grams, impedance, bmi, fat_pct, fat_kg, " +
            "muscle_kg, muscle_pct, skeletal_kg, skeletal_pct, water_kg, water_pct, protein_kg, " +
            "protein_pct, bone_kg, visceral_cm2, bmr_kcal, metabolic_age, obesity_degree, ideal_kg, " +
            "frame_name, product_id FROM readings"

        const val CSV_HEADER = "timestamp,mac,grams,kg,bmi,fat_pct,fat_kg,muscle_kg,muscle_pct," +
            "skeletal_kg,skeletal_pct,water_kg,water_pct,protein_kg,protein_pct,bone_kg," +
            "visceral_cm2,bmr_kcal,metabolic_age,obesity_degree,ideal_kg,impedance_ohm,frame,product_id"
    }
}
