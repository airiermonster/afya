package com.maximillianurio.afya.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maximillianurio.afya.BuildConfig
import com.maximillianurio.afya.R
import com.maximillianurio.afya.Reminders
import com.maximillianurio.afya.WeightWidget
import com.maximillianurio.afya.core.ActivityLevel
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.DrinkKind
import com.maximillianurio.afya.core.Heat
import com.maximillianurio.afya.core.IntentProfile
import com.maximillianurio.afya.core.MeasurementSites
import com.maximillianurio.afya.core.Report
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.ScaleProtocol
import com.maximillianurio.afya.core.ScaleReading
import com.maximillianurio.afya.core.ScaleScanner
import com.maximillianurio.afya.core.ScaleKeyClient
import com.maximillianurio.afya.core.SoundCue
import com.maximillianurio.afya.core.TrendWeight
import com.maximillianurio.afya.core.WeighInGuard
import com.maximillianurio.afya.core.WeightUnit
import com.maximillianurio.afya.data.Backup
import com.maximillianurio.afya.data.HealthRecord
import com.maximillianurio.afya.data.HealthShare
import com.maximillianurio.afya.data.LocalStore
import com.maximillianurio.afya.data.NetworkCall
import com.maximillianurio.afya.data.RecordCell
import com.maximillianurio.afya.data.RecordPdf
import com.maximillianurio.afya.data.RecordSite
import com.maximillianurio.afya.data.ReadingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * Owns the single BLE scan, the store and the database, and turns locked frames into recorded
 * weigh-ins — the role `MainActivity` held in the Views build. Screens observe [state] and
 * receive one-shot messages on [notices] instead of registering listeners.
 *
 * Threading rule, unchanged: the BLE callback arrives on a binder thread, so every scanner callback
 * marshals to the main thread before touching state.
 */
class AfyaViewModel(application: Application) : AndroidViewModel(application) {

    val store = LocalStore(application)
    val db = ReadingsDb(application)
    private val health = HealthShare(application)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _notices = Channel<Notice>(Channel.BUFFERED)
    val notices: Flow<Notice> = _notices.receiveAsFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val readings = HashMap<String, ScaleReading>()
    private val keyAttempts = HashSet<String>()
    private val rawLines = ArrayDeque<String>()
    private val dataVersion = AtomicInteger()

    private var candidate: ScaleReading? = null
    private var committedMac: String? = null
    private var committedGrams = Double.NaN
    private var committedAtMs = 0L

    /** The last locked, plausible frame off the bound scale, held so the sheet never goes blank. */
    private var stable: ScaleReading? = null

    /** The row [stable] was filed as, so the sheet can offer to take it back. */
    private var filed: ReadingsDb.Entry? = null

    /**
     * A figure the person just deleted, with the load still on the plinth. Filing it again behind
     * their back would make the delete look broken, so it is refused until they step off.
     */
    private var discardedMac: String? = null
    private var discardedGrams = Double.NaN

    private var recordQueued = false

    /**
     * A locked figure held back from the records because it lands far from the recent pattern —
     * most often whoever the phone was lent to. It is filed only when the person says it is theirs.
     */
    private var unconfirmed: ScaleReading? = null
    private var unconfirmedReferenceKg: Double? = null

    private val settle = Runnable { settleNow() }

    private val pruneStale = object : Runnable {
        override fun run() {
            scanner.forgetStale(STALE_MS)
            mainHandler.postDelayed(this, STALE_MS)
        }
    }

    private val scanner: ScaleScanner = ScaleScanner(
        context = application,
        keyProvider = { mac -> store.keyFor(mac) },
        onDevicesChanged = { post { publishDevices() } },
        onReading = { reading -> post { handleReading(reading) } },
        onRawAdvertisement = { mac, _, rssi, header, hex ->
            if (header != "--") post { addRawLine("$header $mac $rssi  $hex") }
        },
        onError = { message -> post { notify(message) } },
    )

    init {
        scanner.rawSweepEnabled = store.showDiagnostics
        mainHandler.postDelayed(pruneStale, STALE_MS)
        restorePlatform()
        publishAll()
    }

    // region lifecycle

    /** Mirrors the old onResume: restart the scan if the radio and permissions allow it. */
    fun onResumed() {
        if (scanner.adapterEnabled && scanner.hasPermissions()) startScanning()
        publishReading()
        publishDevices()
    }

    /**
     * The scan cannot outlive the screen, but a figure the scale already locked is the person's
     * weigh-in. The old build dropped it when the phone slept mid-settle, which on a barefoot morning
     * meant stepping on again. File it now instead; the guest check still applies.
     */
    fun onPaused() {
        if (recordQueued && candidate != null) {
            mainHandler.removeCallbacks(settle)
            settleNow()
        } else {
            cancelPendingRecord()
        }
        scanner.stop()
        publishDevices()
    }

    override fun onCleared() {
        mainHandler.removeCallbacksAndMessages(null)
        scanner.stop()
        db.close()
    }

