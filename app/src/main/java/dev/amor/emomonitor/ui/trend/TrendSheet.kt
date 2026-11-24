package dev.amor.emomonitor.ui.trend

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.data.EmoReading
import dev.amor.emomonitor.data.EmoRepository
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.ui.AnimatableFloat
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.RollingNumber
import dev.amor.emomonitor.ui.numStyle
import dev.amor.emomonitor.ui.levelColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Peek = 168.dp
private val ExpandedTop = 76.dp
private const val FLING_DP_PER_SECOND = 800f
private const val DAY_MS = 24L * 60 * 60 * 1000

/** 面板的四个范围；顺序即分段控件里的顺序。 */
enum class TrendRange(val label: String) {
    LIVE("实时"),
    TODAY("今天"),
    YESTERDAY("昨天"),
    WEEK("7 天"),
}

/**
 * 自实现的底部抽屉：progress 无上下界，拖出范围的部分乘 0.2 做橡皮筋，
 * 松手时把手指速度作为 initialVelocity 交给 spring，所以动画能接住手势、也能被再次抓住。
 */
@Composable
fun TrendSheet(
    state: EmoUiState,
    progress: AnimatableFloat,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val expandedTop = statusTop + ExpandedTop
        val restTop = maxHeight - navBottom - Peek
        val span = (restTop - expandedTop).coerceAtLeast(1.dp)
        val spanPx = with(density) { span.toPx() }
        val sheetHeight = maxHeight - expandedTop

        val scope = rememberCoroutineScope()
        val collapsed by remember { derivedStateOf { progress.value < 0.15f } }
        val expanded by remember { derivedStateOf { progress.value > 0.5f } }

        BackHandler(enabled = expanded) {
            scope.launch { progress.animateTo(0f, Motion.sheet()) }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch {
                progress.snapTo(rubber(progress.value, progress.value - delta / spanPx))
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, ((1f - progress.value) * spanPx).roundToInt()) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val v = -velocity / spanPx
                        val target = when {
                            abs(v) > FLING_DP_PER_SECOND / span.value -> if (v > 0f) 1f else 0f
                            progress.value > 0.5f -> 1f
                            else -> 0f
                        }
                        progress.animateTo(target, Motion.sheet(), initialVelocity = v)
                    },
                )
                .pointerInput(collapsed) {
                    if (!collapsed) return@pointerInput
                    detectTapGestures {
                        scope.launch { progress.animateTo(1f, Motion.sheet()) }
                    }
                }
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val radius = lerp(32f, 28f, p).dp
                    shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                    clip = true
                }
                .drawBehind {
                    val p = progress.value.coerceIn(0f, 1f)
                    drawRoundRect(
                        color = palette.sheet,
                        cornerRadius = CornerRadius(lerp(32f, 28f, p).dp.toPx()),
                    )
                },
        ) {
            SheetContent(state, progress, navBottom)
        }
    }
}

