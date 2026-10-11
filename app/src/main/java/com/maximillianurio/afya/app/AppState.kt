package com.maximillianurio.afya.app

import com.maximillianurio.afya.core.ActivityLevel
import com.maximillianurio.afya.core.Heat
import com.maximillianurio.afya.core.IntentProfile
import com.maximillianurio.afya.core.ScaleReading
import com.maximillianurio.afya.core.ScanSummary
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.WeightUnit
import com.maximillianurio.afya.data.NetworkCall
import com.maximillianurio.afya.data.ReadingsDb

data class ProfileState(
    val sex: Sex = Sex.MALE,
    val name: String = "",
    /** File name inside the app's private storage, not a path; null until a photo is chosen. */
    val avatarFile: String? = null,
    /** [java.time.LocalDate.toEpochDay], 0 when no birthday has been picked. */
    val birthEpochDay: Long = 0L,
    val ageYears: Int = 0,
    val heightCm: Double = 0.0,
    val targetKg: Double = 0.0,
    val unit: WeightUnit = WeightUnit.KG,
    /** Both feed the water and energy targets; neither changes what the scale measured. */
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val heat: Heat = Heat.MILD,
    val intent: IntentProfile = IntentProfile(),
    val complete: Boolean = false,
)

data class SettingsState(
    val autoRecord: Boolean = true,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val diagnostics: Boolean = false,
    /** Null on both means "use what the equations recommend today". */
    val waterGoalMl: Int? = null,
    val calorieGoalKcal: Int? = null,
    val lengthInches: Boolean = false,
    val themeMode: Int = -1,
    /** Minutes after midnight for the weigh-in reminder, -1 while it is off. */
    val reminderMinutes: Int = -1,
    val healthConnect: Boolean = false,
    /** Newest first: every request that has left the phone. */
    val networkLog: List<NetworkCall> = emptyList(),
)

/** Where the one-time key lookup for the bound scale stands. */
enum class KeyFetch { Idle, Fetching, Failed }

/**
 * A bar message with, when the act that caused it was reversible, the way to take it back.
 * [onAction] runs when the person taps [actionLabel]; a transient message leaves both null.
 */
data class Notice(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Everything the UI renders. The view model owns the scanner, the store and the database and pushes
 * a new snapshot here when any of them changes, so screens never touch those objects themselves.
 */
data class AppState(
    val reading: ScaleReading? = null,
    /**
     * The last frame the scale locked with a plausible load on it. A scale goes quiet the moment
     * weight comes off it, and this is what keeps the finished figure on the platform until the
     * next one locks.
     */
    val lastStable: ScaleReading? = null,
    /** The row [lastStable] was filed as; null when it has not been filed or was just deleted. */
    val filedEntry: ReadingsDb.Entry? = null,
    /** True while a locked load is waiting out the settle window before being filed. */
    val recordQueued: Boolean = false,
    /**
     * Set while the figure on the platform is held back for being far from the recent pattern: the
     * median of recent weigh-ins, in kilograms, that it was compared with. Null when nothing is held.
     */
    val checkReferenceKg: Double? = null,
    val devices: List<ScanSummary> = emptyList(),
    val rawFrames: List<String> = emptyList(),
    val boundMac: String? = null,
    val boundKey: String? = null,
    /** The one-time key lookup for the bound scale, so the sheet can show its wait and its failure. */
    val keyFetch: KeyFetch = KeyFetch.Idle,
    /** What went wrong in plain words after a failed lookup; null until one fails. */
    val keyFetchReason: String? = null,
    val isScanning: Boolean = false,
    val adapterEnabled: Boolean = false,
    /** The Nearby devices permission the scan needs; false until it is granted. */
    val permissionsGranted: Boolean = true,
    val profile: ProfileState = ProfileState(),
    val settings: SettingsState = SettingsState(),
    val lastSaved: ReadingsDb.Entry? = null,
    val readingCount: Int = 0,
    /** Bumped whenever the stored rows change, so the trends screen re-queries. */
    val dataVersion: Int = 0,
)
