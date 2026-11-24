package dev.amor.emomonitor.ui.trend

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.numStyle
import dev.amor.emomonitor.ui.levelColor
import kotlin.math.roundToInt

/** 三列统计：最高 / 平均 / 最低。数字前一个对应等级色的圆点，切换范围时数字滚过去。 */
@Composable
fun TrendStats(values: List<Double>, modifier: Modifier = Modifier) {
    val max = values.maxOrNull() ?: return
    val min = values.minOrNull() ?: return
    val average = values.average()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        StatColumn("最高", max, Modifier.weight(1f), Alignment.Start)
        StatColumn("平均", average, Modifier.weight(1f), Alignment.CenterHorizontally)
        StatColumn("最低", min, Modifier.weight(1f), Alignment.End)
    }
}

@Composable
private fun StatColumn(label: String, target: Double, modifier: Modifier, align: Alignment.Horizontal) {
    val palette = Emo.colors
    val animated by animateFloatAsState(target.toFloat(), Motion.standard(), label = "stat$target")
    Column(modifier, horizontalAlignment = align) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
        )
        Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.levelColor(AirLevel.of(target))),
            )
            Text(
                text = animated.roundToInt().toString(),
                style = numStyle(28.sp, FontWeight.SemiBold),
                color = palette.ink,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * 等级占比：10dp 堆叠条 + 只列实际出现等级的图例。
 * 各段宽度从旧占比用 emphasized 重新分配，读数在 draw 阶段取。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelRatio(counts: IntArray, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    val total = counts.sum()
    if (total == 0) return

    val target = FloatArray(AirLevel.entries.size) { counts[it] / total.toFloat() }
    val from = remember { mutableStateOf(FloatArray(target.size)) }
    val to = remember { mutableStateOf(target) }
    val anim = remember { Animatable(1f) }
    val key = counts.toList()
    LaunchedEffect(key) {
        from.value = if (anim.value >= 0.999f) to.value else blendFractions(from.value, to.value, anim.value)
        to.value = target
        anim.snapTo(0f)
        anim.animateTo(1f, Motion.emphasized())
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "等级占比",
            fontSize = 13.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            val fractions = blendFractions(from.value, to.value, anim.value)
            val radius = CornerRadius(size.height / 2f)
            clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }) {
                var x = 0f
                AirLevel.entries.forEachIndexed { index, level ->
                    val width = size.width * fractions[index]
                    if (width <= 0f) return@forEachIndexed
                    drawRect(
                        color = palette.levelColor(level),
                        topLeft = Offset(x, 0f),
                        size = Size(width + 0.5f, size.height),
                    )
                    x += width
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AirLevel.entries.forEachIndexed { index, level ->
                val fraction = to.value[index]
                if (fraction <= 0.0001f) return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.levelColor(level)),
                    )
                    Text(
                        text = level.label,
                        fontSize = 13.sp,
                        fontFamily = Manrope,
                        color = palette.inkSoft,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                    Text(
                        text = percentText(fraction),
                        style = numStyle(13.sp),
                        color = palette.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

private fun blendFractions(from: FloatArray, to: FloatArray, progress: Float): FloatArray = when {
    from.size != to.size -> to
    progress >= 0.999f -> to
    else -> FloatArray(to.size) { from[it] + (to[it] - from[it]) * progress }
}

/** 有样本但占比不足 1% 时写 <1%，避免看起来像统计出错。 */
private fun percentText(fraction: Float): String {
    val percent = (fraction * 100).roundToInt()
    return if (percent == 0) "<1%" else "$percent%"
}

/** 每个等级在样本里的占比；顺序与 AirLevel.entries 一致。 */
fun levelCounts(values: List<Double>): IntArray {
    val counts = IntArray(AirLevel.entries.size)
    values.forEach { counts[AirLevel.of(it).ordinal]++ }
    return counts
}
