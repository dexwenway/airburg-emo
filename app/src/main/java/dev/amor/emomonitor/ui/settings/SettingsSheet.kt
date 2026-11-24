package dev.amor.emomonitor.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.data.EmoRepository
import dev.amor.emomonitor.data.Prefs
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Manrope
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.numStyle
import dev.amor.emomonitor.ui.levelColor
import dev.amor.emomonitor.ui.tap
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FLING_DP_PER_SECOND = 800f

/**
 * 全高设置面板：自底部升起，后面压一层遮罩；下拉 / 点遮罩 / 返回键都关闭，
 * 松手时的速度直接交给 spring，和趋势抽屉同一套接续物理。
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val palette = Emo.colors
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current

    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, Motion.sheet())
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.sheet()) }
    BackHandler { dismiss() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight - statusTop - 24.dp
        val heightPx = with(density) { sheetHeight.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(palette.ink.copy(alpha = 0.28f * progress.value.coerceIn(0f, 1f)))
                }
                .pointerInput(Unit) {
                    detectTapGestures { dismiss() }
                },
        )

        val dragState = rememberDraggableState { delta ->
            scope.launch {
                progress.snapTo(rubberDown(progress.value, progress.value - delta / heightPx))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, ((1f - progress.value) * heightPx).roundToInt()) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val v = -velocity / heightPx
                        val target = when {
                            abs(v) > FLING_DP_PER_SECOND / sheetHeight.value -> if (v > 0f) 1f else 0f
                            progress.value > 0.85f -> 1f
                            else -> 0f
                        }
                        progress.animateTo(target, Motion.sheet(), initialVelocity = v)
                        if (target == 0f) onDismiss()
                    },
                )
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    clip = true
                }
                .background(palette.sheet)
                .padding(horizontal = 20.dp)
                .padding(bottom = navBottom),
        ) {
            SettingsContent(onClose = ::dismiss)
        }
    }
}

@Composable
private fun SettingsContent(onClose: () -> Unit) {
    val palette = Emo.colors
    val context = LocalContext.current

    var background by remember { mutableStateOf(Prefs.backgroundRecord(context)) }
    var mock by remember { mutableStateOf(Prefs.effectiveMock(context)) }
    var mockPm by remember { mutableStateOf(Prefs.mockPm(context).takeIf { it >= 0 } ?: 46) }
    var confirming by remember { mutableStateOf(false) }

    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "设置",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Manrope,
                color = palette.ink,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.field)
                    .tap(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val stroke = 1.8.dp.toPx()
                    drawLine(
                        color = palette.ink,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    drawLine(
                        color = palette.ink,
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }

        GroupLabel("记录", Modifier.padding(top = 8.dp))
        Group {
            SwitchRow(
                title = "后台连续记录",
                detail = "息屏后继续采样，每分钟保存一条",
                checked = background,
            ) { enabled ->
                background = enabled
                Prefs.setBackgroundRecord(context, enabled)
            }
        }

        GroupLabel("数据源", Modifier.padding(top = 8.dp))
        Group {
            SwitchRow(
                title = "模拟数据",
                detail = "不连接设备，用模拟曲线验证界面",
                checked = mock,
            ) { enabled ->
                mock = enabled
                Prefs.setMock(context, enabled)
                EmoRepository.stop()
                EmoRepository.start(context, Prefs.effectiveMock(context))
            }
            if (mock) {
                Divider()
                SliderRow(
                    value = mockPm,
                    onChange = { next ->
                        mockPm = next
                        Prefs.setMockPm(context, next)
                    },
                )
            }
        }

        GroupLabel("数据", Modifier.padding(top = 8.dp))
        Group {
            ClearRow(
                confirming = confirming,
                onConfirmStateChange = { confirming = it },
                onClear = {
                    EmoRepository.clearHistory()
                    confirming = false
                },
            )
        }

        GroupLabel("关于", Modifier.padding(top = 8.dp))
        Group {
            InfoRow("版本号", version)
            Divider()
            InfoRow("设备名", "PM2.5TESTER")
        }
    }
}

@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    Text(
        text = text,
        fontSize = 13.sp,
        fontFamily = Manrope,
        color = palette.inkSoft,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val palette = Emo.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.field),
        content = content,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Manrope,
                color = Emo.colors.ink,
            )
            Text(
                text = detail,
                fontSize = 13.sp,
                fontFamily = Manrope,
                color = Emo.colors.inkSoft,
                modifier = Modifier.padding(top = 2.dp, end = 10.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        EmoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClearRow(
    confirming: Boolean,
    onConfirmStateChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val palette = Emo.colors
    AnimatedContent(
        targetState = confirming,
        transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
        label = "clearRow",
    ) { asking ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!asking) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "清除历史记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Manrope,
                        color = palette.ink,
                    )
                    Text(
                        text = "删除本机保存的所有采样",
                        fontSize = 13.sp,
                        fontFamily = Manrope,
                        color = palette.inkSoft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "清除",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Manrope,
                    color = palette.danger,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .tap { onConfirmStateChange(true) }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                )
                return@Row
            }
            Text(
                text = "确定清除全部记录？",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Manrope,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "取消",
                fontSize = 16.sp,
                fontFamily = Manrope,
                color = palette.inkSoft,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .tap { onConfirmStateChange(false) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
            Text(
                text = "清除",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Manrope,
                color = palette.danger,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .tap(onClear)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = Emo.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Manrope,
            color = palette.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = Manrope,
            color = palette.inkSoft,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Emo.colors.hairline),
    )
}

@Composable
private fun SliderRow(value: Int, onChange: (Int) -> Unit) {
    val palette = Emo.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "模拟浓度",
                fontSize = 15.sp,
                fontFamily = Manrope,
                color = palette.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$value µg/m³",
                style = numStyle(15.sp),
                color = palette.inkSoft,
            )
        }
        EmoSlider(
            value = value,
            onChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        )
    }
}

/** 自绘开关：52×32 轨道，26dp 白色滑块；按住时滑块横向拉长到 30dp，位移 snappy。 */
@Composable
fun EmoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    val thumb = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { thumb.animateTo(if (checked) 1f else 0f, Motion.snappy()) }
    val track = animateColorAsState(
        targetValue = if (checked) palette.ink else palette.ink.copy(alpha = 0.16f),
        animationSpec = Motion.quick(),
        label = "switchTrack",
    )
    var pressed by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { pressed = it is PressInteraction.Press }
    }

    Canvas(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clickable(interactionSource = source, indication = null) { onCheckedChange(!checked) },
    ) {
        val radius = size.height / 2f
        drawRoundRect(color = track.value, cornerRadius = CornerRadius(radius))
        val thumbWidth = (if (pressed) 30.dp else 26.dp).toPx()
        val x = lerp(3.dp.toPx(), size.width - 3.dp.toPx() - thumbWidth, thumb.value)
        val centerY = size.height / 2f
        drawCircle(
            color = Color.Black.copy(alpha = 0.10f),
            radius = thumbWidth / 2f,
            center = Offset(x + thumbWidth / 2f, centerY + 0.5.dp.toPx()),
        )
        drawCircle(
            color = palette.sheet,
            radius = thumbWidth / 2f,
            center = Offset(x + thumbWidth / 2f, centerY),
        )
    }
}

