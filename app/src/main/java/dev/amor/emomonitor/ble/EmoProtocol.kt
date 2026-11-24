package dev.amor.emomonitor.ble

import dev.amor.emomonitor.data.EmoReading
import java.util.UUID

object EmoProtocol {
    const val DEVICE_NAME = "PM2.5TESTER"

    val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHAR_DATA: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val CHAR_INTERVAL: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val CHAR_BATTERY: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private const val PM_OFFSET = 10
    private const val COUNT_OFFSET = 12
    private const val PM_SCALE = 1.265
    private const val COUNT_SCALE = 2.56

    fun parse(frame: ByteArray): EmoReading? {
        if (frame.size < COUNT_OFFSET + 2) return null
        val pm = readU16(frame, PM_OFFSET) * PM_SCALE
        val count = (readU16(frame, COUNT_OFFSET) * COUNT_SCALE).toInt().coerceAtLeast(0)
        return EmoReading(pm25 = pm, particles = count)
    }

    // 设备按大端存放；原厂 f/b.java 交换两字节后按小端读，结果等价于大端。
    private fun readU16(frame: ByteArray, offset: Int): Int =
        ((frame[offset].toInt() and 0xFF) shl 8) or (frame[offset + 1].toInt() and 0xFF)
}
