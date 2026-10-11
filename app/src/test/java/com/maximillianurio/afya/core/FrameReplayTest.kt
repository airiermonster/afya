package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform card keeps the last weigh-in across a restart by replaying the frame it came from, so
 * that replay has to produce the device's own figure again rather than a re-derivation of it.
 */
class FrameReplayTest {

    private val mac = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `a stored 0xCA frame replays as the same locked figure`() {
        // 0xCA, sub-command 2, property 0x01 (kg, one decimal, locked), 1237 -> 123.7 kg.
        val payload = byteArrayOf(
            0xCA.toByte(), 0x01, 0x0B, 0x01, 0x02, 0x04, 0xD5.toByte(), 0, 0, 0, 1,
        )
        val live = ScaleProtocol.parse(payload, mac, null) ?: error("frame did not parse")
        val replayed = ScaleProtocol.parse(ScaleProtocol.payloadFromHex(live.rawHex), mac, null)
            ?: error("stored frame did not replay")

        assertTrue(live.locked)
        assertTrue(replayed.locked)
        val asLive = live.weight ?: error("live frame carried no weight")
        val asReplayed = replayed.weight ?: error("replayed frame carried no weight")
        assertEquals(asLive.display, asReplayed.display)
        assertEquals(asLive.grams, asReplayed.grams, 0.0)
        assertEquals(123.7, asReplayed.grams / 1000.0, 0.001)
    }
}
