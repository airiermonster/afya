package com.maximillianurio.afya.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

data class ScanSummary(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMs: Long,
    val frameName: String,
    val productId: Int,
    val encrypted: Boolean,
    val keyMissing: Boolean,
    val locked: Boolean,
    val weight: WeightValue?,
    val rawHex: String,
)

/**
 * Passive reader: the scale is a BLE beacon, so an unfiltered LE scan is the whole connection.
 * A filtered scan cannot be used because the advertiser randomises its address.
 */
class ScaleScanner(
    private val context: Context,
    private val keyProvider: (String) -> String?,
    private val onDevicesChanged: (List<ScanSummary>) -> Unit,
    private val onReading: (ScaleReading) -> Unit,
    private val onRawAdvertisement: (mac: String, name: String?, rssi: Int, header: String, hex: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var leScanner: BluetoothLeScanner? = null
    private var scanning = false

    private val devices = LinkedHashMap<String, ScanSummary>()
    private val lastEmitAt = HashMap<String, Long>()
    private val lastDisplay = HashMap<String, String>()
    private var lastListRefresh = 0L

    var rawSweepEnabled = false

    val isScanning: Boolean
        get() = scanning

    val adapterEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = NEEDED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (scanning) return true
        val bluetoothAdapter = adapter ?: run {
            onError("This device has no Bluetooth adapter")
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            onError("Bluetooth is off")
            return false
        }
        if (!hasPermissions()) {
            onError("Missing Bluetooth scan permission")
            return false
        }
        val scanner = leScanner ?: bluetoothAdapter.bluetoothLeScanner ?: run {
            onError("LE scanner unavailable")
            return false
        }
        leScanner = scanner
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()
            scanner.startScan(null, settings, callback)
            scanning = true
            true
        } catch (e: SecurityException) {
            onError("Permission revoked: ${e.message}")
            false
        } catch (e: Exception) {
            onError("startScan failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        scanning = false
        try {
            leScanner?.stopScan(callback)
        } catch (e: Exception) {
            onError("stopScan failed: ${e.message}")
        }
        leScanner = null
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handle(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handle(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError(
                when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started elsewhere"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "BLE stack internal error"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "LE scan not supported"
                    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of BLE hardware resources"
                    else -> "Scan failed (code $errorCode)"
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun handle(result: ScanResult) {
        val record = result.scanRecord?.bytes ?: return
        val payload = ScaleProtocol.manufacturerPayload(record)
        val address = try {
            result.device.address
        } catch (e: SecurityException) {
            return
        } ?: return
        val name = try {
            result.scanRecord?.deviceName ?: result.device.name
        } catch (e: SecurityException) {
            null
        }

        if (payload == null) {
            if (rawSweepEnabled) onRawAdvertisement(address, name, result.rssi, "--", ScaleProtocol.hex(record))
            return
        }

        val header = payload[0].toInt() and 0xFF
        if (rawSweepEnabled) {
            onRawAdvertisement(address, name, result.rssi, "%02X".format(header), ScaleProtocol.hex(payload))
        }

        val mac = ScaleProtocol.macFromPayload(payload, address)
        val reading = ScaleProtocol.parse(payload, address, keyProvider(mac)) ?: return
        val displayKey = reading.weight?.display ?: "key?"

        synchronized(devices) {
            devices[mac] = ScanSummary(
                mac = mac,
                name = name,
                rssi = result.rssi,
                lastSeenMs = System.currentTimeMillis(),
                frameName = reading.frameName + if (reading.encrypted) "*" else "",
                productId = reading.productId,
                encrypted = reading.encrypted,
                keyMissing = reading.keyMissing,
                locked = reading.locked,
                weight = reading.weight,
                rawHex = reading.rawHex,
            )
        }

        val now = SystemClock.elapsedRealtime()
        val changed = lastDisplay[mac] != displayKey
        val due = now - (lastEmitAt[mac] ?: 0L) > LIST_REFRESH_MS
        if (changed || due) {
            lastEmitAt[mac] = now
            lastDisplay[mac] = displayKey
            onReading(reading)
        }
        if (now - lastListRefresh > LIST_REFRESH_MS) {
            lastListRefresh = now
            onDevicesChanged(snapshot())
        }
    }

    fun snapshot(): List<ScanSummary> = synchronized(devices) {
        devices.values.sortedByDescending { it.lastSeenMs }
    }

    fun forgetStale(olderThanMs: Long) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val removed = synchronized(devices) {
            val stale = devices.filterValues { it.lastSeenMs < cutoff }.keys.toList()
            stale.forEach { devices.remove(it) }
            stale
        }
        if (removed.isNotEmpty()) onDevicesChanged(snapshot())
    }

    companion object {
        private const val LIST_REFRESH_MS = 700L
        val NEEDED_PERMISSIONS = arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
    }
}
