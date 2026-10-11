package com.maximillianurio.afya.data

import android.content.Context
import android.content.SharedPreferences
import com.maximillianurio.afya.core.ActivityLevel
import com.maximillianurio.afya.core.CheckIn
import com.maximillianurio.afya.core.Aim
import com.maximillianurio.afya.core.Heat
import com.maximillianurio.afya.core.IntentProfile
import com.maximillianurio.afya.core.Motivation
import com.maximillianurio.afya.core.Obstacle
import com.maximillianurio.afya.core.Pace
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.WeightUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Bound scale, per-MAC obfuscation keys, the user profile and app settings. */
class LocalStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("afya_scale", Context.MODE_PRIVATE)

    var boundMac: String?
        get() = prefs.getString(KEY_BOUND, null)
        set(value) = prefs.edit().putString(KEY_BOUND, value).apply()

    var autoRecord: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var showDiagnostics: Boolean
        get() = prefs.getBoolean(KEY_DIAG, false)
        set(value) = prefs.edit().putBoolean(KEY_DIAG, value).apply()

    /**
     * The last frame the scale locked with a body on it, kept so the platform still shows a finished
     * weigh-in when the app is reopened. It is the device's own payload, never a re-derived figure.
     */
    var lastFrameMac: String?
        get() = prefs.getString(KEY_FRAME_MAC, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_FRAME_MAC) else putString(KEY_FRAME_MAC, value)
        }.apply()

    var lastFrameHex: String?
        get() = prefs.getString(KEY_FRAME_HEX, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_FRAME_HEX) else putString(KEY_FRAME_HEX, value)
        }.apply()

    var lastFrameAtMs: Long
        get() = prefs.getLong(KEY_FRAME_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_FRAME_TS, value).apply()

    var sex: Sex
        get() = runCatching { Sex.valueOf(prefs.getString(KEY_SEX, Sex.MALE.name)!!) }
            .getOrDefault(Sex.MALE)
        set(value) = prefs.edit().putString(KEY_SEX, value.name).apply()

    /** What the sheets and the greeting call the person. Empty until they write one. */
    var displayName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    /**
     * The file name of the avatar inside the app's private files directory, not a path: the
     * directory moves between installs and backups must not carry a machine-specific absolute path.
     */
    var avatarFile: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().apply {
            val name = bareFileName(value)
            if (name == null) remove(KEY_AVATAR) else putString(KEY_AVATAR, name)
        }.apply()

    /** [java.time.LocalDate.toEpochDay], or 0 when no birthday has been written. */
    var birthEpochDay: Long
        get() = prefs.getLong(KEY_BIRTH, 0L)
        set(value) = prefs.edit().putLong(KEY_BIRTH, value).apply()

    /**
     * Age is never stored: it is counted from the birthday. A profile written before the birthday
     * field existed keeps its integer age until one is picked, so nothing reads as zero mid-upgrade.
     */
    val ageYears: Int
        get() = if (birthEpochDay > 0L) {
            ChronoUnit.YEARS.between(
                LocalDate.ofEpochDay(birthEpochDay),
                LocalDate.now(),
            ).toInt().coerceAtLeast(0)
        } else {
            prefs.getInt(KEY_AGE, 0)
        }

    /** Writing a birthday retires the legacy integer age for good. */
    fun setBirthDate(date: LocalDate?) {
        prefs.edit().apply {
            if (date == null) remove(KEY_BIRTH) else putLong(KEY_BIRTH, date.toEpochDay())
            remove(KEY_AGE)
        }.apply()
    }

    val intent: IntentProfile
        get() = IntentProfile(
            aim = enumPref<Aim>(KEY_INTENT_DIRECTION),
            pace = enumPref<Pace>(KEY_INTENT_PACE),
            motivation = enumPref<Motivation>(KEY_INTENT_MOTIVATION),
            obstacle = enumPref<Obstacle>(KEY_INTENT_OBSTACLE),
            checkIn = enumPref<CheckIn>(KEY_INTENT_CHECKIN),
            answeredAtMs = prefs.getLong(KEY_INTENT_AT, 0L),
        )

    fun saveIntent(intent: IntentProfile) {
        prefs.edit().apply {
            putOrRemove(KEY_INTENT_DIRECTION, intent.aim?.name)
            putOrRemove(KEY_INTENT_PACE, intent.pace?.name)
            putOrRemove(KEY_INTENT_MOTIVATION, intent.motivation?.name)
            putOrRemove(KEY_INTENT_OBSTACLE, intent.obstacle?.name)
            putOrRemove(KEY_INTENT_CHECKIN, intent.checkIn?.name)
            if (intent.answeredAtMs > 0L) putLong(KEY_INTENT_AT, intent.answeredAtMs) else remove(KEY_INTENT_AT)
        }.apply()
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    /** An avatar is a bare file name in the app's own directory; anything that looks like a path is dropped. */
    private fun bareFileName(value: String?): String? = value?.takeIf {
        it.endsWith(".jpg") && !it.contains('/') && !it.contains('\\') && !it.contains("..")
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    var heightCm: Double
        get() = prefs.getFloat(KEY_HEIGHT, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_HEIGHT, value.toFloat()).apply()

    var targetKg: Double
        get() = prefs.getFloat(KEY_TARGET, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TARGET, value.toFloat()).apply()

    var unit: WeightUnit
        get() = runCatching {
            WeightUnit.valueOf(prefs.getString(KEY_UNIT, WeightUnit.KG.name)!!)
        }.getOrDefault(WeightUnit.KG)
        set(value) = prefs.edit().putString(KEY_UNIT, value.name).apply()

    var activity: ActivityLevel
        get() = runCatching {
            ActivityLevel.valueOf(prefs.getString(KEY_ACTIVITY, ActivityLevel.LIGHT.name)!!)
        }.getOrDefault(ActivityLevel.LIGHT)
        set(value) = prefs.edit().putString(KEY_ACTIVITY, value.name).apply()

    var heat: Heat
        get() = runCatching { Heat.valueOf(prefs.getString(KEY_HEAT, Heat.MILD.name)!!) }
            .getOrDefault(Heat.MILD)
        set(value) = prefs.edit().putString(KEY_HEAT, value.name).apply()

    /** Body measurements are kept in centimetres and only converted on the way out. */
    var lengthInches: Boolean
        get() = prefs.getBoolean(KEY_LENGTH_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_LENGTH_IN, value).apply()

    /** -1 follows the system, 0 stays light, 1 stays dark. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, -1)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** Null means the goal is whatever the equations recommend for today. */
    var waterGoalMl: Int?
        get() = if (prefs.contains(KEY_WATER_GOAL)) prefs.getInt(KEY_WATER_GOAL, 0) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_WATER_GOAL) else putInt(KEY_WATER_GOAL, value)
        }.apply()

    /** Null means the energy target follows resting metabolism, activity and the goal weight. */
    var calorieGoalKcal: Int?
        get() = if (prefs.contains(KEY_CALORIE_GOAL)) prefs.getInt(KEY_CALORIE_GOAL, 0) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_CALORIE_GOAL) else putInt(KEY_CALORIE_GOAL, value)
        }.apply()

    /** Minutes after midnight for the weigh-in reminder, or -1 while it is off. Off until chosen. */
    var reminderMinutes: Int
        get() = prefs.getInt(KEY_REMINDER, -1)
        set(value) = prefs.edit().putInt(KEY_REMINDER, value).apply()

    /** Whether each weigh-in's weight is also written to Health Connect. Off until switched on. */
    var healthConnect: Boolean
        get() = prefs.getBoolean(KEY_HEALTH_CONNECT, false)
        set(value) = prefs.edit().putBoolean(KEY_HEALTH_CONNECT, value).apply()

    /**
     * Every request this app has made to the network, newest first, so the one-egress promise can be
     * checked rather than taken on trust. Kept on this phone only and never put in a backup.
     */
    val networkLog: List<NetworkCall>
        get() = prefs.getString(KEY_NETWORK_LOG, null).orEmpty()
            .lineSequence()
            .mapNotNull { NetworkCall.parse(it) }
            .toList()

    fun logNetworkCall(call: NetworkCall) {
        val lines = listOf(call.line()) + networkLog.take(NETWORK_LOG_LIMIT - 1).map { it.line() }
        prefs.edit().putString(KEY_NETWORK_LOG, lines.joinToString("\n")).apply()
    }

    val profileComplete: Boolean
        get() = ageYears in 1..120 && heightCm > 0

    fun keyFor(mac: String?): String? = mac?.let { prefs.getString(KEY_PREFIX + it, null) }

    fun setKey(mac: String, keyHex: String) {
        prefs.edit().putString(KEY_PREFIX + mac, keyHex.trim()).apply()
    }

    fun snapshot(): Map<String, Any?> = linkedMapOf(
        "bound_mac" to boundMac,
        "auto_record" to autoRecord,
        "sound" to soundEnabled,
        "haptics" to hapticsEnabled,
        "diagnostics" to showDiagnostics,
        "sex" to sex.name,
        "name" to displayName,
        "avatar_file" to avatarFile,
        "birth_epoch_day" to birthEpochDay.takeIf { it > 0L },
        "age_years" to prefs.getInt(KEY_AGE, 0).takeIf { it > 0 },
        "height_cm" to heightCm,
        "target_kg" to targetKg,
        "unit" to unit.name,
        "activity" to activity.name,
        "heat" to heat.name,
        "length_inches" to lengthInches,
        "theme_mode" to themeMode,
        "water_goal_ml" to waterGoalMl,
        "calorie_goal_kcal" to calorieGoalKcal,
        "intent_direction" to intent.aim?.name,
        "intent_pace" to intent.pace?.name,
        "intent_motivation" to intent.motivation?.name,
        "intent_obstacle" to intent.obstacle?.name,
        "intent_check_in" to intent.checkIn?.name,
        "intent_answered_at" to intent.answeredAtMs.takeIf { it > 0L },
        "keys" to prefs.all.entries
            .filter { it.key.startsWith(KEY_PREFIX) }
            .associate { it.key.removePrefix(KEY_PREFIX) to it.value.toString() },
    )

    fun restore(values: Map<String, Any?>) {
        val editor = prefs.edit()
        fun string(key: String, pref: String) = (values[key] as? String)?.let { editor.putString(pref, it) }
        fun bool(key: String, pref: String, default: Boolean) =
            editor.putBoolean(pref, values[key] as? Boolean ?: default)

        fun optionalLong(key: String, pref: String) = when (val value = values[key]) {
            null -> editor.remove(pref)
            else -> editor.putLong(pref, (value as? Number)?.toLong() ?: 0L)
        }

        fun optionalString(key: String, pref: String) = when (val value = values[key]) {
            null -> editor.remove(pref)
            else -> editor.putString(pref, value.toString())
        }

        string("bound_mac", KEY_BOUND)
        bool("auto_record", KEY_AUTO, true)
        bool("sound", KEY_SOUND, true)
        bool("haptics", KEY_HAPTIC, true)
        bool("diagnostics", KEY_DIAG, false)
        string("sex", KEY_SEX)
        string("unit", KEY_UNIT)
        string("activity", KEY_ACTIVITY)
        string("heat", KEY_HEAT)
        fun optionalInt(key: String, pref: String) = when (val value = values[key]) {
            null -> editor.remove(pref)
            else -> editor.putInt(pref, (value as? Number)?.toInt() ?: 0)
        }

        editor.putBoolean(KEY_LENGTH_IN, values["length_inches"] as? Boolean ?: false)
        editor.putInt(KEY_THEME, (values["theme_mode"] as? Number)?.toInt() ?: -1)
        optionalInt("water_goal_ml", KEY_WATER_GOAL)
        optionalInt("calorie_goal_kcal", KEY_CALORIE_GOAL)
        optionalInt("age_years", KEY_AGE)
        optionalLong("birth_epoch_day", KEY_BIRTH)
        editor.putFloat(KEY_HEIGHT, (values["height_cm"] as? Number)?.toFloat() ?: 0f)
        editor.putFloat(KEY_TARGET, (values["target_kg"] as? Number)?.toFloat() ?: 0f)
        editor.putString(KEY_NAME, values["name"] as? String ?: "")
        val avatar = bareFileName(values["avatar_file"] as? String)
        if (avatar == null) editor.remove(KEY_AVATAR) else editor.putString(KEY_AVATAR, avatar)
        optionalLong("intent_answered_at", KEY_INTENT_AT)
        optionalString("intent_direction", KEY_INTENT_DIRECTION)
        optionalString("intent_pace", KEY_INTENT_PACE)
        optionalString("intent_motivation", KEY_INTENT_MOTIVATION)
        optionalString("intent_obstacle", KEY_INTENT_OBSTACLE)
        optionalString("intent_check_in", KEY_INTENT_CHECKIN)
        editor.apply()

        @Suppress("UNCHECKED_CAST")
        val keys = values["keys"] as? Map<String, String> ?: emptyMap()
        val keyEditor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { keyEditor.remove(it) }
        keys.forEach { (mac, key) -> keyEditor.putString(KEY_PREFIX + mac, key) }
        keyEditor.apply()
    }

    companion object {
        private const val KEY_BOUND = "bound_mac"
        private const val KEY_AUTO = "auto_record"
        private const val KEY_SOUND = "sound"
        private const val KEY_HAPTIC = "haptics"
        private const val KEY_DIAG = "diagnostics"
        private const val KEY_FRAME_MAC = "last_frame_mac"
        private const val KEY_FRAME_HEX = "last_frame_hex"
        private const val KEY_FRAME_TS = "last_frame_ts"
        private const val KEY_SEX = "sex"
        private const val KEY_NAME = "display_name"
        private const val KEY_AVATAR = "avatar_file"
        private const val KEY_BIRTH = "birth_epoch_day"
        private const val KEY_AGE = "age"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_TARGET = "target_kg"
        private const val KEY_UNIT = "unit"
        private const val KEY_ACTIVITY = "activity"
        private const val KEY_HEAT = "heat"
        private const val KEY_LENGTH_IN = "length_inches"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_WATER_GOAL = "water_goal_ml"
        private const val KEY_CALORIE_GOAL = "calorie_goal_kcal"
        private const val KEY_INTENT_DIRECTION = "intent_direction"
        private const val KEY_INTENT_PACE = "intent_pace"
        private const val KEY_INTENT_MOTIVATION = "intent_motivation"
        private const val KEY_INTENT_OBSTACLE = "intent_obstacle"
        private const val KEY_INTENT_CHECKIN = "intent_check_in"
        private const val KEY_INTENT_AT = "intent_answered_at"
        private const val KEY_PREFIX = "key_"
        private const val KEY_REMINDER = "reminder_minutes"
        private const val KEY_HEALTH_CONNECT = "health_connect"
        private const val KEY_NETWORK_LOG = "network_log"
        private const val NETWORK_LOG_LIMIT = 50
    }
}