    // endregion

    // region scanning

    enum class ScanRequest { Scanning, NeedsPermissions, NeedsBluetooth }

    fun scanPermissions(): Array<String> = ScaleScanner.NEEDED_PERMISSIONS

    fun hasScanPermissions(): Boolean = scanner.hasPermissions()

    val adapterEnabled: Boolean
        get() = scanner.adapterEnabled

    /**
     * Tries to start reading and reports what is in the way, so the caller can launch the matching
     * system prompt and hand the result back to [onPermissionsResult] or [onBluetoothResult].
     */
    fun requestScanning(): ScanRequest {
        if (!scanner.hasPermissions()) return ScanRequest.NeedsPermissions
        if (!scanner.adapterEnabled) return ScanRequest.NeedsBluetooth
        if (!scanner.start()) {
            // The radio disappeared between the check and the call; report it as the old app did.
            stopScanning()
            return ScanRequest.NeedsBluetooth
        }
        publishDevices()
        return ScanRequest.Scanning
    }

    fun onPermissionsResult(granted: Boolean) {
        // A refusal is shown by the Measure panel, which also carries the way back in.
        if (granted) startScanning() else publishDevices()
    }

    fun onBluetoothResult(enabled: Boolean) {
        if (enabled) startScanning() else notify(getApplication<Application>().getString(R.string.bt_still_off))
    }

    fun startScanning() {
        if (!scanner.adapterEnabled) return
        scanner.start()
        publishDevices()
    }

    fun stopScanning() {
        scanner.stop()
        cancelPendingRecord()
        publishDevices()
    }

    // endregion

    // region readings and recording

    private fun latestReading(): ScaleReading? {
        val mac = store.boundMac
            ?: return readings.values.maxByOrNull { it.timestampMs }
        return readings[mac]
    }

    /**
     * The platform reopens on the last weigh-in it locked rather than on an empty scale. The frame is
     * replayed through the same decoder, so the figure is the device's own LCD text and not a
     * reconstruction from the stored row.
     */
    private fun restorePlatform() {
        val mac = store.lastFrameMac ?: return
        val hex = store.lastFrameHex ?: return
        if (mac != store.boundMac) return
        val atMs = store.lastFrameAtMs
        val restored = ScaleProtocol.parse(ScaleProtocol.payloadFromHex(hex), mac, store.keyFor(mac))
            ?.takeIf { it.locked && it.weight != null && BodyComposition.bodyPlausible(it.weight.grams) }
            ?: return
        stable = restored.copy(timestampMs = atMs)
        val grams = restored.weight!!.grams
        filed = db.recent(1).firstOrNull { it.mac == mac && abs(it.grams - grams) < 1.0 }
    }

    /** One frame per new figure, not per broadcast: the scale repeats itself several times a second. */
    private fun keepFrame(reading: ScaleReading) {
        store.lastFrameMac = reading.mac
        store.lastFrameHex = reading.rawHex
        store.lastFrameAtMs = reading.timestampMs
    }

    private fun handleReading(reading: ScaleReading) {
        readings[reading.mac] = reading
        if (store.boundMac == null && reading.weight != null) {
            store.boundMac = reading.mac
            notify(getApplication<Application>().getString(R.string.msg_connected, reading.mac))
        }
        if (reading.mac != store.boundMac) return

        val grams = reading.weight?.grams
        val onPlinth = grams != null && BodyComposition.bodyPlausible(grams)
        if (!onPlinth) {
            // Weight came off the scale. Whatever was just deleted can now be measured again.
            discardedMac = null
            discardedGrams = Double.NaN
        } else if (reading.locked) {
            val before = stable?.weight?.grams
            val fresh = before == null || abs(before - grams) >= 1.0
            if (fresh && before != null) filed = null
            stable = reading
            if (fresh) keepFrame(reading)
        }

        publishReading()
        queueRecord(reading)
        maybeFetchKey(reading)
    }

    /** The scale flags a frame locked once the load settles; hold off briefly to save one row. */
    private fun queueRecord(reading: ScaleReading) {
        val weight = reading.weight
        if (weight == null || !reading.locked || weight.grams < MIN_GRAMS) {
            cancelPendingRecord()
            return
        }
        candidate = reading
        if (!recordQueued) {
            recordQueued = true
            publishReading()
        }
        mainHandler.removeCallbacks(settle)
        mainHandler.postDelayed(settle, SETTLE_MS)
    }

    private fun cancelPendingRecord() {
        candidate = null
        mainHandler.removeCallbacks(settle)
        if (recordQueued) {
            recordQueued = false
            publishReading()
        }
    }

    /** The settle window closed: file what was on the plinth, and say so either way. */
    private fun settleNow() {
        val pending = candidate
        candidate = null
        recordQueued = false
        commit(pending)
        publishReading()
    }

