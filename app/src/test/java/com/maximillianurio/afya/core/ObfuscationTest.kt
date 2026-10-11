package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 15-bit rotate-and-XOR obfuscation, checked against an inverse written from the round
 * description in the handoff notes rather than against [ScaleProtocol.decrypt15] itself.
 */
class ObfuscationTest {

    private fun rot15(value: Int, left: Int) =
        ((value shl left) and 0x7FFF) or (value ushr (15 - left))

    private fun unsigned(bytes: ByteArray, index: Int) = bytes[index].toInt() and 0xFF

    /**
     * The writer the device would use, i.e. the exact inverse of the reader: XOR k3, rotate back
     * 2, XOR k2, rotate back 3, XOR k1, rotate back 1, XOR k0.
     */
    private fun encrypt15(plain: Int, key: ByteArray): Int {
        val sign = (plain ushr 15) and 1
        var v = (plain and 0x7FFF) xor unsigned(key, 3)
        v = rot15(v, 2) xor unsigned(key, 2)
        v = rot15(v, 3) xor unsigned(key, 1)
        v = rot15(v, 1) xor unsigned(key, 0)
        return (v and 0x7FFF) or (sign shl 15)
    }

    private fun keyOf(hex: String) = requireNotNull(ScaleProtocol.hexToBytes(hex))

    @Test
    fun `decrypting what the device would broadcast gives the original number back`() {
        val key = keyOf("A1B2C3D4")
        for (plain in 0..0x7FFF) {
            assertEquals(plain, ScaleProtocol.decrypt15(encrypt15(plain, key), key))
        }
    }

    @Test
    fun `the sign bit travels through untouched`() {
        val key = keyOf("A1B2C3D4")
        for (plain in listOf(0x8000, 0x8001, 0xFFFF, 0x8FFF)) {
            val cipher = encrypt15(plain, key)
            assertEquals("sign survives encryption", 1, cipher ushr 15)
            assertEquals(plain, ScaleProtocol.decrypt15(cipher, key))
        }
        val unsignedCipher = encrypt15(12320, key)
        assertEquals(0, unsignedCipher ushr 15)
        assertEquals(12320, ScaleProtocol.decrypt15(unsignedCipher, key))
    }

    @Test
    fun `obfuscation is not identity and a short key yields nothing`() {
        val key = keyOf("A1B2C3D4")
        val cipher = encrypt15(12320, key)
        assertNotEquals(12320, cipher)
        assertEquals(0, ScaleProtocol.decrypt15(12320, byteArrayOf(1, 2, 3)))
        assertEquals(0, ScaleProtocol.decrypt15(12320, ByteArray(0)))
    }

    @Test
    fun `a key is four bytes of hex and anything shorter is rejected`() {
        assertTrue(ScaleProtocol.hexToBytes("a1b2c3d4")!!.contentEquals(keyOf("A1B2C3D4")))
        assertTrue(ScaleProtocol.hexToBytes("A1 B2:C3-D4")!!.contentEquals(keyOf("A1B2C3D4")))
        assertEquals(0xA1.toByte(), keyOf("A1B2C3D4")[0])
        assertEquals(0xD4.toByte(), keyOf("A1B2C3D4")[3])
        assertNull(ScaleProtocol.hexToBytes("A1B2C3"))
        assertNull(ScaleProtocol.hexToBytes(null))
        assertNull(ScaleProtocol.hexToBytes("zzzzzzzz"))
    }

