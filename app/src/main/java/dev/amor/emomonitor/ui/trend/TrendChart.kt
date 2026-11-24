package dev.amor.emomonitor.ui.trend

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.ui.AnimatableFloat
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.levelColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** 曲线固定重采样到 72 点：切换范围时新旧两组都映射到同一组槽位，才能逐点插值变形。 */
private const val ShapePoints = 72

private val StepChoices = intArrayOf(10, 20, 25, 50, 100)

/** 纵轴：下限到上限 + 网格步长。上限下限都是步长的整数倍，标注必然是规整整数。 */
data class ChartAxis(val lo: Float, val hi: Float, val step: Int)

/**
 * 纵轴范围取 [min−余量, max+余量]，最小跨度 40（读数持平时曲线也不至于压成直线），
 * 下限不低于 0；步长从 10/20/25/50/100 里挑最接近 3 条内网格线的那个。
 */
fun chartAxis(minValue: Double, maxValue: Double): ChartAxis {
    val pad = maxOf(6.0, (maxValue - minValue) * 0.18)
    var lo = (minValue - pad).coerceAtLeast(0.0)
    var hi = maxValue + pad
    if (hi - lo < 40.0) {
        // 读数几乎持平时固定给 40 的跨度（3 条内网格线），不要再被取整撑大，
        // 否则几个 µg 的小起伏会缩成一条直线。
        val base = (floor(((lo + hi) / 2 - 20.0) / 10.0) * 10.0).coerceAtLeast(0.0)
        return ChartAxis(base.toFloat(), (base + 40.0).toFloat(), 10)
    }
    val step = StepChoices.minByOrNull { abs((hi - lo) / it - 4.0) } ?: 10
    return ChartAxis(
        lo = (floor(lo / step) * step).toFloat(),
        hi = (ceil(hi / step) * step).toFloat(),
        step = step,
    )
}

/**
 * 趋势曲线。范围切换时新旧形状逐点插值变形（[morphOnChange]），实时范围则靠新读数
 * 到达时整条左滑一个步长。纵向位置与纵轴都在 draw 阶段读取，不逐帧重组。
 */
