package com.maximillianurio.afya.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

/**
 * The opt-in copy of each weigh-in's weight into Health Connect, where other apps on the phone can
 * read it. Only the weight the scale measured goes there — never a composition estimate, which other
 * apps would show without the word "estimate" next to it. Health Connect is a store on this phone,
 * not a cloud, but the person still chooses it, and nothing is written until they do.
 */
class HealthShare(context: Context) {
    private val app = context.applicationContext

    val available: Boolean
        get() = HealthConnectClient.getSdkStatus(app) == HealthConnectClient.SDK_AVAILABLE

    /** True when Health Connect is missing but can be installed or updated from the store. */
    val needsInstall: Boolean
        get() = HealthConnectClient.getSdkStatus(app) ==
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private val client: HealthConnectClient? by lazy {
        if (available) HealthConnectClient.getOrCreate(app) else null
    }

    suspend fun granted(): Boolean {
        val connected = client ?: return false
        return connected.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    /** Writes one weigh-in, keyed by its timestamp so a second write of the same row replaces it. */
    suspend fun write(timestampMs: Long, kilograms: Double): Boolean {
        val connected = client ?: return false
        if (!granted()) return false
        val at = Instant.ofEpochMilli(timestampMs)
        val record = WeightRecord(
            time = at,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.autoRecorded(
                device = Device(type = Device.TYPE_SCALE),
                clientRecordId = clientId(timestampMs),
                clientRecordVersion = 1L,
            ),
        )
        return runCatching { connected.insertRecords(listOf(record)) }.isSuccess
    }

    /** Takes a weigh-in back out, so a delete in Afya is a delete everywhere it was copied to. */
    suspend fun remove(timestampMs: Long) {
        val connected = client ?: return
        if (!granted()) return
        runCatching {
            connected.deleteRecords(
                WeightRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(clientId(timestampMs)),
            )
        }
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(HealthPermission.getWritePermission(WeightRecord::class))

        private fun clientId(timestampMs: Long) = "afya-weigh-in-$timestampMs"
    }
}
