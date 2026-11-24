package dev.amor.emomonitor.ui.now

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.data.EmoReading
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.data.adviceFor
import dev.amor.emomonitor.ui.AnimatableFloat
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.RollingNumber
import dev.amor.emomonitor.ui.enter
import dev.amor.emomonitor.ui.levelColor
import kotlin.math.roundToInt

private val Neutral = Color(0xFF9A9AA0)

@Composable
fun InfoBlock(
    state: EmoUiState,
    progress: AnimatableFloat,
    playEnter: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val reading = state.reading
    val level = reading?.let { AirLevel.of(it.pm25) }
    val pm25 = reading?.pm25
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val numberSize = (screenWidth * 0.36f).coerceIn(108f, 150f).sp

    val targetWeight = when (level) {
        AirLevel.FRESH, AirLevel.NORMAL -> 500
        AirLevel.LIGHT, AirLevel.MEDIUM -> 600
        AirLevel.HEAVY, AirLevel.SEVERE -> 700
        AirLevel.HAZARD -> 800
        null -> 500
    }
    val weightAnim = animateFloatAsState(targetWeight.toFloat(), Motion.emphasized(), label = "weight")
    // 量化到 20 的倍数：字重过渡期间只在跨档时重组，不会每帧新建 FontFamily。
    val weight by remember { derivedStateOf { (weightAnim.value / 20f).roundToInt() * 20 } }

    val haptic = LocalHapticFeedback.current
    var lastLevel by remember { mutableStateOf(level) }
    LaunchedEffect(level) {
        if (level != null && lastLevel != null && level != lastLevel) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        lastLevel = level
    }

    Column(
        modifier.graphicsLayer {
            val p = progress.value.coerceIn(0f, 1f)
            alpha = (1f - p * 2f).coerceIn(0f, 1f)
            translationY = p * 48.dp.toPx()
            transformOrigin = TransformOrigin(0f, 0f)
            val scale = 1f - p * 0.06f
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text = "PM2.5",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Manrope,
            letterSpacing = 0.12.em,
            color = palette.inkSoft,
            modifier = Modifier.enter(1, playEnter),
        )
        Spacer(Modifier.height(2.dp))
        RollingNumber(
            value = pm25,
            integerSize = numberSize,
            weight = weight,
            unit = if (pm25 == null) null else "µg/m³",
            unitColor = palette.inkSoft,
            integerColor = palette.ink,
            softColor = palette.inkSoft,
            modifier = Modifier.enter(1, playEnter),
        )

        Row(
            Modifier
                .padding(top = 6.dp)
                .enter(2, playEnter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(level?.let { palette.levelColor(it) } ?: Neutral)
                    .border(1.dp, palette.hairline, CircleShape),
            )
            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    (
                        slideInVertically(Motion.emphasized()) { it } +
                            fadeIn(Motion.emphasized())
                        ).togetherWith(
                        slideOutVertically(Motion.emphasized()) { -it } +
                            fadeOut(Motion.emphasized()),
                    ).using(SizeTransform(clip = false))
                },
                label = "levelWord",
                modifier = Modifier.padding(start = 8.dp),
            ) { current ->
                Text(
                    text = current?.label ?: "等待读数",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Manrope,
                    letterSpacing = 0.04.em,
                    color = if (current == null) palette.inkSoft else palette.ink,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Crossfade(
            targetState = reading?.let { adviceFor(it.pm25) } ?: "正在获取读数",
            animationSpec = Motion.standard(),
            label = "advice",
            modifier = Modifier
                .fillMaxWidth()
                .enter(3, playEnter),
        ) { advice ->
            Text(
                text = advice,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Manrope,
                lineHeight = 23.sp,
                color = palette.ink.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Crossfade(
            targetState = trendHint(state.recent, pm25),
            animationSpec = Motion.standard(),
            label = "trend",
            modifier = Modifier
                .padding(top = 2.dp)
                .enter(4, playEnter),
        ) { hint ->
            Text(
                text = hint,
                fontSize = 13.sp,
                fontFamily = Manrope,
                color = palette.inkSoft,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(14.dp))
        LevelScale(
            pm25 = pm25,
            level = level,
            modifier = Modifier.enter(5, playEnter),
        )
    }
}

/** 趋势提示沿用旧首页的逻辑，只是搬到了新信息块里。 */
private fun trendHint(points: List<EmoReading>, current: Double?): String {
    if (current == null) return "等待数据"
    val slope = slopePerMinute(points) ?: return "读数稳定"
    if (slope < -0.3 && current > 35) {
        val minutes = ((current - 35) / -slope).roundToInt().coerceIn(1, 180)
        return "正在下降 · 约 $minutes 分钟后低于 35"
    }
    if (slope > 0.3) {
        if (slope > 20) return "读数波动较大"
        return "正在上升 · 每分钟 +%.1f".format(slope)
    }
    return "读数稳定 · 每分钟 %+.1f".format(slope)
}

private fun slopePerMinute(points: List<EmoReading>): Double? {
    val window = points.takeLast(30)
    if (window.size < 6) return null
    val t0 = window.first().timestamp
    val xs = window.map { (it.timestamp - t0) / 60_000.0 }
    val ys = window.map { it.pm25 }
    val meanX = xs.average()
    val meanY = ys.average()
    val denom = xs.sumOf { (it - meanX) * (it - meanX) }
    if (denom == 0.0) return null
    return xs.zip(ys).sumOf { (x, y) -> (x - meanX) * (y - meanY) } / denom
}

/** 顶栏之下的紧凑标题：抽屉展开后与等级色、读数一起出现在面板上方的留白里。 */
@Composable
fun CompactHeader(
    state: EmoUiState,
    progress: AnimatableFloat,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val reading = state.reading
    val level = reading?.let { AirLevel.of(it.pm25) }
    Row(
        modifier.graphicsLayer {
            alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(level?.let { palette.levelColor(it) } ?: Neutral)
                .border(1.dp, palette.hairline, CircleShape),
        )
        RollingNumber(
            value = reading?.pm25,
            integerSize = 28.sp,
            weight = 600,
            unit = null,
            unitColor = palette.inkSoft,
            integerColor = palette.ink,
            softColor = palette.inkSoft,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = level?.label ?: "",
            fontSize = 16.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
