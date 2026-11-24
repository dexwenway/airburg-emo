package dev.amor.emomonitor.ui.atmosphere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 微尘的状态全部放在普通数组里：这些值每帧变，绝不能进 Compose state，
 * 否则 60fps 重组整棵组合树。重绘由外部一个 tick 计数触发。
 */
class ParticleField(val count: Int = 140) {

    private val random = Random(System.nanoTime())
    val x = FloatArray(count) { random.nextFloat() }
    val y = FloatArray(count) { random.nextFloat() }

    // 深度刻意做成双峰：一半远景小微尘、一半中近景大颗粒，中间过渡少，
    // 这样一屏里大小对比明显，不会看起来像同一种圆点复制了 140 次。
    val z = FloatArray(count) {
        if (random.nextBoolean()) random.nextFloat() * 0.30f else 0.58f + random.nextFloat() * 0.42f
    }
    val alpha = FloatArray(count)

    private val phase = FloatArray(count) { random.nextFloat() * 6.2831855f }
    private val target = IntArray(count)
    private val pushX = FloatArray(count)
    private val pushY = FloatArray(count)
    private var targetCount = -1

    fun update(dt: Float, t: Float, haze: Float, aspect: Float, want: Int) {
        retarget(want)
        // 空气越脏，尘越沉、越慢。
        val speed = 0.020f + (0.008f - 0.020f) * haze
        val decay = exp(-3f * dt)
        for (i in 0 until count) {
            val goal = if (target[i] == 1) 1f else 0f
            val a = alpha[i]
            alpha[i] = if (a < goal) (a + 0.8f * dt).coerceAtMost(goal) else (a - 0.8f * dt).coerceAtLeast(goal)

            val depth = 0.4f + 0.6f * z[i]
            val theta = 1.3f * sin(2.7f * x[i] + 0.11f * t) + 1.3f * cos(3.1f * y[i] - 0.07f * t)
            val vx = cos(theta) * speed * depth + pushX[i]
            val vy = sin(theta) * speed * depth - 0.006f + pushY[i]

            x[i] = wrap(x[i] + vx * dt * aspect)
            y[i] = wrap(y[i] + vy * dt)
            pushX[i] *= decay
            pushY[i] *= decay
        }
    }

    /** 每颗粒子有自己的呼吸相位，整屏不会同步闪，悬浮物才不像像素点。 */
    fun breathe(index: Int, t: Float): Float = 0.78f + 0.22f * sin(t * 0.42f + phase[index])

    /** 只在目标数量变化的那一帧换粒子，避免每帧重新抽签导致闪烁。 */
    private fun retarget(want: Int) {
        if (want == targetCount) return
        targetCount = want
        val on = (0 until count).filter { target[it] == 1 }
        if (on.size < want) {
            (0 until count)
                .filter { target[it] == 0 }
                .sortedByDescending { alpha[it] }
                .take(want - on.size)
                .forEach { target[it] = 1 }
            return
        }
        on.sortedBy { alpha[it] }
            .take(on.size - want)
            .forEach { target[it] = 0 }
    }

    fun push(px: Float, py: Float, width: Float, height: Float, radius: Float) {
        for (i in 0 until count) {
            val dx = x[i] * width - px
            val dy = y[i] * height - py
            val d = hypot(dx, dy)
            if (d > radius || d < 0.001f) continue
            val force = (1f - d / radius) * 0.45f
            pushX[i] += dx / d * force
            pushY[i] += dy / d * force
        }
    }

    private fun wrap(v: Float): Float = when {
        v < 0f -> v + 1f
        v > 1f -> v - 1f
        else -> v
    }
}

class Ripple(val x: Float, val y: Float) {
    var age = 0f
}

class TouchRipples {
    private val ripples = ArrayList<Ripple>(6)
    private var lastSpawn = 0L

    fun spawn(x: Float, y: Float) {
        val now = System.nanoTime()
        if (now - lastSpawn < 150_000_000L) return
        lastSpawn = now
        if (ripples.size >= 5) ripples.removeAt(0)
        ripples += Ripple(x, y)
    }

    fun update(dt: Float) {
        for (ripple in ripples) ripple.age += dt
        ripples.removeAll { it.age > 0.6f }
    }

    fun draw(scope: DrawScope, ink: Color) = with(scope) {
        for (ripple in ripples) {
            val k = ripple.age / 0.6f
            if (k >= 1f) continue
            drawCircle(
                color = ink.copy(alpha = 0.18f * (1f - k)),
                radius = 90.dp.toPx() * k,
                center = Offset(ripple.x, ripple.y),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
    }
}

/**
 * 64×64 的软边光点：中心实、边缘化开，比实心圆更接近真实浮尘的景深感。
 * 所有粒子共用这张纹理，靠 tint 换色，避免每帧为 140 个粒子建渐变对象。
 */
fun dotTexture(size: Int = 64): ImageBitmap {
    val center = (size - 1) / 2f
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = (x - center) / center
            val dy = (y - center) / center
            val d2 = dx * dx + dy * dy
            if (d2 >= 1f) continue
            val falloff = 1f - d2
            val a = (falloff * 255f).toInt().coerceIn(0, 255)
            pixels[y * size + x] = (a shl 24) or 0xFFFFFF
        }
    }
    return android.graphics.Bitmap
        .createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/** 绘制半径与透明度都随深度 z 变化：近处的尘埃更大更实，远处的更小更淡。 */
fun DrawScope.drawDust(
    field: ParticleField,
    texture: ImageBitmap,
    levelColor: Color,
    ink: Color,
    t: Float,
) {
    val tint = colorLerp(levelColor, ink, 0.35f)
    // 整屏同色，色滤镜只建一次；140 个粒子共用它，避免每帧分配 140 个对象。
    val filter = ColorFilter.tint(tint)
    for (i in 0 until field.count) {
        val visible = field.alpha[i]
        if (visible <= 0.004f) continue
        val depth = field.z[i]
        val center = Offset(field.x[i] * size.width, field.y[i] * size.height)
        val base = lerp(1.2f, 4.2f, depth * depth).dp.toPx()
        val opacity = visible * lerp(0.10f, 0.38f, depth) * field.breathe(i, t)

        // 近景虚焦：把同一张柔边纹理放大 2.2 倍并压暗，景深就出来了，不必每帧新建渐变。
        val near = depth > 0.8f
        val radius = if (near) base * 2.2f else base
        val alpha = if (near) opacity * 0.35f else opacity

        val diameter = (radius * 2f).toInt().coerceAtLeast(1)
        drawImage(
            image = texture,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(texture.width, texture.height),
            dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
            dstSize = IntSize(diameter, diameter),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = filter,
        )
    }
}
