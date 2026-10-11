package com.maximillianurio.afya.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Unit field of the weight broadcast frames. The vendor decodes it with `BytesUtil.getUnit`, a
 * 2-bit field, so a body scale can only ever broadcast one of these four. The 10-value table in
 * `UnitUtil` belongs to the nutrition/food-scale frames and misreads a kg frame as fl'oz.
 */
enum class ScaleUnit(val label: String, val toKg: Double, val preference: WeightUnit) {
    KG("kg", 1.0, WeightUnit.KG),
    JIN("jin", 0.5, WeightUnit.JIN),
    LB("lb", 0.4535924, WeightUnit.LB),
    ST("st:lb", 14 * 0.4535924, WeightUnit.ST_LB),
}

data class WeightValue(
    val display: String,
    val unit: ScaleUnit,
    val decimals: Int,
    val grams: Double,
)

data class ScaleReading(
    val mac: String,
    val frameType: Int,
    val seqNo: Int,
    val productId: Int,
    val locked: Boolean,
    val weight: WeightValue?,
    val encrypted: Boolean,
    val keyMissing: Boolean,
    val impedanceOhms: Double?,
    val rawHex: String,
    val timestampMs: Long,
) {
    val frameName: String
        get() = when (frameType) {
            ScaleProtocol.FRAME_CA -> "0xCA"
            ScaleProtocol.FRAME_B0 -> "0xB0"
            ScaleProtocol.FRAME_C0 -> "0xC0"
            else -> "0x%02X".format(frameType)
        }
}

/**
 * Over-the-air format of the OKOK / Chipsea nutrition scale. The device never opens a GATT
 * connection: every frame is a BLE advertisement carrying the frame in manufacturer data.
 */
object ScaleProtocol {
    const val FRAME_CA = 0xCA
    const val FRAME_B0 = 0xB0
    const val FRAME_C0 = 0xC0
    const val NUTRITION_SUBCMD = 2

    /**
     * Walks the AD structure list and returns the body of the last manufacturer-data record
     * (type 0xFF), including the two-byte company id, exactly like the vendor SDK does — so the
     * frame header is the first byte of the result.
     */
    fun manufacturerPayload(record: ByteArray?): ByteArray? {
        if (record == null) return null
        var payload: ByteArray? = null
        var i = 0
        while (i + 1 < record.size) {
            val len = record[i].toInt() and 0xFF
            if (len == 0 || i + 1 + len > record.size) break
            val start = i + 1
            if (record[start].toInt() and 0xFF == 0xFF && len >= 2) {
                payload = record.copyOfRange(start + 1, start + len)
            }
            i = start + len
        }
        return payload
    }

    fun parse(payload: ByteArray?, address: String, keyHex: String?): ScaleReading? {
        if (payload == null || payload.size < 4) return null
        val header = payload[0].toInt() and 0xFF
        val raw = hex(payload)
        return when (header) {
            FRAME_CA -> {
                if (payload.size < 11 || (payload[4].toInt() and 0xFF) != NUTRITION_SUBCMD) null
                else buildCa(payload, address, raw)
            }

            FRAME_B0 -> if (payload.size < 7) null else buildB0(payload, address, raw)
            FRAME_C0 -> if (payload.size < 9) null else buildC0(payload, address, keyHex, raw)
            else -> null
        }
    }