    /**
     * Files one reading. Auto-record calls this on the settle timer; the sheet's own action calls it
     * with `manual` when auto-record is off. Returns false when there was nothing to file.
     */
    private fun commit(reading: ScaleReading?, manual: Boolean = false): Boolean {
        val weight = reading?.weight ?: return false
        if (!store.autoRecord && !manual) return false
        if (reading.mac != store.boundMac && store.boundMac != null) return false

        val grams = weight.grams
        val now = reading.timestampMs
        val repeat = reading.mac == committedMac &&
            abs(grams - committedGrams) < REPEAT_GRAMS &&
            now - committedAtMs < REPEAT_WINDOW_MS
        if (repeat) return false
        val discarded = reading.mac == discardedMac &&
            abs(grams - discardedGrams) < REPEAT_GRAMS
        if (discarded) return false

        // Auto-record never files a stranger's weight as the owner's. Recording by hand is the
        // person's own call, so it skips the question.
        if (!manual && BodyComposition.bodyPlausible(grams)) {
            val reference = WeighInGuard.reference(
                db.recent(WeighInGuard.SAMPLE * 2)
                    .filter { BodyComposition.bodyPlausible(it.grams) }
                    .map { it.timestampMs to it.kilograms },
                now,
            )
            if (WeighInGuard.needsCheck(grams / 1000.0, reference)) {
                val sameLoad = unconfirmed?.weight?.grams?.let { abs(it - grams) < REPEAT_GRAMS } == true
                if (!sameLoad) {
                    unconfirmed = reading
                    unconfirmedReferenceKg = reference
                    SoundCue.stable(getApplication(), store.soundEnabled, store.hapticsEnabled)
                }
                return false
            }
        }

        val metrics = if (store.profileComplete && BodyComposition.bodyPlausible(grams)) {
            BodyComposition.compute(grams / 1000.0, store.heightCm, store.ageYears, store.sex)
        } else {
            null
        }

        db.insert(
            ReadingsDb.Entry(
                timestampMs = now,
                mac = reading.mac,
                grams = grams,
                impedanceOhms = reading.impedanceOhms,
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
                frameName = reading.frameName,
                productId = reading.productId,
            )
        )

        committedMac = reading.mac
        committedGrams = grams
        committedAtMs = now
        candidate = null
        unconfirmed = null
        unconfirmedReferenceKg = null
        filed = db.recent(1).firstOrNull { it.timestampMs == now }

        SoundCue.stable(getApplication(), store.soundEnabled, store.hapticsEnabled)
        if (store.healthConnect && BodyComposition.bodyPlausible(grams)) {
            viewModelScope.launch { health.write(now, grams / 1000.0) }
        }
        publishData()
        return true
    }

    /** The sheet's wide ink action: file the locked reading on the platform right now. */
    fun recordNow(): Boolean {
        val reading = stable ?: latestReading()?.takeIf { it.locked && it.weight != null }
        if (reading == null) {
            notify(getApplication<Application>().getString(R.string.msg_no_locked))
            return false
        }
        // Reaching for this by hand is a change of mind about a figure deleted moments ago.
        discardedMac = null
        discardedGrams = Double.NaN
        val filedRow = commit(reading, manual = true)
        recordQueued = false
        publishReading()
        if (!filedRow) notify(getApplication<Application>().getString(R.string.msg_already_filed))
        return filedRow
    }

    /** The held figure is the owner's after all: file it as any other weigh-in. */
    fun confirmMine() {
        val held = unconfirmed ?: return
        discardedMac = null
        discardedGrams = Double.NaN
        commit(held, manual = true)
        publishReading()
    }

    /**
     * The held figure belongs to someone else. It stays on the platform so they can read it, and is
     * kept out of the records until the load comes off.
     */
    fun notMine() {
        val held = unconfirmed ?: return
        discardedMac = held.mac
        discardedGrams = held.weight?.grams ?: Double.NaN
        unconfirmed = null
        unconfirmedReferenceKg = null
        cancelPendingRecord()
        publishReading()
        notify(getApplication<Application>().getString(R.string.guest_not_saved))
    }

    /**
     * Take one weigh-in back out of the records. The figure stays on the platform so the person can
     * see what they removed, and the load under it is not re-filed until they step off.
     */
    fun deleteReading(id: Long) {
        val gone = db.byId(id) ?: return
        db.delete(id)
        if (store.healthConnect) viewModelScope.launch { health.remove(gone.timestampMs) }
        discardedMac = gone.mac
        discardedGrams = gone.grams
        filed = null
        committedMac = null
        committedGrams = Double.NaN
        committedAtMs = 0L
        cancelPendingRecord()
        publishData()
        publishReading()
        notifyWithUndo(getApplication<Application>().getString(R.string.reading_deleted)) {
            val newId = db.insert(gone)
            if (store.healthConnect) viewModelScope.launch { health.write(gone.timestampMs, gone.kilograms) }
            // Still standing on the same load: point the sheet's filed row at the restored row.
            val onPlinth = stable?.weight?.grams?.let { abs(it - gone.grams) < 1.0 } == true
            if (onPlinth && gone.mac == stable?.mac) filed = db.byId(newId)
            publishData()
            publishReading()
        }
    }

