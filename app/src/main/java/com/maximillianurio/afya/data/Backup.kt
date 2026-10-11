package com.maximillianurio.afya.data

import com.maximillianurio.afya.core.MeasurementSites
import org.json.JSONArray
import org.json.JSONObject

/**
 * Self-contained JSON snapshot: settings, profile, cached keys, the reading history with the
 * composition each row was printed with, body measurements and the drink log. The field names are
 * the ones the Views-era writer used, so older exports still import; schema 3 only adds.
 */
object Backup {
    private const val SCHEMA = 4

    data class ImportSummary(
        val readings: Int,
        val skipped: Int,
        val measurements: Int = 0,
        val drinks: Int = 0,
        val meals: Int = 0,
    )

    fun exportJson(store: LocalStore, db: ReadingsDb): String {
        val root = JSONObject()
        root.put("app", "afya")
        root.put("schema", SCHEMA)
        root.put("exported_at", System.currentTimeMillis())
        root.put("settings", JSONObject(store.snapshot()))
        root.put("readings", JSONArray().apply {
            for (entry in db.all()) put(readingsJson(entry))
        })
        root.put("measurements", JSONArray().apply {
            for (row in db.allMeasurements()) {
                put(JSONObject().put("ts", row.timestampMs).put("site", row.site).put("cm", row.centimetres))
            }
        })
        root.put("drinks", JSONArray().apply {
            for (row in db.allDrinks()) {
                put(JSONObject().put("ts", row.timestampMs).put("kind", row.kind).put("ml", row.millilitres))
            }
        })
        root.put("meals", JSONArray().apply {
            for (row in db.allMeals()) {
                put(
                    JSONObject()
                        .put("ts", row.timestampMs)
                        .put("food", row.food)
                        .put("amount", row.amount)
                        .put("kcal", row.kilocalories ?: JSONObject.NULL)
                )
            }
        })
        return root.toString(2)
    }

    private fun readingsJson(entry: ReadingsDb.Entry): JSONObject = JSONObject().apply {
        put("ts", entry.timestampMs)
        put("mac", entry.mac)
        put("grams", entry.grams)
        put("impedance", entry.impedanceOhms ?: JSONObject.NULL)
        put("bmi", entry.bmi ?: JSONObject.NULL)
        put("fat_pct", entry.fatPercent ?: JSONObject.NULL)
        put("fat_kg", entry.fatMassKg ?: JSONObject.NULL)
        put("muscle_kg", entry.muscleKg ?: JSONObject.NULL)
        put("muscle_pct", entry.musclePercent ?: JSONObject.NULL)
        put("skeletal_kg", entry.skeletalKg ?: JSONObject.NULL)
        put("skeletal_pct", entry.skeletalPercent ?: JSONObject.NULL)
        put("water_kg", entry.waterKg ?: JSONObject.NULL)
        put("water_pct", entry.waterPercent ?: JSONObject.NULL)
        put("protein_kg", entry.proteinKg ?: JSONObject.NULL)
        put("protein_pct", entry.proteinPercent ?: JSONObject.NULL)
        put("bone_kg", entry.boneKg ?: JSONObject.NULL)
        put("visceral_cm2", entry.visceralCm2 ?: JSONObject.NULL)
        put("bmr_kcal", entry.bmrKcal ?: JSONObject.NULL)
        put("metabolic_age", entry.metabolicAge ?: JSONObject.NULL)
        put("obesity_degree", entry.obesityDegree ?: JSONObject.NULL)
        put("ideal_kg", entry.idealKg ?: JSONObject.NULL)
        put("frame", entry.frameName)
        put("product_id", entry.productId)
    }