    /** 0xCA: [CA][cmd][len][prop][subcmd][wHi][wLo][prod x4][...]. */
    private fun buildCa(payload: ByteArray, address: String, raw: String): ScaleReading {
        val prop = payload[3]
        return ScaleReading(
            mac = address,
            frameType = FRAME_CA,
            seqNo = 0,
            productId = beInt(payload, 7, 4),
            locked = isLocked(prop),
            weight = decode(payload[5], payload[6], prop),
            encrypted = false,
            keyMissing = false,
            impedanceOhms = null,
            rawHex = raw,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /** 0xB0: [B0][seq][wHi][wLo][prodHi][prodLo][prop][...]. */
    private fun buildB0(payload: ByteArray, address: String, raw: String): ScaleReading {
        val prop = payload[6]
        return ScaleReading(
            mac = address,
            frameType = FRAME_B0,
            seqNo = payload[1].toInt() and 0xFF,
            productId = beInt(payload, 4, 2),
            locked = isLocked(prop),
            weight = decode(payload[2], payload[3], prop),
            encrypted = false,
            keyMissing = false,
            impedanceOhms = null,
            rawHex = raw,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * 0xC0: [C0][seq][wHi][wLo][rHi][rLo][prodHi][prodLo][prop][encMac x2][key x4][...].
     * bit7 of byte 4 marks the frame as obfuscated; the two MAC bytes that follow are encrypted
     * with the four key bytes that follow them, which is the device's own public MAC address, so
     * the MAC is self-deobfuscating. The weight and resistance pairs need the server key.
     */
    private fun buildC0(
        payload: ByteArray,
        address: String,
        keyHex: String?,
        raw: String,
    ): ScaleReading {
        val encrypted = (payload[4].toInt() and 0x80) != 0
        val prop = payload[8]

        var mac = address
        var weightBytes = byteArrayOf(payload[2], payload[3])
        var keyMissing = false
        var impedanceBits = beInt(payload, 4, 2) and 0x7FFF

        if (encrypted) {
            mac = macFromPayload(payload, address)
            val key = hexToBytes(keyHex)
            if (key == null) {
                keyMissing = true
            } else {
                val plain = decrypt15(beInt(payload, 2, 2), key)
                weightBytes = byteArrayOf((plain shr 8).toByte(), (plain and 0xFF).toByte())
                impedanceBits = decrypt15(beInt(payload, 4, 2), key) and 0x7FFF
            }
        }

        return ScaleReading(
            mac = mac,
            frameType = FRAME_C0,
            seqNo = payload[1].toInt() and 0xFF,
            productId = beInt(payload, 6, 2),
            locked = isLocked(prop),
            weight = if (keyMissing) null else decode(weightBytes[0], weightBytes[1], prop),
            encrypted = encrypted,
            keyMissing = keyMissing,
            impedanceOhms = if (keyMissing) null else impedanceBits / 10.0,
            rawHex = raw,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * Real MAC of the advertiser. A 0xC0 frame randomises the advertised address but ships the
     * encrypted MAC together with the key bytes that decrypt it, so no server round-trip is needed.
     */
    fun macFromPayload(payload: ByteArray?, address: String): String {
        if (payload == null || payload.size < 15) return address
        if ((payload[0].toInt() and 0xFF) != FRAME_C0) return address
        if ((payload[4].toInt() and 0x80) == 0) return address
        val tail = payload.copyOfRange(11, 15)
        val plain = decrypt15(beInt(payload, 9, 2), tail)
        return macToString(
            byteArrayOf(
                (plain shr 8).toByte(),
                (plain and 0xFF).toByte(),
                tail[0],
                tail[1],
                tail[2],
                tail[3],
            )
        )
    }

    /** scaleProperty: bit7 sign, bits4..3 unit, bits2..1 decimals, bit0 lock. */
    fun decode(high: Byte, low: Byte, prop: Byte): WeightValue {
        val raw = beInt(byteArrayOf(high, low))
        val unit = unitOf(prop)
        val decimals = decimalsOf(prop)
        val negative = (prop.toInt() and 0x80) != 0

        val stones = high.toInt() and 0xFF
        val tenthsOfAPound = (low.toInt() and 0xFF) / 10.0
        val scaled = roundTo(raw / 10.0.pow(decimals), decimals)
        val kg = if (unit == ScaleUnit.ST) {
            (stones * 14 + tenthsOfAPound) * ScaleUnit.LB.toKg
        } else {
            scaled * unit.toKg
        }
        val display = if (unit == ScaleUnit.ST) "$stones:$tenthsOfAPound" else plain(scaled, decimals)

        return WeightValue(
            display = prefix(negative, display),
            unit = unit,
            decimals = decimals,
            grams = if (negative) -kg * 1000.0 else kg * 1000.0,
        )
    }

    private fun prefix(negative: Boolean, value: String) = if (negative) "-$value" else value

    private fun roundTo(value: Double, places: Int): Double =
        BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()

    private fun plain(value: Double, places: Int): String =
        if (places == 0) value.roundToInt().toString()
        else BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toPlainString()

    private fun unitOf(prop: Byte) = when ((prop.toInt() shr 3) and 0x03) {
        1 -> ScaleUnit.JIN
        2 -> ScaleUnit.LB
        3 -> ScaleUnit.ST
        else -> ScaleUnit.KG
    }

    /** `BytesUtil.getDigit`: 01 means no decimals, 10 means two, everything else means one. */
    private fun decimalsOf(prop: Byte) = when ((prop.toInt() shr 1) and 0x03) {
        1 -> 0
        2 -> 2
        else -> 1
    }

    private fun isLocked(prop: Byte) = (prop.toInt() and 0x01) == 1

    /**
     * 15-bit rotate-and-XOR obfuscation. The top bit of [input] is the sign and passes through
     * untouched; each round XORs one key byte and rotates the remaining 15 bits.
     */
    fun decrypt15(input: Int, key: ByteArray): Int {
        if (key.size < 4) return 0
        val sign = (input ushr 15) and 1
        var v = ((input and 0x7FFF) xor (key[0].toInt() and 0xFF)) and 0x7FFF
        v = ((((((v shl 14) and 0x7FFF) or (v ushr 1)) and 0x7FFF) xor (key[1].toInt() and 0xFF)) and 0x7FFF)
        v = ((((((v shl 12) and 0x7FFF) or (v ushr 3)) and 0x7FFF) xor (key[2].toInt() and 0xFF)) and 0x7FFF)
        v = ((((((v shl 13) and 0x7FFF) or (v ushr 2)) and 0x7FFF) xor (key[3].toInt() and 0xFF)) and 0x7FFF)
        return (v and 0x7FFF) or (sign shl 15)
    }

    fun beInt(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var value = 0
        for (i in offset until offset + length) {
            value = (value shl 8) or (bytes[i].toInt() and 0xFF)
        }
        return value
    }

    fun hexToBytes(value: String?): ByteArray? {
        val clean = value?.replace(Regex("[^0-9A-Fa-f]"), "") ?: return null
        if (clean.length < 8) return null
        val out = ByteArray(4)
        for (i in 0 until 4) {
            out[i] = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        }
        return out
    }

    /** The inverse of [hex], for replaying a stored frame through [parse]. */
    fun payloadFromHex(value: String?): ByteArray? {
        val clean = value?.replace(Regex("[^0-9A-Fa-f]"), "") ?: return null
        if (clean.length < 8 || clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        }
    }

    fun macToString(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    fun hex(bytes: ByteArray?): String =
        bytes?.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } ?: ""
}