/** 自绘滑杆：4dp 轨道、已选部分为当前模拟等级色、22dp 白色滑块，按下放大到 26dp。 */
@Composable
fun EmoSlider(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = Emo.colors
    var pressed by remember { mutableStateOf(false) }
    val fraction = (value / 500f).coerceIn(0f, 1f)
    val thumbSize by animateFloatAsState(
        targetValue = if (pressed) 26f else 22f,
        animationSpec = Motion.snappy(),
        label = "sliderThumb",
    )
    val active = animateColorAsState(
        targetValue = palette.levelColor(AirLevel.of(value.toDouble())),
        animationSpec = Motion.standard(),
        label = "sliderActive",
    )
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                fun update(x: Float) {
                    val next = ((x / size.width) * 500f).roundToInt().coerceIn(0, 500)
                    if (next != value) onChange(next)
                }
                update(down.position.x)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.positionChanged()) {
                        change.consume()
                        update(change.position.x)
                    }
                }
                pressed = false
            }
        },
    ) {
        val trackHeight = 4.dp.toPx()
        val radius = trackHeight / 2f
        val centerY = size.height / 2f
        drawRoundRect(
            color = palette.ink.copy(alpha = 0.12f),
            topLeft = Offset(0f, centerY - radius),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius),
        )
        val activeWidth = size.width * fraction
        if (activeWidth > 0f) {
            drawRoundRect(
                color = active.value,
                topLeft = Offset(0f, centerY - radius),
                size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(radius),
            )
        }
        val r = thumbSize.dp.toPx() / 2f
        val cx = (activeWidth).coerceIn(r, size.width - r)
        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            radius = r,
            center = Offset(cx, centerY + 0.5.dp.toPx()),
        )
        drawCircle(color = palette.sheet, radius = r, center = Offset(cx, centerY))
    }
}

/** 只有向下越界才橡皮筋（往上已经到顶），位移乘 0.2。 */
private fun rubberDown(current: Float, next: Float): Float = when {
    current < 0f -> current + (next - current) * 0.2f
    next < 0f -> next * 0.2f
    next > 1f -> 1f + (next - 1f) * 0.2f
    else -> next
}