    fun importJson(store: LocalStore, db: ReadingsDb, text: String): ImportSummary {
        val root = JSONObject(text)
        root.optJSONObject("settings")?.let { settings ->
            val map = mutableMapOf<String, Any?>()
            settings.keys().forEach { key ->
                val value = settings.opt(key)
                map[key] = if (value === JSONObject.NULL) null else value
            }
            // restore() wants a plain map for the per-MAC keys; JSON hands us a JSONObject.
            settings.optJSONObject("keys")?.let { cached ->
                map["keys"] = buildMap {
                    cached.keys().forEach { mac -> put(mac, cached.optString(mac)) }
                }
            }
            store.restore(map)
        }

        val readingsSeen = db.all().mapTo(mutableSetOf()) { it.timestampMs }
        var inserted = 0
        var skipped = 0
        val rows = root.optJSONArray("readings") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val timestamp = row.optLong("ts", 0L)
            val grams = row.optDouble("grams", Double.NaN)
            if (timestamp == 0L || grams.isNaN() || timestamp in readingsSeen) {
                skipped++
                continue
            }
            db.insert(
                ReadingsDb.Entry(
                    timestampMs = timestamp,
                    mac = row.optString("mac", ""),
                    grams = grams,
                    impedanceOhms = row.optDoubleOrNull("impedance"),
                    bmi = row.optDoubleOrNull("bmi"),
                    fatPercent = row.optDoubleOrNull("fat_pct"),
                    fatMassKg = row.optDoubleOrNull("fat_kg"),
                    muscleKg = row.optDoubleOrNull("muscle_kg"),
                    musclePercent = row.optDoubleOrNull("muscle_pct"),
                    skeletalKg = row.optDoubleOrNull("skeletal_kg"),
                    skeletalPercent = row.optDoubleOrNull("skeletal_pct"),
                    waterKg = row.optDoubleOrNull("water_kg"),
                    waterPercent = row.optDoubleOrNull("water_pct"),
                    proteinKg = row.optDoubleOrNull("protein_kg"),
                    proteinPercent = row.optDoubleOrNull("protein_pct"),
                    boneKg = row.optDoubleOrNull("bone_kg"),
                    visceralCm2 = row.optDoubleOrNull("visceral_cm2"),
                    bmrKcal = row.optDoubleOrNull("bmr_kcal"),
                    metabolicAge = if (row.isNull("metabolic_age")) null else row.optInt("metabolic_age"),
                    obesityDegree = row.optDoubleOrNull("obesity_degree"),
                    idealKg = row.optDoubleOrNull("ideal_kg"),
                    frameName = row.optString("frame", ""),
                    productId = row.optInt("product_id", 0),
                )
            )
            readingsSeen += timestamp
            inserted++
        }

        // A site the app no longer measures is skipped rather than inserted as an orphan row.
        val measuresSeen = db.allMeasurements().mapTo(mutableSetOf()) { it.site to it.timestampMs }
        var measures = 0
        val measureRows = root.optJSONArray("measurements") ?: JSONArray()
        for (i in 0 until measureRows.length()) {
            val row = measureRows.optJSONObject(i) ?: continue
            val site = row.optString("site", "")
            val timestamp = row.optLong("ts", 0L)
            val centimetres = row.optDouble("cm", Double.NaN)
            val key = site to timestamp
            if (!MeasurementSites.keyIsValid(site) || timestamp == 0L || centimetres.isNaN()) continue
            if (key in measuresSeen) continue
            db.insertMeasurement(site, centimetres, timestamp)
            measuresSeen += key
            measures++
        }

        val drinksSeen = db.allDrinks().mapTo(mutableSetOf()) { it.timestampMs to it.kind }
        var drinks = 0
        val drinkRows = root.optJSONArray("drinks") ?: JSONArray()
        for (i in 0 until drinkRows.length()) {
            val row = drinkRows.optJSONObject(i) ?: continue
            val timestamp = row.optLong("ts", 0L)
            val kind = row.optString("kind", "")
            val millilitres = row.optInt("ml", 0)
            val key = timestamp to kind
            if (timestamp == 0L || kind.isEmpty() || millilitres <= 0) continue
            if (key in drinksSeen) continue
            db.insertDrink(kind, millilitres, timestamp)
            drinksSeen += key
            drinks++
        }

        val mealSeen = db.allMeals().mapTo(mutableSetOf()) { it.timestampMs to it.food }
        var meals = 0
        val mealRows = root.optJSONArray("meals") ?: JSONArray()
        for (i in 0 until mealRows.length()) {
            val row = mealRows.optJSONObject(i) ?: continue
            val timestamp = row.optLong("ts", 0L)
            val food = row.optString("food", "").trim()
            val key = timestamp to food
            if (timestamp == 0L || food.isEmpty() || key in mealSeen) continue
            db.insertMeal(
                food = food,
                amount = row.optString("amount", ""),
                kilocalories = row.optInt("kcal", 0).takeIf { it > 0 },
                timestampMs = timestamp,
            )
            mealSeen += key
            meals++
        }

        return ImportSummary(inserted, skipped, measures, drinks, meals)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { !it.isNaN() }
    }
}
