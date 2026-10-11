package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property byte is read by hand here — bit positions spelled out from the vendor's
 * `BytesUtil` tables — so [ScaleProtocol.decode] has to agree with an independent reading of the
 * same 8 bits rather than with itself.
 */
class PropertyByteTest {

    private fun bits(prop: Int) = Integer.toBinaryString(prop and 0xFF).padStart(8, '0')

    private fun field(prop: Int, from: Int, width: Int) =
        bits(prop).substring(from, from + width).toInt(2)

    private fun expectedUnit(prop: Int) = when (field(prop, 3, 2)) {
        0 -> ScaleUnit.KG
        1 -> ScaleUnit.JIN
        2 -> ScaleUnit.LB
        else -> ScaleUnit.ST
    }

    /** `BytesUtil.getDigit`: 00 and 11 both mean one decimal, 01 none, 10 two. */
    private fun expectedDecimals(prop: Int) = when (field(prop, 5, 2)) {
        1 -> 0
        2 -> 2
        else -> 1
    }

    /** A plain 0xC0 frame for any property byte: unencrypted, 12320 counts, id 2577. */
    private fun c0(prop: Int) = byteArrayOf(
        0xC0.toByte(), 0x2E, 0x30, 0x20, 0x17, 0x70, 0x0A, 0x11, prop.toByte(),
    )

    @Test
    fun `every property byte reads the documented unit and precision`() {
        for (raw in 0..255) {
            val weight = requireNotNull(
                ScaleProtocol.parse(c0(raw), "AA:BB:CC:DD:EE:FF", null)
            ).weight
            requireNotNull(weight)
            assertEquals("unit of ${bits(raw)}", expectedUnit(raw), weight.unit)
            assertEquals("decimals of ${bits(raw)}", expectedDecimals(raw), weight.decimals)
        }
    }

    @Test
    fun `lock and sign bits are read from the ends of the byte`() {
        for (raw in 0..255) {
            val reading = requireNotNull(ScaleProtocol.parse(c0(raw), "AA:BB:CC:DD:EE:FF", null))
            assertEquals("lock of ${bits(raw)}", field(raw, 7, 1) == 1, reading.locked)
            assertEquals("sign of ${bits(raw)}", field(raw, 0, 1) == 1, reading.weight!!.grams < 0.0)
        }
    }

    @Test
    fun `a kg frame reads the number the scale's own lcd shows`() {
        // C0 2E 30 20 17 70 0A 11 24 — the worked example from the handoff notes.
        val reading = requireNotNull(ScaleProtocol.parse(c0(0x24), "AA:BB:CC:11:22:33", null))
        val weight = requireNotNull(reading.weight)

        assertEquals("0xC0", reading.frameName)
        assertEquals(2577, reading.productId)
        assertEquals(0x2E, reading.seqNo)
        assertFalse(reading.locked)
        assertFalse(reading.encrypted)
        assertEquals(ScaleUnit.KG, weight.unit)
        assertEquals(2, weight.decimals)
        assertEquals("123.20", weight.display)
        assertEquals(123_200.0, weight.grams, 0.5)
        // The 4-bit nutrition table read this same frame as 3.5, the worst bug in the old app.
        assertTrue(weight.grams > 100_000.0)
    }

    @Test
    fun `units convert through the same factors the vendor broadcasts with`() {
        assertEquals(0.5, ScaleUnit.JIN.toKg, 1e-9)
        assertEquals(0.4535924, ScaleUnit.LB.toKg, 1e-9)
        assertEquals(6.3502936, ScaleUnit.ST.toKg, 1e-6)

        val jin = requireNotNull(ScaleProtocol.parse(c0(0x0C), "AA:BB:CC:DD:EE:FF", null)).weight
        assertEquals(ScaleUnit.JIN, jin!!.unit)
        assertEquals(61_600.0, jin.grams, 0.5)

        val pound = requireNotNull(ScaleProtocol.parse(c0(0x14), "AA:BB:CC:DD:EE:FF", null)).weight
        assertEquals(ScaleUnit.LB, pound!!.unit)
        assertEquals(123.20 * 0.4535924 * 1000.0, pound.grams, 0.5)
    }

