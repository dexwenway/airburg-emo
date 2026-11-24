package dev.amor.emomonitor.data

import androidx.compose.ui.graphics.Color

data class EmoReading(
    val pm25: Double,
    val particles: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class AirLevel(val label: String, val upperBound: Double, val color: Color) {
    FRESH("清新有爱", 35.0, Color(0xFF3F86C9)),
    NORMAL("空气正常", 75.0, Color(0xFF43A047)),
    LIGHT("轻度污染", 115.0, Color(0xFFE0A800)),
    MEDIUM("中度污染", 150.0, Color(0xFFE07B39)),
    HEAVY("重度污染", 250.0, Color(0xFFD64545)),
    SEVERE("严重污染", 350.0, Color(0xFF8E5BA6)),
    HAZARD("极度污染", Double.MAX_VALUE, Color(0xFF2B2B2B));

    companion object {
        fun of(pm25: Double): AirLevel = entries.first { pm25 < it.upperBound }
    }
}

enum class Phase { IDLE, SCANNING, CONNECTING, STREAMING, ERROR }

data class EmoUiState(
    val phase: Phase = Phase.IDLE,
    val reading: EmoReading? = null,
    val battery: Int? = null,
    val recent: List<EmoReading> = emptyList(),
    val message: String? = null,
    val usingMock: Boolean = false,
)
