package dev.amor.emomonitor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 里程表式读数：按字符位拆开、从右往左编号，只有变化的位滚动。
 * 小数点与小数位固定在右侧槽位，位数变化时靠槽位宽度补间而不是整串淡入淡出。
 */
@Composable
fun RollingNumber(
    value: Double?,
    integerSize: TextUnit,
    weight: Int,
    modifier: Modifier = Modifier,
    unit: String? = "µg/m³",
    unitColor: Color,
    integerColor: Color,
    softColor: Color,
) {
    val cells = remember(value) { cellsOf(value) }
    val direction = remember { mutableFloatStateOf(1f) }
    var previous by remember { mutableStateOf<Double?>(null) }
    var entering by remember { mutableStateOf(false) }
    if (value != previous) {
        direction.floatValue = if (value != null && previous != null && value < previous!!) -1f else 1f
        entering = value != null && previous == null
        previous = value
    }

    val integerStyle = remember(integerSize, weight) {
        TextStyle(
            fontFamily = Manrope,
            fontSize = integerSize,
            fontWeight = FontWeight(weight),
            letterSpacing = (-0.04).em,
            fontFeatureSettings = "tnum",
        )
    }
    val decimalStyle = remember(integerStyle) {
        integerStyle.copy(fontSize = integerSize * 0.34f)
    }

    val measurer = rememberTextMeasurer()
    val integerAdvance = remember(measurer, integerStyle) {
        measurer.measure(AnnotatedString("00"), integerStyle).size.width / 2f
    }
    val decimalAdvance = remember(measurer, decimalStyle) {
        measurer.measure(AnnotatedString("00"), decimalStyle).size.width / 2f
    }
    // 「--」揉在一个槽里量宽，避免两个横杠之间留出一整个数字的空隙。
    val dashAdvance = remember(measurer, integerStyle) {
        measurer.measure(AnnotatedString("--"), integerStyle).size.width.toFloat()
    }

    // 统一槽高与基线：所有字符位（含单位）都摆到同一条基线上，空槽不会把基线带偏。
    // 文字排版盒的上 ascent 空白远大于字面高度，槽高上下各裁掉一部分，
    // 读数才能紧贴 PM2.5 标注、下方等级行也能上移，把上半屏让给微尘。
    val sample = remember(measurer, integerStyle) { measurer.measure(AnnotatedString("0"), integerStyle) }
    val density = LocalDensity.current
    val trimTop = with(density) { (integerStyle.fontSize.toPx() * 0.17f).roundToInt() }
    val trimBottom = with(density) { (integerStyle.fontSize.toPx() * 0.20f).roundToInt() }
    val slotHeight = (sample.size.height - trimTop - trimBottom).coerceAtLeast(1)
    val slotBaseline = sample.firstBaseline.roundToInt() - trimTop

    val unitStyle = remember(unitColor) {
        TextStyle(
            fontFamily = Manrope,
            fontSize = 15.sp,
            color = unitColor,
            fontFeatureSettings = "tnum",
        )
    }
    val unitBaseline = remember(measurer, unit, unitStyle) {
        if (unit == null) 0 else measurer.measure(AnnotatedString(unit), unitStyle).firstBaseline.roundToInt()
    }

    Layout(
        content = {
            // 顺序固定：百位、十位、个位、小数点、小数位；空槽宽度为 0，不占位也不销毁。
            RollCell(
                cell = cells[0],
                style = integerStyle,
                advance = integerAdvance,
                color = integerColor,
                softColor = softColor,
                direction = direction.floatValue,
                stagger = if (entering) 200L else 0L,
                slotHeight = slotHeight,
                slotBaseline = slotBaseline,
                trimTop = trimTop,
                trimBottom = trimBottom,
            )
            RollCell(
                cell = cells[1],
                style = integerStyle,
                advance = integerAdvance,
                color = integerColor,
                softColor = softColor,
                direction = direction.floatValue,
                stagger = if (entering) 150L else 0L,
                slotHeight = slotHeight,
                slotBaseline = slotBaseline,
                trimTop = trimTop,
                trimBottom = trimBottom,
            )
            RollCell(
                cell = cells[2],
                style = integerStyle,
                advance = if (cells[2] == "--") dashAdvance else integerAdvance,
                color = integerColor,
                softColor = softColor,
                direction = direction.floatValue,
                stagger = if (entering) 100L else 0L,
                slotHeight = slotHeight,
                slotBaseline = slotBaseline,
                trimTop = trimTop,
                trimBottom = trimBottom,
            )
            RollCell(
                cell = cells[3],
                style = decimalStyle,
                advance = decimalAdvance * 0.55f,
                color = softColor,
                softColor = softColor,
                direction = direction.floatValue,
                stagger = if (entering) 50L else 0L,
                slotHeight = slotHeight,
                slotBaseline = slotBaseline,
                trimTop = trimTop,
                trimBottom = trimBottom,
            )
            RollCell(
                cell = cells[4],
                style = decimalStyle,
                advance = decimalAdvance,
                color = softColor,
                softColor = softColor,
                direction = direction.floatValue,
                stagger = 0L,
                slotHeight = slotHeight,
                slotBaseline = slotBaseline,
                trimTop = trimTop,
                trimBottom = trimBottom,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = unitStyle,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        modifier = modifier,
        measurePolicy = remember(slotHeight, slotBaseline, unitBaseline) {
            MeasurePolicy { measurables, constraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                val width = placeables.sumOf { it.width }
                layout(width, slotHeight) {
                    var x = 0
                    placeables.forEachIndexed { index, placeable ->
                        placeable.place(x, if (index < 5) 0 else slotBaseline - unitBaseline)
                        x += placeable.width
                    }
                }
            }
        },
    )
}

/**
 * 一个字符位。宽度用 Animatable 在 layout 阶段读（位数增减时从 0 长出来），
 * 上下滚动的位移在 graphicsLayer 里读，两者都不触发重组。
 */
@Composable
private fun RollCell(
    cell: String,
    style: TextStyle,
    advance: Float,
    color: Color,
    softColor: Color,
    direction: Float,
    stagger: Long,
    slotHeight: Int,
    slotBaseline: Int,
    trimTop: Int,
    trimBottom: Int,
    modifier: Modifier = Modifier,
) {
    var previous by remember { mutableStateOf(cell) }
    var current by remember { mutableStateOf(cell) }
    val anim = remember { Animatable(1f) }
    var dir by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(cell) {
        if (cell == current) return@LaunchedEffect
        previous = current
        current = cell
        dir = direction
        anim.snapTo(0f)
        if (stagger > 0L) delay(stagger)
        anim.animateTo(1f, tween(420, easing = Motion.Emphasized))
    }

    val width = remember { Animatable(if (cell.isNotEmpty()) advance else 0f) }
    LaunchedEffect(cell.isNotEmpty(), advance) {
        width.animateTo(if (cell.isNotEmpty()) advance else 0f, Motion.emphasized())
    }

    val lineHeightPx = with(LocalDensity.current) {
        val lineHeight = style.lineHeight
        if (lineHeight.isUnspecified) style.fontSize.toPx() * 1.8f else lineHeight.toPx()
    }
    val textColor = if (cell == "-" || cell.isEmpty()) softColor else color

    val policy = remember(slotHeight, slotBaseline, width) {
        MeasurePolicy { measurables, constraints ->
            val outgoing = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val incoming = measurables[1].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val slotWidth = width.value.roundToInt().coerceAtLeast(0)
            val outgoingBaseline = outgoing[FirstBaseline].coerceAtLeast(0)
            val incomingBaseline = incoming[FirstBaseline].coerceAtLeast(0)
            layout(slotWidth, slotHeight, mapOf(FirstBaseline to slotBaseline)) {
                outgoing.place(0, slotBaseline - outgoingBaseline)
                incoming.place(0, slotBaseline - incomingBaseline)
            }
        }
    }

    Layout(
        content = {
            Text(
                text = previous,
                style = style.copy(color = textColor),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - anim.value
                    translationY = -dir * anim.value * lineHeightPx
                },
            )
            Text(
                text = current,
                style = style.copy(color = textColor),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = anim.value
                    translationY = dir * (1f - anim.value) * lineHeightPx
                },
            )
        },
        // 用 drawWithContent + clipRect 而不是 clipToBounds：后者是 LayoutModifier，
        // 会吞掉子项 baseline，单位就会和读数对不上。
        modifier = modifier.drawWithContent {
            val scope = this
            // 盒子上下各裁掉一段 ascent/descent 空白；裁剪区仍覆盖整个字面高度。
            clipRect(top = -trimTop.toFloat(), bottom = size.height + trimBottom.toFloat()) {
                scope.drawContent()
            }
        },
        measurePolicy = policy,
    )
}

/** 固定返回 5 个槽：百位、十位、个位、小数点、小数位；缺位为空串。 */
private fun cellsOf(value: Double?): List<String> {
    if (value == null) return listOf("", "", "--", "", "")
    val text = if (value >= 100) value.roundToInt().toString() else String.format(Locale.US, "%.1f", value)
    val intPart = text.substringBefore('.')
    val hasDot = text.contains('.')
    val decPart = text.substringAfter('.', "")
    return listOf(
        intPart.getOrNull(intPart.length - 3)?.toString() ?: "",
        intPart.getOrNull(intPart.length - 2)?.toString() ?: "",
        intPart.getOrNull(intPart.length - 1)?.toString() ?: "",
        if (hasDot) "." else "",
        decPart.take(1),
    )
}