@Composable
fun TrendChart(
    values: List<Double>,
    morphKey: Any,
    morphOnChange: Boolean,
    progress: AnimatableFloat,
    level: AirLevel?,
    timeLabels: List<String>,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(palette) {
        TextStyle(
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            color = palette.inkSoft,
        )
    }

    val fromShape = remember { mutableStateOf(FloatArray(0)) }
    val toShape = remember { mutableStateOf(FloatArray(0)) }
    val morph = remember { Animatable(1f) }

    LaunchedEffect(values, morphKey) {
        val next = resample(values)
        if (next.isEmpty() || !morphOnChange || toShape.value.isEmpty()) {
            fromShape.value = next
            toShape.value = next
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        // 先把「当前看起来的形状」定格成起点，再从它变形到新数据。
        fromShape.value = shownShape(fromShape.value, toShape.value, morph.value)
        toShape.value = next
        morph.snapTo(0f)
        morph.animateTo(1f, Motion.emphasized())
    }

    // 实时：新读数到达时曲线整体左滑一个步长。
    val shift = remember { Animatable(0f) }
    var lastSize by remember { mutableStateOf(0) }
    LaunchedEffect(values.size) {
        if (morphOnChange) {
            lastSize = values.size
            return@LaunchedEffect
        }
        if (values.size > lastSize && lastSize > 1) {
            shift.snapTo(1f)
            shift.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        }
        lastSize = values.size
    }

    val pulse = remember { Animatable(1f) }
    LaunchedEffect(values.lastOrNull(), values.size) {
        if (values.size < 2) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    val axis = remember(values.minOrNull(), values.maxOrNull()) {
        chartAxis(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 40.0)
    }
    val axisLo by animateFloatAsState(axis.lo, Motion.standard(), label = "axisLo")
    val axisHi by animateFloatAsState(axis.hi, Motion.standard(), label = "axisHi")

    var scrubSlot by remember { mutableStateOf<Int?>(null) }
    val scrubAlpha = remember { Animatable(0f) }
    val dotRadius = animateFloatAsState(
        targetValue = if (scrubSlot != null) 7f else 4f,
        animationSpec = Motion.snappy(),
        label = "scrubDot",
    )
    LaunchedEffect(scrubSlot) {
        scrubAlpha.animateTo(if (scrubSlot != null) 1f else 0f, Motion.quick())
    }

    Box(
        modifier.pointerInput(values.size, morphOnChange) {
            if (values.size < 2) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // 露头状态点一下就是展开抽屉，拖动读数只在展开后接管手势。
                if (progress.value < 0.5f) {
                    var pressed = true
                    while (pressed) {
                        pressed = awaitPointerEvent().changes.any { it.pressed }
                    }
                    return@awaitEachGesture
                }
                fun slotAt(x: Float): Int {
                    val detail = chartDetail(progress.value)
                    val left = 34.dp.toPx() * detail
                    val right = 16.dp.toPx()
                    val width = (size.width - left - right).coerceAtLeast(1f)
                    val fraction = ((x - left) / width).coerceIn(0f, 1f)
                    return (fraction * (ShapePoints - 1)).roundToInt()
                }
                var slot = slotAt(down.position.x)
                scrubSlot = slot
                onScrub(slot)
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                var dxSum = 0f
                var dySum = 0f
                var handedOff = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    dxSum += dx
                    dySum += dy
                    // 竖向占优就把手势让回抽屉，横向才吃掉，避免拖动读数时抽屉跟着走。
                    if (abs(dySum) > viewConfiguration.touchSlop && abs(dySum) > abs(dxSum)) {
                        handedOff = true
                        scrubSlot = null
                        onScrub(null)
                        break
                    }
                    if (abs(dxSum) > viewConfiguration.touchSlop) change.consume()
                    val next = slotAt(change.position.x)
                    if (next != slot) {
                        slot = next
                        scrubSlot = next
                        onScrub(next)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
                if (!handedOff) {
                    scope.launch {
                        delay(300)
                        scrubSlot = null
                        onScrub(null)
                    }
                }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shape = shownShape(fromShape.value, toShape.value, morph.value)
            if (shape.size < 2) return@Canvas

            val detail = chartDetail(progress.value)
            val left = 34.dp.toPx() * detail
            val right = 16.dp.toPx()
            val bottom = 17.dp.toPx() * detail
            val top = 2.dp.toPx()
            val plotWidth = (size.width - left - right).coerceAtLeast(1f)
            val plotHeight = (size.height - top - bottom).coerceAtLeast(1f)
            val lo = axisLo
            val span = (axisHi - lo).coerceAtLeast(1f)
            val stepX = plotWidth / (ShapePoints - 1f)
            val offsetX = shift.value * stepX

            fun yOf(value: Float): Float = top + plotHeight * (1f - (value - lo) / span)

            if (detail > 0f) {
                drawGrid(measurer, labelStyle, axis, lo, lo + span, palette.hairline, top, left, plotWidth, plotHeight, detail)
            }

            clipRect(left = left, top = top, right = left + plotWidth, bottom = top + plotHeight) {
                val line = Path()
                val fill = Path()
                var prevX = left + offsetX
                var prevY = yOf(shape[0])
                line.moveTo(prevX, prevY)
                fill.moveTo(prevX, top + plotHeight)
                fill.lineTo(prevX, prevY)
                for (i in 1 until ShapePoints) {
                    val x = left + i * stepX + offsetX
                    val y = yOf(shape[i])
                    val controlX = x - stepX / 3f
                    val controlY = prevY + (y - prevY) / 3f
                    line.cubicTo(controlX, controlY, controlX, controlY, x, y)
                    fill.cubicTo(controlX, controlY, controlX, controlY, x, y)
                    prevX = x
                    prevY = y
                }
                fill.lineTo(prevX, top + plotHeight)
                fill.close()

                val stops = gradientStops(lo, lo + span) { palette.levelColor(it) }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colorStops = stops.map { (position, color) ->
                            position to color.copy(alpha = 0.20f * position)
                        }.toTypedArray(),
                        startY = top,
                        endY = top + plotHeight,
                    ),
                )
                drawPath(
                    path = line,
                    brush = Brush.verticalGradient(
                        colorStops = stops.toTypedArray(),
                        startY = top,
                        endY = top + plotHeight,
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                val endX = left + (ShapePoints - 1) * stepX + offsetX
                val endY = yOf(shape[ShapePoints - 1])
                val tint = palette.levelColor(level)
                if (pulse.value < 1f) {
                    val k = pulse.value
                    drawCircle(
                        color = tint.copy(alpha = 0.24f * (1f - k)),
                        radius = 3.dp.toPx() + 14.dp.toPx() * k,
                        center = Offset(endX, endY),
                    )
                }
                drawCircle(tint.copy(alpha = 0.16f), 7.dp.toPx(), Offset(endX, endY))
                drawCircle(tint, 3.dp.toPx(), Offset(endX, endY))

                val slot = scrubSlot
                if (slot != null && scrubAlpha.value > 0f) {
                    val x = left + slot * stepX + offsetX
                    val y = yOf(shape[slot])
                    val alpha = scrubAlpha.value
                    drawLine(
                        color = palette.ink.copy(alpha = 0.30f * alpha),
                        start = Offset(x, top),
                        end = Offset(x, top + plotHeight),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val dotColor = palette.levelColor(AirLevel.of(shape[slot].toDouble()))
                    drawCircle(dotColor.copy(alpha = 0.22f * alpha), dotRadius.value.dp.toPx() + 3.dp.toPx(), Offset(x, y))
                    drawCircle(dotColor.copy(alpha = alpha), dotRadius.value.dp.toPx(), Offset(x, y))
                }
            }

            if (detail > 0f && timeLabels.size == 3) {
                val ty = top + plotHeight + 3.dp.toPx()
                drawTime(measurer, labelStyle, timeLabels[0], Offset(left, ty), detail)
                val mid = measurer.measure(AnnotatedString(timeLabels[1]), labelStyle)
                drawText(
                    textLayoutResult = mid,
                    topLeft = Offset(left + plotWidth / 2f - mid.size.width / 2f, ty),
                    alpha = detail,
                )
                val end = measurer.measure(AnnotatedString(timeLabels[2]), labelStyle)
                drawText(
                    textLayoutResult = end,
                    topLeft = Offset(left + plotWidth - end.size.width, ty),
                    alpha = detail,
                )
            }
        }

        if (values.size < 2) {
            Text(
                text = "这段时间还没有记录",
                fontSize = 15.sp,
                fontFamily = Manrope,
                color = palette.inkSoft,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

internal fun chartDetail(progress: Float): Float = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)

private fun DrawScope.drawGrid(
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    axis: ChartAxis,
    lo: Float,
    hi: Float,
    hairline: Color,
    top: Float,
    left: Float,
    plotWidth: Float,
    plotHeight: Float,
    detail: Float,
) {
    val span = (hi - lo).coerceAtLeast(1f)
    var value = ceil(lo / axis.step) * axis.step
    while (value < hi - 1f) {
        val y = top + plotHeight * (1f - (value - lo) / span)
        drawLine(
            color = hairline.copy(alpha = hairline.alpha * 2.2f * detail),
            start = Offset(left, y),
            end = Offset(left + plotWidth, y),
            strokeWidth = 1.dp.toPx(),
        )
        val text = measurer.measure(AnnotatedString(value.roundToInt().toString()), style)
        drawText(
            textLayoutResult = text,
            topLeft = Offset(left - 8.dp.toPx() - text.size.width, y - text.size.height / 2f),
            alpha = detail,
        )
        value += axis.step
    }
}

private fun DrawScope.drawTime(
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    text: String,
    topLeft: Offset,
    alpha: Float,
) {
    drawText(
        textLayoutResult = measurer.measure(AnnotatedString(text), style),
        topLeft = topLeft,
        alpha = alpha,
    )
}

/**
 * 采样点比槽位少时线性插值；比槽位多时每槽取窗口最大值，
 * 否则几百个采样里的单点峰值（比如某分钟突然 425）会被插值抹平，和统计数字对不上。
 */
private fun resample(values: List<Double>): FloatArray {
    if (values.isEmpty()) return FloatArray(0)
    if (values.size == 1) return FloatArray(ShapePoints) { values[0].toFloat() }
    if (values.size < ShapePoints) {
        return FloatArray(ShapePoints) { index ->
            val t = index * (values.size - 1) / (ShapePoints - 1).toFloat()
            val i0 = t.toInt().coerceIn(0, values.size - 1)
            val i1 = (i0 + 1).coerceAtMost(values.size - 1)
            val fraction = t - i0
            (values[i0] * (1.0 - fraction) + values[i1] * fraction).toFloat()
        }
    }
    return FloatArray(ShapePoints) { index ->
        val from = (index * values.size / ShapePoints).coerceAtMost(values.size - 1)
        val to = (((index + 1) * values.size + ShapePoints - 1) / ShapePoints).coerceIn(from + 1, values.size)
        var peak = values[from]
        for (i in from until to) if (values[i] > peak) peak = values[i]
        peak.toFloat()
    }
}

private fun blend(from: FloatArray, to: FloatArray, progress: Float): FloatArray = when {
    from.size != ShapePoints -> to
    to.size != ShapePoints -> from
    else -> FloatArray(ShapePoints) { from[it] + (to[it] - from[it]) * progress }
}

private fun shownShape(from: FloatArray, to: FloatArray, progress: Float): FloatArray = when {
    progress >= 0.999f -> to
    else -> blend(from, to, progress)
}

/**
 * 竖直渐变的色标：阈值在当前纵轴上的位置决定曲线穿过哪一档时变成哪个等级色。
 * 阈值上下各留 1% 过渡带，档内保持纯色；深色下 HAZARD 用 ink（#2B2B2B 在深色面板上不可见）。
 */
internal fun gradientStops(
    lo: Float,
    hi: Float,
    colorOf: (AirLevel) -> Color,
): List<Pair<Float, Color>> {
    val span = (hi - lo).coerceAtLeast(1f)
    val cuts = floatArrayOf(35f, 75f, 115f, 150f, 250f, 350f)
    val raw = ArrayList<Pair<Float, Color>>(14)
    raw += 0f to colorOf(AirLevel.of((hi + 1f).toDouble()))
    cuts.forEach { cut ->
        if (cut <= lo || cut >= hi) return@forEach
        val position = (hi - cut) / span
        raw += (position - 0.01f).coerceIn(0f, 1f) to colorOf(AirLevel.of((cut + 0.5f).toDouble()))
        raw += (position + 0.01f).coerceIn(0f, 1f) to colorOf(AirLevel.of((cut - 0.5f).toDouble()))
    }
    raw += 1f to colorOf(AirLevel.of(lo.toDouble()))

    val out = ArrayList<Pair<Float, Color>>(raw.size)
    var last = -1f
    for ((position, color) in raw.sortedBy { it.first }) {
        val p = if (position <= last) last + 0.0005f else position
        if (p > 1f) continue
        out += p to color
        last = p
    }
    return out
}