    @Test
    fun `stones keep the device's whole-stones and tenth-of-a-pound split`() {
        // 12 stone 13.0 lb: the low byte is 130 tenths of a pound, not a decimal weight.
        val stone = ScaleProtocol.decode(12.toByte(), 130.toByte(), 0x18.toByte()) // unit bits = 11
        assertEquals(ScaleUnit.ST, stone.unit)
        assertEquals("12:13.0", stone.display)
        assertEquals((12 * 14 + 13.0) * 0.4535924 * 1000.0, stone.grams, 0.5)
    }

    @Test
    fun `a no-decimal frame shows a whole number and a one-decimal frame shows tenths`() {
        val whole = ScaleProtocol.decode(0x00, 0x4B, 0x02.toByte()) // bits2..1 = 01
        assertEquals(0, whole.decimals)
        assertEquals("75", whole.display)
        assertEquals(75_000.0, whole.grams, 0.5)

        val tenth = ScaleProtocol.decode(0x02, 0xE6.toByte(), 0x00.toByte()) // bits2..1 = 00 -> one decimal
        assertEquals(1, tenth.decimals)
        assertEquals("74.2", tenth.display)
    }

    @Test
    fun `a negative frame keeps the minus on the display and the grams`() {
        val negative = ScaleProtocol.decode(0x30, 0x20, 0xA4.toByte()) // bit7 set
        assertEquals("-123.20", negative.display)
        assertEquals(-123_200.0, negative.grams, 0.5)
    }

    @Test
    fun `unknown headers and short frames are ignored rather than guessed`() {
        assertNull(ScaleProtocol.parse(byteArrayOf(0x01, 0x02, 0x03, 0x04), "AA:BB", null))
        assertNull(ScaleProtocol.parse(byteArrayOf(0xC0.toByte(), 0x01), "AA:BB", null))
        assertNull(ScaleProtocol.parse(null, "AA:BB", null))

        val ca = byteArrayOf(
            0xCA.toByte(), 0x01, 0x08, 0x24, 0x02, 0x30, 0x20, 0x00, 0x00, 0x0A, 0x11,
        )
        val nutrition = requireNotNull(ScaleProtocol.parse(ca, "AA:BB:CC:DD:EE:FF", null))
        assertEquals("0xCA", nutrition.frameName)
        assertEquals(2577, nutrition.productId)
        assertEquals("123.20", requireNotNull(nutrition.weight).display)
        // 0xCA carries no impedance and never needs a key.
        assertNull(nutrition.impedanceOhms)
        assertFalse(nutrition.encrypted)

        val caWrongSubcmd = ca.copyOf().also { it[4] = 0x01 }
        assertNull(ScaleProtocol.parse(caWrongSubcmd, "AA:BB:CC:DD:EE:FF", null))

        // 0xB0: [B0][seq][wHi][wLo][prodHi][prodLo][prop]
        val b0 = byteArrayOf(0xB0.toByte(), 0x07, 0x02, 0xE6.toByte(), 0x0A, 0x11, 0x25.toByte())
        val legacy = requireNotNull(ScaleProtocol.parse(b0, "AA:BB:CC:DD:EE:FF", null))
        assertEquals(7, legacy.seqNo)
        assertEquals(2577, legacy.productId)
        assertTrue(legacy.locked)
        assertEquals("7.42", requireNotNull(legacy.weight).display)
    }

    @Test
    fun `the last manufacturer record of the ad structure is the frame`() {
        val record = byteArrayOf(
            0x02, 0x01, 0x06,                               // flags
            0x03, 0x03, 0xB3.toByte(), 0xEC.toByte(),       // 16-bit service uuid
            0x05, 0xFF.toByte(), 0x01, 0x02, 0x03, 0x04,    // an earlier manufacturer record
            0x0A, 0xFF.toByte(),                            // len = type + 9 frame bytes
            0xC0.toByte(), 0x2E, 0x30, 0x20, 0x17, 0x70, 0x0A, 0x11, 0x24,
        )
        val payload = requireNotNull(ScaleProtocol.manufacturerPayload(record))
        assertEquals("C0 2E 30 20 17 70 0A 11 24", ScaleProtocol.hex(payload))

        assertNull(ScaleProtocol.manufacturerPayload(byteArrayOf(0x02, 0x01)))
        assertNull(ScaleProtocol.manufacturerPayload(null))
        assertEquals("", ScaleProtocol.hex(null))
    }
}
