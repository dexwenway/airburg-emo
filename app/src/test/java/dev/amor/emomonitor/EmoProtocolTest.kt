package dev.amor.emomonitor

import dev.amor.emomonitor.ble.EmoProtocol
import dev.amor.emomonitor.data.AirLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmoProtocolTest {

    private fun String.hex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun parsesFrameCapturedFromDevice() {
        val frame = "002800340038001e002b003422da07a400ed".hex()
        val reading = EmoProtocol.parse(frame)!!
        assertEquals(65.78, reading.pm25, 0.001)
        assertEquals(22840, reading.particles)
    }

    @Test
    fun parsesSecondCapturedFrame() {
        val frame = "0027003e0041001f0031003b000000000000".hex()
        val reading = EmoProtocol.parse(frame)!!
        assertEquals(74.635, reading.pm25, 0.001)
        assertEquals(0, reading.particles)
    }

    @Test
    fun ignoresTruncatedFrame() {
        assertNull(EmoProtocol.parse(ByteArray(13)))
    }

    @Test
    fun levelBoundariesMatchAqiBands() {
        assertEquals(AirLevel.FRESH, AirLevel.of(34.9))
        assertEquals(AirLevel.NORMAL, AirLevel.of(35.0))
        assertEquals(AirLevel.NORMAL, AirLevel.of(74.9))
        assertEquals(AirLevel.LIGHT, AirLevel.of(75.0))
        assertEquals(AirLevel.MEDIUM, AirLevel.of(115.0))
        assertEquals(AirLevel.HEAVY, AirLevel.of(150.0))
        assertEquals(AirLevel.SEVERE, AirLevel.of(250.0))
        assertEquals(AirLevel.HAZARD, AirLevel.of(350.0))
    }
}
