package dev.amor.emomonitor.ui.atmosphere

import androidx.compose.ui.graphics.Color
import dev.amor.emomonitor.data.AirLevel

data class HazeTrio(val a: Color, val b: Color, val c: Color)

private val LightTrios = mapOf(
    AirLevel.FRESH to HazeTrio(Color(0xFFCFE6FF), Color(0xFFDDF5EE), Color(0xFFECE8FF)),
    AirLevel.NORMAL to HazeTrio(Color(0xFFD3F0D6), Color(0xFFE6F6D8), Color(0xFFD6EEF4)),
    AirLevel.LIGHT to HazeTrio(Color(0xFFFFEDB8), Color(0xFFFFE2C2), Color(0xFFF1F0CF)),
    AirLevel.MEDIUM to HazeTrio(Color(0xFFFFD6B0), Color(0xFFFFE0CC), Color(0xFFF3D5C2)),
    AirLevel.HEAVY to HazeTrio(Color(0xFFFFC4BE), Color(0xFFF5CDBE), Color(0xFFEBC6CF)),
    AirLevel.SEVERE to HazeTrio(Color(0xFFE0CAEA), Color(0xFFD6C2DC), Color(0xFFEAC6D2)),
    AirLevel.HAZARD to HazeTrio(Color(0xFFC8C4C1), Color(0xFFB9B2AD), Color(0xFFD6CCC3)),
)

private val NoReading = HazeTrio(Color(0xFFE6EBF2), Color(0xFFEEF0F4), Color(0xFFE9E6F0))
private val NoReadingDark = Color(0xFF8E8E93)

/** 深色模式不另配色，统一用等级色压暗，避免高饱和色团在黑底上发光。 */
fun hazeTrio(level: AirLevel?, dark: Boolean): HazeTrio {
    if (dark) {
        val base = level?.color ?: NoReadingDark
        return HazeTrio(
            base.copy(alpha = 0.30f),
            base.copy(alpha = 0.22f),
            base.copy(alpha = 0.16f),
        )
    }
    return level?.let { LightTrios[it] } ?: NoReading
}
