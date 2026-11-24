package dev.amor.emomonitor.ui.trend

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.ui.AnimatableFloat
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.tap
import kotlin.math.roundToInt

private val Labels = listOf("实时", "今天", "昨天", "7 天")

/**
 * 范围分段控件。露头状态下高度为 0（此时固定显示实时），随抽屉展开长到 40dp 并淡入；
 * 滑块位置在 layout 阶段读，切换时用 snappy 滑过去。
 */
@Composable
fun Segmented(
    selected: Int,
    onSelect: (Int) -> Unit,
    progress: AnimatableFloat,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val shape = RoundedCornerShape(14.dp)
    val slider = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { slider.animateTo(selected.toFloat(), Motion.snappy()) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val p = progress.value.coerceIn(0f, 1f)
                val height = (40.dp * p).roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            .clip(shape)
            .background(palette.field)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val itemWidth = constraints.maxWidth / Labels.size
                    val placeable = measurable.measure(
                        Constraints.fixed(itemWidth, constraints.maxHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((slider.value * itemWidth).roundToInt(), 0)
                    }
                }
                .shadow(1.dp, RoundedCornerShape(11.dp))
                .background(palette.sheet, RoundedCornerShape(11.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            Labels.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tap { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Manrope,
                        color = if (index == selected) palette.ink else palette.inkSoft,
                    )
                }
            }
        }
    }
}