    fun bindTo(mac: String) {
        store.boundMac = mac
        committedMac = null
        committedGrams = Double.NaN
        forgetPlatform()
        notify(getApplication<Application>().getString(R.string.msg_bound, mac))
        publishReading()
        publishDevices()
        publishProfile()
        readings[mac]?.let { maybeFetchKey(it) }
    }

    fun unbind() {
        store.boundMac = null
        cancelPendingRecord()
        forgetPlatform()
        notify(getApplication<Application>().getString(R.string.msg_unbound))
        publishReading()
        publishDevices()
        publishProfile()
    }

    /** A new scale, or an empty history: nothing is held on the platform any more. */
    private fun forgetPlatform() {
        stable = null
        filed = null
        discardedMac = null
        discardedGrams = Double.NaN
        unconfirmed = null
        unconfirmedReferenceKg = null
        store.lastFrameMac = null
        store.lastFrameHex = null
        store.lastFrameAtMs = 0L
    }

    private fun maybeFetchKey(reading: ScaleReading) {
        if (!reading.encrypted || !reading.keyMissing) return
        if (store.keyFor(reading.mac) != null || !keyAttempts.add(reading.mac)) return
        fetchKey(reading.mac)
    }

    fun fetchKey(mac: String) {
        if (_state.value.keyFetch == KeyFetch.Fetching) return
        _state.update { it.copy(keyFetch = KeyFetch.Fetching, keyFetchReason = null) }
        viewModelScope.launch {
            val result = ScaleKeyClient.fetchKey(mac)
            store.logNetworkCall(
                NetworkCall(
                    timestampMs = System.currentTimeMillis(),
                    host = ScaleKeyClient.HOST,
                    mac = mac,
                    outcome = when (result) {
                        is ScaleKeyClient.Result.Key -> "key received"
                        is ScaleKeyClient.Result.Failure -> result.message
                    },
                )
            )
            publishSettings()
            when (result) {
                is ScaleKeyClient.Result.Key -> {
                    store.setKey(mac, result.keyHex)
                    _state.update { it.copy(keyFetch = KeyFetch.Idle, keyFetchReason = null) }
                    notify(getApplication<Application>().getString(R.string.key_received))
                    publishProfile()
                    publishReading()
                }

                is ScaleKeyClient.Result.Failure -> {
                    // Plain words, never the raw exception: the sheet shows the reason and the retry.
                    val reason = keyFailureText(result.message)
                    _state.update { it.copy(keyFetch = KeyFetch.Failed, keyFetchReason = reason) }
                    notify(reason)
                }
            }
        }
    }

    /** Whether Health Connect is on this phone at all, and whether it only needs installing. */
    val healthAvailable: Boolean get() = health.available
    val healthNeedsInstall: Boolean get() = health.needsInstall

    /** Switches the weight copy on only once Health Connect has actually granted the write. */
    fun setHealthConnect(on: Boolean) {
        viewModelScope.launch {
            store.healthConnect = on && health.granted()
            publishSettings()
            if (on && !store.healthConnect) {
                notify(getApplication<Application>().getString(R.string.health_not_granted))
            }
        }
    }

    /** Minutes after midnight, or -1 to switch the reminder off. */
    fun setReminder(minutes: Int) {
        store.reminderMinutes = minutes
        Reminders.schedule(getApplication(), minutes)
        publishSettings()
    }

    /** The bar's retry, and the panel's: the one-shot guard would otherwise never ask again. */
    fun retryKeyFetch() {
        val mac = _state.value.reading?.mac ?: store.boundMac ?: return
        keyAttempts.remove(mac)
        fetchKey(mac)
    }

    /** The vendor client's message sorted into the three things a person can act on. */
    private fun keyFailureText(raw: String): String {
        val context = getApplication<Application>()
        return when {
            raw.startsWith("No key in reply") -> context.getString(R.string.key_error_none)
            raw.startsWith("HTTP") ->
                context.getString(R.string.key_error_http, raw.removePrefix("HTTP").trim())
            else -> context.getString(R.string.key_error_network)
        }
    }

    fun saveKey(mac: String, keyHex: String): Boolean {
        if (ScaleProtocol.hexToBytes(keyHex) == null) return false
        store.setKey(mac, keyHex)
        notify(getApplication<Application>().getString(R.string.msg_key_saved, mac))
        publishProfile()
        return true
    }

    // endregion

    // profile, settings and data

    fun setSex(sex: Sex) {
        store.sex = sex
        publishProfile()
    }

    fun setUnit(unit: WeightUnit) {
        store.unit = unit
        publishProfile()
        WeightWidget.refresh(getApplication())
    }

