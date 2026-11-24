package dev.amor.emomonitor.ui.now

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.levelColor

/** 七档边界；最后一档的上界只在刻度映射里使用（设备量程到 500）。 */
private val Bounds = doubleArrayOf(0.0, 35.0, 75.0, 115.0, 150.0, 250.0, 350.0, 500.0)
private val Thresholds = listOf("35", "75", "115", "150", "250", "350")

/**
 * 分段线性映射：落在第 i 档时 fraction = (i + (v−lo)/(hi−lo)) / 7。
 * 抽成纯函数是为了能直接对边界值做单测，避免视觉与分级逻辑漂移。
 */
fun levelFraction(pm25: Double): Float {
    for (i in 0 until 7) {
        val lo = Bounds[i]
        val hi = Bounds[i + 1]
        if (pm25 < hi) {
            val inner = if (hi > lo) (pm25 - lo) / (hi - lo) else 0.0
            return ((i + inner) / 7.0).toFloat().coerceIn(0f, 1f)
        }
    }
    return 1f
}

@Composable
fun LevelScale(pm25: Double?, level: AirLevel?, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    val target = pm25?.let { levelFraction(it) } ?: 0f
    val marker = remember { Animatable(target) }
    LaunchedEffect(pm25) {
        if (pm25 != null) marker.animateTo(target, Motion.soft())
    }

    // 每到一个新读数，标记外扩一圈；这是「数据还活着」的唯一提示，不做常驻闪烁。
    val heartbeat = remember { Animatable(1f) }
    LaunchedEffect(pm25) {
        if (pm25 == null) return@LaunchedEffect
        heartbeat.snapTo(0f)
        heartbeat.animateTo(1f, tween(900, easing = LinearEasing))
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            color = palette.inkSoft,
        )
    }

    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val barHeight = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val segment = (size.width - gap * 6f) / 7f

        clipPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = barHeight,
                        cornerRadius = CornerRadius(barHeight / 2f),
                    ),
                )
            },
        ) {
            AirLevel.entries.forEachIndexed { index, entry ->
                val active = entry == level
                drawRect(
                    color = palette.levelColor(entry).copy(alpha = if (active) 1f else 0.35f),
                    topLeft = Offset(index * (segment + gap), 0f),
                    size = androidx.compose.ui.geometry.Size(segment, barHeight),
                )
            }
        }

        if (pm25 != null && level != null) {
            val x = marker.value * size.width
            val y = barHeight / 2f
            if (heartbeat.value < 1f) {
                val k = heartbeat.value
                drawCircle(
                    color = palette.ink.copy(alpha = 0.35f * (1f - k)),
                    radius = 6.dp.toPx() + 18.dp.toPx() * k,
                    center = Offset(x, y),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            drawCircle(color = palette.ink, radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(
                color = palette.paper,
                radius = 6.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Thresholds.forEachIndexed { index, label ->
            val layout = measurer.measure(AnnotatedString(label), labelStyle)
            val cx = (index + 1) * (segment + gap) - gap / 2f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(cx - layout.size.width / 2f, barHeight + 6.dp.toPx()),
            )
        }
    }
}