    @Test
    fun `an encrypted body frame decodes weight and impedance with the server key`() {
        val key = keyOf("A1B2C3D4")
        val weightPlain = 6850 // 68.50 kg at two decimals
        val impedancePlain = 5000 or 0x8000 // bit7 of byte 4 marks the frame obfuscated
        val payload = byteArrayOf(
            0xC0.toByte(), 0x01,
            0x00, 0x00,                                  // encrypted weight, filled below
            ((impedancePlain shr 8) and 0xFF).toByte(), (impedancePlain and 0xFF).toByte(),
            0x0A, 0x11, 0x24.toByte(),                  // id 2577, kg / 2 decimals / unlocked
            0x00, 0x00,                                  // encrypted MAC, filled below
            0x11, 0x22, 0x33, 0x44,                      // key bytes, also the MAC tail
        )
        val weightCipher = encrypt15(weightPlain, key)
        payload[2] = ((weightCipher shr 8) and 0xFF).toByte()
        payload[3] = (weightCipher and 0xFF).toByte()
        val impedanceCipher = encrypt15(impedancePlain, key)
        payload[4] = (((impedanceCipher shr 8) and 0xFF) or 0x80).toByte()
        payload[5] = (impedanceCipher and 0xFF).toByte()

        val reading = requireNotNull(
            ScaleProtocol.parse(payload, "AA:BB:CC:DD:EE:FF", ScaleProtocol.hex(key))
        )
        assertTrue(reading.encrypted)
        assertFalse(reading.keyMissing)
        assertEquals("68.50", requireNotNull(reading.weight).display)
        assertEquals(68_500.0, reading.weight!!.grams, 0.5)
        assertEquals(500.0, requireNotNull(reading.impedanceOhms), 0.05)
    }

    @Test
    fun `an encrypted frame without a key reports the gap instead of a weight`() {
        val payload = byteArrayOf(
            0xC0.toByte(), 0x01, 0x12, 0x34, 0x97.toByte(), 0x56, 0x0A, 0x11, 0x24.toByte(),
            0xAB.toByte(), 0xCD.toByte(), 0x11, 0x22, 0x33, 0x44,
        )
        val reading = requireNotNull(ScaleProtocol.parse(payload, "AA:BB:CC:DD:EE:FF", null))
        assertTrue(reading.encrypted)
        assertTrue(reading.keyMissing)
        assertNull(reading.weight)
        assertNull(reading.impedanceOhms)
    }

    @Test
    fun `the real mac is recovered from the frame itself`() {
        val mac = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val tail = mac.copyOfRange(2, 6)
        val payload = byteArrayOf(
            0xC0.toByte(), 0x01, 0x12, 0x34, 0x97.toByte(), 0x56, 0x0A, 0x11, 0x24.toByte(),
            0x00, 0x00,                                  // encrypted head of the MAC
            mac[2], mac[3], mac[4], mac[5],
        )
        val cipher = encrypt15(((mac[0].toInt() and 0xFF) shl 8) or (mac[1].toInt() and 0xFF), tail)
        payload[9] = ((cipher shr 8) and 0xFF).toByte()
        payload[10] = (cipher and 0xFF).toByte()

        assertEquals("11:22:33:44:55:66", ScaleProtocol.macFromPayload(payload, "ZZ:ZZ"))
        // Anything that is not an obfuscated 0xC0 frame keeps the advertised address.
        assertEquals("ZZ:ZZ", ScaleProtocol.macFromPayload(payload.copyOf().also { it[4] = 0x17 }, "ZZ:ZZ"))
        assertEquals("ZZ:ZZ", ScaleProtocol.macFromPayload(payload.copyOf().also { it[0] = 0xB0.toByte() }, "ZZ:ZZ"))
        assertEquals("ZZ:ZZ", ScaleProtocol.macFromPayload(payload.copyOfRange(0, 14), "ZZ:ZZ"))
        assertEquals("ZZ:ZZ", ScaleProtocol.macFromPayload(null, "ZZ:ZZ"))
    }

    @Test
    fun `big-endian reads and mac formatting match the vendor's helpers`() {
        val bytes = byteArrayOf(0x0A, 0x11, 0x22, 0x33)
        assertEquals(2577, ScaleProtocol.beInt(bytes, 0, 2))
        assertEquals(0x0A112233, ScaleProtocol.beInt(bytes))
        assertEquals(0x1122, ScaleProtocol.beInt(bytes, 1, 2))
        assertEquals("0A 11 22 33", ScaleProtocol.hex(bytes))
        assertEquals("0A:11:22:33", ScaleProtocol.macToString(bytes))
    }
}