    /**
     * Validated save of the person's own fields. Age is never written: it is counted from the
     * birthday, and a birthday that does not land between 12 and 100 years ago is rejected.
     */
    fun saveProfile(name: String, birthEpochDay: Long?, heightCm: Double?, targetKg: Double?): Boolean {
        val birth = birthEpochDay?.let { LocalDate.ofEpochDay(it) }
        val age = birth?.let { ChronoUnit.YEARS.between(it, LocalDate.now()).toInt() }
        if (birth != null && (birth.isAfter(LocalDate.now()) || age !in 12..100)) {
            notify(getApplication<Application>().getString(R.string.msg_bad_birthday))
            return false
        }
        if (heightCm == null || heightCm !in 80.0..250.0) {
            notify(getApplication<Application>().getString(R.string.msg_bad_height))
            return false
        }
        if (targetKg != null && targetKg !in 20.0..400.0) {
            notify(getApplication<Application>().getString(R.string.msg_bad_goal))
            return false
        }
        store.displayName = name
        if (birth != null) store.setBirthDate(birth)
        store.heightCm = heightCm
        store.targetKg = targetKg ?: 0.0
        cancelPendingRecord()
        committedMac = null
        publishProfile()
        publishData()
        notify(getApplication<Application>().getString(R.string.msg_profile_saved))
        return true
    }

    /** The intent interview's answers, written in one go so a half-answered set never persists. */
    fun saveIntent(intent: IntentProfile) {
        store.saveIntent(intent.copy(answeredAtMs = if (intent.answeredAtMs > 0L) intent.answeredAtMs else System.currentTimeMillis()))
        publishProfile()
        notify(getApplication<Application>().getString(R.string.msg_intent_saved))
    }

