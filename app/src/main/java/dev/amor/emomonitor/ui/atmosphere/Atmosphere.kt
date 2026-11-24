package dev.amor.emomonitor.ui.atmosphere

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Motion
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 全屏大气层：底色 → 三团缓慢漂移的色雾 → 噪点 → 微尘。
 * 逐帧时钟只写一个 Float state，且只在 Canvas 的绘制 lambda 里读，
 * 所以 60fps 只会重绘这一块，不会重组任何 Composable。
 */
@Composable
fun Atmosphere(
    level: AirLevel?,
    pm25: Double?,
    coldStart: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val trio = hazeTrio(level, palette.dark)
    val blobA = animateColorAsState(trio.a, Motion.slow(), label = "blobA")
    val blobB = animateColorAsState(trio.b, Motion.slow(), label = "blobB")
    val blobC = animateColorAsState(trio.c, Motion.slow(), label = "blobC")

    val haze = pm25?.let { (it / 250.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val wantParticles = if (pm25 == null) 10 else (14f + 126f * haze).roundToInt()

    val field = remember { ParticleField() }
    val ripples = remember { TouchRipples() }
    val noiseTile = remember { noiseTile() }
    val dot = remember { dotTexture() }
    // 噪点启动时铺成一张全屏位图，每帧只是一次位图绘制，不在 fragment 里跑 tile shader。
    var noiseFill by remember { mutableStateOf<ImageBitmap?>(null) }
    val clock = remember { mutableFloatStateOf(0f) }
    var hOverW by remember { mutableFloatStateOf(1.78f) }

    val updatedHaze by rememberUpdatedState(haze)
    val updatedWant by rememberUpdatedState(wantParticles)
    val updatedAspect by rememberUpdatedState(hOverW)

    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            var origin = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (origin == 0L) {
                        origin = now
                        last = now
                    }
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.032f)
                    last = now
                    val t = (now - origin) / 1_000_000_000f
                    field.update(dt, t, updatedHaze, updatedAspect, updatedWant)
                    ripples.update(dt)
                    clock.floatValue = t
                }
            }
        }
    }

    val fade = remember { Animatable(if (coldStart) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (coldStart) fade.animateTo(1f, tween(600))
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                hOverW = size.height.toFloat() / size.width
                if (size.width > 0 && size.height > 0 &&
                    (noiseFill?.width != size.width || noiseFill?.height != size.height)
                ) {
                    noiseFill = tiledNoise(noiseTile, size.width, size.height)
                }
            }
            .graphicsLayer { alpha = fade.value }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (!change.pressed) continue
                            field.push(
                                change.position.x,
                                change.position.y,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                110.dp.toPx(),
                            )
                            ripples.spawn(change.position.x, change.position.y)
                        }
                    }
                }
            },
    ) {
        drawRect(color = palette.paper)

        val t = clock.floatValue
        val w = size.width
        val h = size.height

        fun blob(
            color: Color,
            cx: Float,
            cy: Float,
            radius: Float,
            period: Float,
            phase: Float,
        ) {
            val angle = (t / period) * 6.2831855f + phase
            val driftX = 0.10f * w * sin(angle)
            val driftY = 0.06f * h * cos(2f * angle)
            val center = Offset(cx * w + driftX, cy * h + driftY)
            val r = radius * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }

        blob(blobA.value, 0.15f, 0.12f, 0.95f, 17f, 0f)
        blob(blobB.value, 0.95f, 0.42f, 0.85f, 23f, 1.7f)
        blob(blobC.value, 0.25f, 0.80f, 0.90f, 29f, 3.4f)

        noiseFill?.let { drawImage(it) }
        drawDust(field, dot, level?.color ?: palette.ink, palette.ink, t)
        ripples.draw(this, palette.ink)
    }
}

private const val NoiseSide = 128
private const val NoiseAlpha = 9

/** 128×128 随机灰度的灰度值，只生成一次。 */
private fun noiseTile(): IntArray {
    val random = kotlin.random.Random(17)
    return IntArray(NoiseSide * NoiseSide) { random.nextInt(256) }
}

/** 把噪点平铺成整屏位图，α 0.035 直接烘进像素里，绘制时不用再乘。 */
private fun tiledNoise(tile: IntArray, width: Int, height: Int): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = (y % NoiseSide) * NoiseSide
        for (x in 0 until width) {
            val value = tile[row + (x % NoiseSide)]
            pixels[y * width + x] = (NoiseAlpha shl 24) or (value shl 16) or (value shl 8) or value
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