@Composable
private fun SheetContent(state: EmoUiState, progress: AnimatableFloat, navBottom: androidx.compose.ui.unit.Dp) {
    val palette = Emo.colors
    val collapsed by remember { derivedStateOf { progress.value < 0.05f } }
    var range by remember { mutableStateOf(TrendRange.LIVE) }
    // 露头状态固定显示实时：收起后把范围复位。
    LaunchedEffect(collapsed) {
        if (collapsed) range = TrendRange.LIVE
    }

    var history by remember { mutableStateOf<List<EmoReading>>(emptyList()) }
    LaunchedEffect(range) {
        if (range == TrendRange.LIVE) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val today = startOfToday()
        val (from, to, hourly) = when (range) {
            TrendRange.TODAY -> Triple(today, now, false)
            TrendRange.YESTERDAY -> Triple(today - DAY_MS, today, false)
            TrendRange.WEEK -> Triple(now - 7 * DAY_MS, now, true)
            TrendRange.LIVE -> Triple(today, now, false)
        }
        history = withContext(Dispatchers.IO) { EmoRepository.historyBetween(from, to, hourly) }
    }

    val readings = if (range == TrendRange.LIVE) state.recent.takeLast(90) else history
    val values = readings.map { it.pm25 }
    var scrubSlot by remember { mutableStateOf<Int?>(null) }
    val scrubSample = scrubSlot?.let { slot ->
        readings.getOrNull(slotToSample(slot, readings.size))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = navBottom),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            SheetHandle(progress)
        }
        Spacer(Modifier.height(10.dp))

        Segmented(
            selected = range.ordinal,
            onSelect = { index ->
                range = TrendRange.entries[index]
                scrubSlot = null
            },
            progress = progress,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Crossfade(
                targetState = scrubSample != null,
                animationSpec = Motion.quick(),
                label = "readingRow",
            ) { scrubbing ->
                val sample = scrubSample
                if (scrubbing && sample != null) {
                    ScrubRow(sample, range)
                } else {
                    RangeRow(state, range, readings)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        TrendChart(
            values = values,
            morphKey = range,
            morphOnChange = range != TrendRange.LIVE,
            progress = progress,
            level = readings.lastOrNull()?.let { AirLevel.of(it.pm25) },
            timeLabels = timeLabels(range, readings),
            onScrub = { scrubSlot = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .chartHeight(progress),
        )

        if (values.size >= 2) {
            Spacer(Modifier.height(14.dp))
            TrendStats(
                values = values,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            LevelRatio(
                counts = levelCounts(values),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun RangeRow(state: EmoUiState, range: TrendRange, readings: List<EmoReading>) {
    val palette = Emo.colors
    val subtitle = when (range) {
        TrendRange.LIVE -> "最近 3 分钟"
        TrendRange.TODAY -> "今天 · ${readings.size} 个采样"
        TrendRange.YESTERDAY -> "昨天 · ${readings.size} 个采样"
        TrendRange.WEEK -> "近 7 天 · ${readings.size} 个采样"
    }
    val meta = when (range) {
        TrendRange.LIVE -> state.reading?.let { "粒子 ${it.particles} /0.1L" } ?: "等待数据"
        TrendRange.WEEK -> "每小时平均"
        else -> "每分钟 1 条"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = meta,
            fontSize = 13.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
        )
    }
}

@Composable
private fun ScrubRow(sample: EmoReading, range: TrendRange) {
    val palette = Emo.colors
    val level = AirLevel.of(sample.pm25)
    val formatter = remember(range) {
        SimpleDateFormat(if (range == TrendRange.WEEK) "M/d HH:mm" else "HH:mm", Locale.US)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RollingNumber(
            value = sample.pm25,
            integerSize = 20.sp,
            weight = 600,
            unit = "µg/m³",
            unitColor = palette.inkSoft,
            integerColor = palette.ink,
            softColor = palette.inkSoft,
        )
        Text(
            text = "· ${level.label}",
            fontSize = 13.sp,
            fontFamily = Manrope,
            color = palette.levelColor(level),
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatter.format(Date(sample.timestamp)),
            style = numStyle(13.sp),
            color = palette.inkSoft,
        )
    }
}

/** 把手宽度 36 → 24dp，随展开收起连续变化。 */
@Composable
private fun SheetHandle(progress: AnimatableFloat) {
    val palette = Emo.colors
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = progress.value.coerceIn(0f, 1f)
                val width = lerp(36f, 24f, p).dp.roundToPx()
                val height = 4.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .background(palette.ink.copy(alpha = 0.18f), RoundedCornerShape(2.dp)),
    )
}

/** 图表高度 80 → 200dp，同一个 Composable 连续长高，保证露头与展开之间的视觉连续。 */
private fun Modifier.chartHeight(progress: AnimatableFloat): Modifier = layout { measurable, constraints ->
    val p = progress.value.coerceIn(0f, 1f)
    val height = lerp(80f, 200f, p).dp.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** 越界部分只跟上 0.2 的位移，手指松手后才知道自己被橡皮筋拉住了。 */
private fun rubber(current: Float, next: Float): Float = when {
    current < 0f || current > 1f -> current + (next - current) * 0.2f
    next in 0f..1f -> next
    next > 1f -> 1f + (next - 1f) * 0.2f
    else -> next * 0.2f
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 72 个槽位映射回真实采样；采样少时取最近的那个。 */
private fun slotToSample(slot: Int, count: Int): Int {
    if (count <= 1) return 0
    return (slot / 71f * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

private fun timeLabels(range: TrendRange, readings: List<EmoReading>): List<String> {
    if (range == TrendRange.LIVE) return listOf("3 分钟前", "1.5 分钟前", "现在")
    val first = readings.firstOrNull()?.timestamp
    val last = readings.lastOrNull()?.timestamp
    if (first == null || last == null) return emptyList()
    val formatter = SimpleDateFormat(if (range == TrendRange.WEEK) "M/d" else "HH:mm", Locale.US)
    return listOf(
        formatter.format(Date(first)),
        formatter.format(Date((first + last) / 2)),
        formatter.format(Date(last)),
    )
}