    /**
     * The picked photo is decoded, shrunk to avatar size and re-written as one JPEG inside the app's
     * private storage, so a 12-megapixel camera roll never lands in the app or the backup.
     */
    fun setAvatarFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val written = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_MAX_PX) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return@runCatching null
                val name = "avatar-${System.currentTimeMillis()}.jpg"
                File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                name
            }
            val name = written.getOrNull()
            if (name == null) {
                notify(getApplication<Application>().getString(R.string.msg_bad_image))
                return@launch
            }
            store.avatarFile?.let { File(context.filesDir, it).delete() }
            store.avatarFile = name
            publishProfile()
        }
    }

    fun clearAvatar() {
        val context = getApplication<Application>()
        store.avatarFile?.let { File(context.filesDir, it).delete() }
        store.avatarFile = null
        publishProfile()
    }

    fun setAutoRecord(enabled: Boolean) {
        store.autoRecord = enabled
        publishSettings()
    }

    fun setSound(enabled: Boolean) {
        store.soundEnabled = enabled
        publishSettings()
    }

    fun setHaptics(enabled: Boolean) {
        store.hapticsEnabled = enabled
        publishSettings()
    }

    fun setDiagnostics(enabled: Boolean) {
        store.showDiagnostics = enabled
        scanner.rawSweepEnabled = enabled
        publishSettings()
    }

    fun setActivity(level: ActivityLevel) {
        store.activity = level
        publishProfile()
    }

    fun setHeat(heat: Heat) {
        store.heat = heat
        publishProfile()
    }

    /** Null puts the water goal back to whatever the equations recommend for today. */
    fun setWaterGoalMl(millilitres: Int?) {
        store.waterGoalMl = millilitres?.coerceIn(500, 8_000)
        publishSettings()
    }

    fun setCalorieGoalKcal(kilocalories: Int?) {
        store.calorieGoalKcal = kilocalories?.coerceIn(800, 6_000)
        publishSettings()
    }

    fun setLengthInches(enabled: Boolean) {
        store.lengthInches = enabled
        publishSettings()
    }

    fun setThemeMode(mode: Int) {
        store.themeMode = mode
        publishSettings()
    }

    // endregion

    // stored rows

    fun allReadings(): List<ReadingsDb.Entry> = db.all()

    fun readingsSince(fromMs: Long): List<ReadingsDb.Entry> = db.since(fromMs)

    /**
     * Oldest-first (time, kg) of every body-weight reading in the last [days], for the trend line.
     * Loads that cannot be a person are left out so a parcel on the plinth never bends the trend.
     */
    fun weightSeries(days: Int = TREND_DAYS): List<Pair<Long, Double>> =
        db.since(System.currentTimeMillis() - days * 86_400_000L)
            .filter { BodyComposition.bodyPlausible(it.grams) }
            .reversed()
            .map { it.timestampMs to it.kilograms }

    /** A row's history, and how many weigh-ins were left out of it for being read another way. */
    data class MetricHistory(val points: List<Pair<Long, Double>>, val leftOut: Int)

    /**
     * One report row's figure across the filed weigh-ins, oldest first, in the unit that row prints
     * in. Only snapshots read through the same height, age and sex as the newest one are plotted:
     * across a profile edit the equations change, and the line would show the edit as a change in
     * the body. The count of the rest is returned so the sheet can say they were left out.
     */
    fun metricHistory(name: String): MetricHistory {
        val rows = db.all()
            .filter { BodyComposition.bodyPlausible(it.grams) }
            .mapNotNull { entry -> metricsOf(entry)?.let { entry to it } }
        val (newest, newestMetrics) = rows.firstOrNull() ?: return MetricHistory(emptyList(), 0)
        val same = rows.filter { (entry, metrics) ->
            Report.sameBasis(newestMetrics, newest.kilograms, metrics, entry.kilograms)
        }
        val points = same.asReversed().mapNotNull { (entry, metrics) ->
            Report.figureOf(name, metrics, store.unit)?.let { entry.timestampMs to it }
        }
        return MetricHistory(points, rows.size - same.size)
    }

    /** The smoothed weight right now, or null until there is enough history for one to mean anything. */
    fun trendKg(): Double? {
        val series = weightSeries()
        return if (TrendWeight.meaningful(series)) TrendWeight.latest(series) else null
    }

    fun previousTo(timestampMs: Long): ReadingsDb.Entry? = db.previousTo(timestampMs)

    /**
     * The figures as they were filed. Every row carries its own snapshot, so a weigh-in read months
     * ago prints the same schedule it did on the day; a row stored before snapshots existed falls
     * back to the equations run on its weight.
     */
    fun metricsOf(entry: ReadingsDb.Entry): BodyMetrics? {
        val metres = (store.heightCm.takeIf { it > 0 } ?: 1.72) / 100.0
        val range = 18.5 * metres * metres to 24.9 * metres * metres
        val bmi = entry.bmi ?: return computeFresh(entry)
        val fatPercent = entry.fatPercent ?: return computeFresh(entry)
        val fatMass = entry.fatMassKg ?: return computeFresh(entry)
        val muscle = entry.muscleKg ?: return computeFresh(entry)
        val water = entry.waterKg ?: return computeFresh(entry)
        val protein = entry.proteinKg ?: return computeFresh(entry)
        val bone = entry.boneKg ?: return computeFresh(entry)
        val visceral = entry.visceralCm2 ?: return computeFresh(entry)
        val energy = entry.bmrKcal ?: return computeFresh(entry)
        val metabolicAge = entry.metabolicAge ?: return computeFresh(entry)
        val ideal = entry.idealKg ?: return computeFresh(entry)
        val kg = entry.kilograms
        val skeletal = entry.skeletalKg ?: muscle * BodyComposition.SKELETAL_OF_MUSCLE
        return BodyMetrics(
            bmi = bmi,
            fatPercent = fatPercent,
            fatMassKg = fatMass,
            leanMassKg = kg - fatMass,
            muscleKg = muscle,
            musclePercent = entry.musclePercent ?: percent(muscle, kg),
            skeletalMuscleKg = skeletal,
            skeletalMusclePercent = entry.skeletalPercent ?: percent(skeletal, kg),
            waterKg = water,
            waterPercent = entry.waterPercent ?: percent(water, kg),
            proteinKg = protein,
            proteinPercent = entry.proteinPercent ?: percent(protein, kg),
            boneMassKg = bone,
            visceralCm2 = visceral,
            bmrKcal = energy,
            metabolicAge = metabolicAge,
            idealWeightKg = ideal,
            obesityDegree = entry.obesityDegree ?: (kg - ideal) / ideal * 100.0,
            healthyRangeKg = range,
        )
    }

    /**
     * The figures of [older] when a change from them to [newer] means something: both filed under
     * the same height, age and sex. Across a profile edit the two snapshots were read through
     * different equations, so the honest delta is none at all.
     */
    fun comparedMetricsOf(newer: ReadingsDb.Entry, older: ReadingsDb.Entry?): BodyMetrics? {
        if (older == null) return null
        return Report.comparable(metricsOf(newer), newer.kilograms, metricsOf(older), older.kilograms)
    }

    private fun computeFresh(entry: ReadingsDb.Entry): BodyMetrics? =
        BodyComposition.compute(entry.kilograms, store.heightCm, store.ageYears, store.sex)

    private fun percent(partKg: Double, totalKg: Double): Double =
        if (totalKg > 0) partKg / totalKg * 100.0 else 0.0

    // measurements and drinks

    /** Latest value per body site, keyed by the site key the sheet prints. */
    fun measurementsBySite(): Map<String, ReadingsDb.Measurement> = db.latestBySite()

    /** A new row rather than an update, so the measurement history reads like the readings do. */
    fun saveMeasurement(siteKey: String, centimetres: Double): Boolean {
        if (!MeasurementSites.keyIsValid(siteKey)) return false
        if (!MeasurementSites.plausible(centimetres)) {
            notify(getApplication<Application>().getString(R.string.msg_bad_measure))
            return false
        }
        db.insertMeasurement(siteKey, centimetres, System.currentTimeMillis())
        publishData()
        return true
    }

    fun deleteMeasurement(id: Long) {
        db.deleteMeasurement(id)
        publishData()
    }

    fun drinksToday(nowMs: Long = System.currentTimeMillis()): List<ReadingsDb.Drink> {
        val (start, end) = DateFmt.dayBounds(nowMs)
        return db.drinks(start, end)
    }

    fun drinkTotalToday(nowMs: Long = System.currentTimeMillis()): Int {
        val (start, end) = DateFmt.dayBounds(nowMs)
        return db.drinkTotalMl(start, end)
    }

    fun logDrink(kind: DrinkKind, millilitres: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (millilitres !in 1..3_000) {
            notify(getApplication<Application>().getString(R.string.msg_bad_volume))
            return false
        }
        db.insertDrink(kind.name, millilitres, nowMs)
        publishData()
        return true
    }

    fun deleteDrink(id: Long) {
        val gone = db.drinkById(id) ?: return
        db.deleteDrink(id)
        publishData()
        notifyWithUndo(getApplication<Application>().getString(R.string.drink_removed)) {
            db.insertDrink(gone.kind, gone.millilitres, gone.timestampMs)
            publishData()
        }
    }

    /** The kind is stored as the enum name so a renamed label never orphans a logged drink. */
    fun drinkKindOf(stored: String): DrinkKind =
        DrinkKind.entries.firstOrNull { it.name == stored } ?: DrinkKind.OTHER

    // meals

    fun mealsToday(nowMs: Long = System.currentTimeMillis()): List<ReadingsDb.Meal> {
        val (start, end) = DateFmt.dayBounds(nowMs)
        return db.meals(start, end)
    }

    /** Null while nobody has typed an energy figure, so the plan says "not written" not "zero". */
    fun mealTotalKcalToday(nowMs: Long = System.currentTimeMillis()): Int? {
        val (start, end) = DateFmt.dayBounds(nowMs)
        return db.mealTotalKcal(start, end)
    }

    fun logMeal(food: String, amount: String, kilocalories: Int?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val name = food.trim()
        if (name.isEmpty()) {
            notify(getApplication<Application>().getString(R.string.msg_no_food))
            return false
        }
        if (kilocalories != null && kilocalories !in 1..6_000) {
            notify(getApplication<Application>().getString(R.string.msg_bad_kcal))
            return false
        }
        db.insertMeal(name, amount.trim(), kilocalories, nowMs)
        publishData()
        return true
    }

    fun deleteMeal(id: Long) {
        val gone = db.mealById(id) ?: return
        db.deleteMeal(id)
        publishData()
        notifyWithUndo(getApplication<Application>().getString(R.string.meal_removed)) {
            db.insertMeal(gone.food, gone.amount, gone.kilocalories, gone.timestampMs)
            publishData()
        }
    }

    fun exportJson(): String = Backup.exportJson(store, db)

    fun exportCsv(): String = db.asCsv()

    /**
     * The printed record, assembled here so the renderer never touches a database or a preference
     * store: the profile, every filed weigh-in, the composition schedule of the latest one, and the
     * tape table. Nothing identifying beyond what the sheet itself shows — no mac, no frame, no
     * product id.
     */
    fun exportPdf(): ByteArray {
        val readings = db.all()
        val newest = readings.firstOrNull()
        val metrics = newest?.let { metricsOf(it) }
        val older = newest?.let { comparedMetricsOf(it, readings.getOrNull(1)) }
        val rows = if (newest != null && metrics != null && store.profileComplete) {
            Report.rows(metrics, newest.kilograms, store.ageYears, store.sex, store.unit, older)
        } else {
            emptyList()
        }
        return RecordPdf.build(
            getApplication(),
            HealthRecord(
                generatedMs = System.currentTimeMillis(),
                versionName = BuildConfig.VERSION_NAME,
                name = store.displayName,
                sexWord = getApplication<Application>()
                    .getString(if (store.sex == Sex.FEMALE) R.string.female else R.string.male),
                sex = store.sex,
                ageYears = store.ageYears,
                birthLabel = DateFmt.birthDate(store.birthEpochDay),
                heightCm = store.heightCm,
                unit = store.unit,
                targetKg = store.targetKg,
                activityLabel = store.activity.label,
                readings = readings,
                rows = rows,
                metrics = metrics,
                sites = tapeSites(),
            ),
        )
    }

    /**
     * One row per taped site that has a figure, in the order a tape is taken down the body, with the
     * bilateral sites merged into a left/right pair the way the measurement sheet prints them. A site
     * never measured is left off; the sheet says what is there rather than filling a grid with dashes.
     */
    private fun tapeSites(): List<RecordSite> {
        // Newest first, so the first row of a site is its figure and the second is what it moved from.
        val history = db.allMeasurements().groupBy { it.site }
        return MeasurementSites.all
            .groupBy { it.key.removeSuffix("_l").removeSuffix("_r") }
            .mapNotNull { (_, sites) ->
                val cells = sites.map { site ->
                    val rows = history[site.key].orEmpty()
                    RecordCell(
                        centimetres = rows.firstOrNull()?.centimetres,
                        changeCm = if (rows.size > 1) rows[0].centimetres - rows[1].centimetres else null,
                    )
                }
                if (cells.none { it.centimetres != null }) return@mapNotNull null
                val latest = sites.mapNotNull { history[it.key]?.firstOrNull()?.timestampMs }
                    .maxOrNull() ?: 0L
                RecordSite(sites.first().label, cells, DateFmt.printDay(latest))
            }
    }

    fun importJson(text: String): Backup.ImportSummary {
        val summary = Backup.importJson(store, db, text)
        committedMac = null
        forgetPlatform()
        publishAll()
        return summary
    }

    /**
     * Start afresh: every recorded row goes — weigh-ins, tape measurements, drinks and meals. The
     * person's own fields, the bound scale and the cached decode keys stay, so the next reading
     * still decodes without a round trip.
     */
    fun clearEverything(): ReadingsDb.Counts {
        val before = db.counts()
        db.clearAll()
        committedMac = null
        forgetPlatform()
        publishAll()
        return before
    }

    // endregion

    // region publishing

    private fun post(action: () -> Unit) = mainHandler.post(action)

    private fun notify(message: String) {
        viewModelScope.launch { _notices.send(Notice(message)) }
    }

    /** A bar message that offers the way back for the act that caused it. */
    private fun notifyWithUndo(message: String, onUndo: () -> Unit) {
        val label = getApplication<Application>().getString(R.string.undo)
        viewModelScope.launch { _notices.send(Notice(message, label, onUndo)) }
    }

    private fun addRawLine(line: String) {
        rawLines.addLast(line)
        while (rawLines.size > RAW_LIMIT) rawLines.removeFirst()
        publishRaw()
    }

    private fun publishReading() = _state.update {
        // The question only stands while the figure it is about is the one on the platform.
        val heldGrams = unconfirmed?.weight?.grams
        val onPlatform = heldGrams != null &&
            stable?.weight?.grams?.let { grams -> abs(grams - heldGrams) < REPEAT_GRAMS } == true
        it.copy(
            reading = latestReading(),
            lastStable = stable,
            filedEntry = filed,
            recordQueued = recordQueued,
            checkReferenceKg = if (onPlatform) unconfirmedReferenceKg else null,
        )
    }

    private fun publishDevices() = _state.update {
        it.copy(
            devices = scanner.snapshot(),
            isScanning = scanner.isScanning,
            adapterEnabled = scanner.adapterEnabled,
            permissionsGranted = scanner.hasPermissions(),
        )
    }

    private fun publishRaw() = _state.update { it.copy(rawFrames = rawLines.toList()) }

    private fun publishProfile() = _state.update {
        it.copy(
            profile = ProfileState(
                sex = store.sex,
                name = store.displayName,
                avatarFile = store.avatarFile,
                birthEpochDay = store.birthEpochDay,
                ageYears = store.ageYears,
                heightCm = store.heightCm,
                targetKg = store.targetKg,
                unit = store.unit,
                activity = store.activity,
                heat = store.heat,
                intent = store.intent,
                complete = store.profileComplete,
            ),
            boundMac = store.boundMac,
            boundKey = store.keyFor(store.boundMac),
        )
    }

    private fun publishSettings() = _state.update {
        it.copy(
            settings = SettingsState(
                autoRecord = store.autoRecord,
                sound = store.soundEnabled,
                haptics = store.hapticsEnabled,
                diagnostics = store.showDiagnostics,
                waterGoalMl = store.waterGoalMl,
                calorieGoalKcal = store.calorieGoalKcal,
                lengthInches = store.lengthInches,
                themeMode = store.themeMode,
                reminderMinutes = store.reminderMinutes,
                healthConnect = store.healthConnect,
                networkLog = store.networkLog,
            )
        )
    }

    /** A stored row changed: bump the version so the trends screen re-queries, and redraw the widget. */
    private fun publishData() {
        _state.update {
            it.copy(
                dataVersion = dataVersion.incrementAndGet(),
                lastSaved = db.recent(1).firstOrNull(),
                readingCount = db.count(),
            )
        }
        WeightWidget.refresh(getApplication())
    }

    private fun publishAll() {
        publishReading()
        publishDevices()
        publishRaw()
        publishProfile()
        publishSettings()
        publishData()
    }

    // endregion

    companion object {
        private const val SETTLE_MS = 1200L
        private const val MIN_GRAMS = 100.0
        private const val REPEAT_GRAMS = 30.0
        private const val REPEAT_WINDOW_MS = 90_000L
        private const val STALE_MS = 30_000L
        private const val RAW_LIMIT = 120
        private const val TREND_DAYS = 120
        private const val AVATAR_MAX_PX = 512

        /** Intent the system dialog uses to turn Bluetooth on. */
        val enableBluetoothIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
}
