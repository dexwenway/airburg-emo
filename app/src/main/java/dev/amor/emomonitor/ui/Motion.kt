package dev.amor.emomonitor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

typealias AnimatableFloat = Animatable<Float, AnimationVector1D>

/** 全工程动画参数唯一来源；任何动画都从这里取，避免每个组件各调一组数字。 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> quick() = tween<T>(180)
    fun <T> standard() = tween<T>(360, easing = FastOutSlowInEasing)
    fun <T> emphasized() = tween<T>(560, easing = Emphasized)
    fun <T> slow() = tween<T>(1200, easing = FastOutSlowInEasing)
    fun <T> snappy() = spring<T>(dampingRatio = 0.72f, stiffness = 520f)
    fun <T> sheet() = spring<T>(dampingRatio = 0.86f, stiffness = 380f)
    fun <T> soft() = spring<T>(dampingRatio = 0.80f, stiffness = 180f)
}

/** 全工程禁止水波纹：按下缩到 0.96，松手用 snappy 回弹。 */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            scale.animateTo(
                if (interaction is PressInteraction.Press) 0.96f else 1f,
                Motion.snappy(),
            )
        }
    }
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** 冷启动只播一次；配置变更（旋转、深色切换）不应重播入场。 */
internal object Intro {
    private var played = false

    fun consume(): Boolean {
        val first = !played
        played = true
        return first
    }
}

/** 入场：上移 16dp → 0 并淡入，每级延迟 70ms。 */
fun Modifier.enter(index: Int, play: Boolean = true): Modifier = composed {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (!play) {
            anim.snapTo(1f)
            return@LaunchedEffect
        }
        delay(index * 70L)
        anim.animateTo(1f, Motion.emphasized())
    }
    graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 16.dp.toPx()
    }
}
