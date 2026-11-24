package dev.amor.emomonitor.ui.now

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.data.Phase
import dev.amor.emomonitor.ui.AnimatableFloat
import dev.amor.emomonitor.ui.BatteryGlyph
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.enter
import dev.amor.emomonitor.ui.tap

private val Connected = Color(0xFF43A047)
private val Waiting = Color(0xFFE0A800)
private val Idle = Color(0xFF9A9AA0)

@Composable
fun TopBar(
    state: EmoUiState,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
    progress: AnimatableFloat,
    playEnter: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            .graphicsLayer {
                // 抽屉展开过一半后顶栏让位给紧凑标题。
                alpha = ((0.5f - progress.value) * 2f).coerceIn(0f, 1f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCapsule(state, onReconnect, Modifier.enter(0, playEnter))
        Spacer(Modifier.weight(1f))
        if (state.battery != null) {
            BorderedCapsule {
                BatteryGlyph(
                    level = state.battery,
                    modifier = Modifier.size(width = 20.dp, height = 11.dp),
                )
                Text(
                    text = "${state.battery}%",
                    fontSize = 15.sp,
                    fontFamily = Manrope,
                    color = palette.ink,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        SettingsButton(onSettings, Modifier.enter(1, playEnter))
    }
}

@Composable
private fun StatusCapsule(
    state: EmoUiState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val (label, dot) = when (state.phase) {
        Phase.IDLE -> "未连接" to Idle
        Phase.SCANNING -> "搜索设备" to Waiting
        Phase.CONNECTING -> "连接中" to Waiting
        Phase.STREAMING -> "已连接" to Connected
        Phase.ERROR -> "连接中断 · 重试" to palette.danger
    }

    val breathe = remember { Animatable(1f) }
    LaunchedEffect(state.phase) {
        if (state.phase != Phase.STREAMING) {
            breathe.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            breathe.animateTo(0.35f, tween(1400))
            breathe.animateTo(1f, tween(1400))
        }
    }

    BorderedCapsule(
        modifier = modifier
            .then(if (state.phase == Phase.ERROR) Modifier.tap(onReconnect) else Modifier),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = breathe.value }
                .background(dot, CircleShape),
        )
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = Manrope,
            color = palette.ink,
            modifier = Modifier.padding(start = 7.dp),
        )
        if (state.usingMock) {
            Text(
                text = "模拟",
                fontSize = 15.sp,
                fontFamily = Manrope,
                color = palette.inkSoft,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun BorderedCapsule(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = Emo.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(palette.glassFill)
            .border(1.dp, palette.glassStroke, shape)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    val shape = CircleShape
    Box(
        modifier
            .size(40.dp)
            .clip(shape)
            .background(palette.glassFill)
            .border(1.dp, palette.glassStroke, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.6.dp.toPx()
            val upper = size.height * 0.33f
            val lower = size.height * 0.67f
            drawLine(palette.ink, Offset(0f, upper), Offset(size.width, upper), stroke, StrokeCap.Round)
            drawLine(palette.ink, Offset(0f, lower), Offset(size.width, lower), stroke, StrokeCap.Round)
            drawCircle(palette.ink, 2.6.dp.toPx(), Offset(size.width * 0.34f, upper))
            drawCircle(palette.ink, 2.6.dp.toPx(), Offset(size.width * 0.64f, lower))
        }
    }
}
