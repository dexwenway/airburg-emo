package dev.amor.emomonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 电池按电量比例填充；≤20% 时只有填充转警示红，外框保持中性色。 */
@Composable
fun BatteryGlyph(level: Int, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    val frame = palette.ink
    val fill = if (level <= 20) palette.danger else frame
    Canvas(modifier) {
        val stroke = 1.2.dp.toPx()
        val nubWidth = size.width * 0.10f
        val gap = 1.dp.toPx()
        val bodyWidth = size.width - nubWidth - gap
        val bodyHeight = size.height
        val corner = bodyHeight * 0.26f

        drawRoundRect(
            color = frame,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, bodyHeight - stroke),
            cornerRadius = CornerRadius(corner),
            style = Stroke(stroke),
        )

        val nubHeight = bodyHeight * 0.42f
        drawRoundRect(
            color = frame,
            topLeft = Offset(bodyWidth + gap, (bodyHeight - nubHeight) / 2),
            size = Size(nubWidth, nubHeight),
            cornerRadius = CornerRadius(nubWidth / 2),
        )

        val inset = stroke + 1.dp.toPx()
        val innerWidth = (bodyWidth - inset * 2) * (level.coerceIn(0, 100) / 100f)
        if (innerWidth <= 0f) return@Canvas
        drawRoundRect(
            color = fill,
            topLeft = Offset(inset, inset),
            size = Size(innerWidth, bodyHeight - inset * 2),
            cornerRadius = CornerRadius(corner * 0.55f),
        )
    }
}
