package dev.amor.emomonitor.ui.now

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.ui.Emo
import dev.amor.emomonitor.ui.Intro
import dev.amor.emomonitor.ui.Motion
import dev.amor.emomonitor.ui.atmosphere.Atmosphere
import dev.amor.emomonitor.ui.settings.SettingsSheet
import dev.amor.emomonitor.ui.trend.TrendSheet
import kotlinx.coroutines.delay

private val SheetPeek = 168.dp

/**
 * 一个画面 + 两层抽屉：上半屏是空气，下半屏是信息。
 * 冷启动时大气层先淡入、各行依次浮入，抽屉最后从屏幕底部弹上来。
 */
@Composable
fun NowScreen(
    state: EmoUiState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Emo.colors
    val coldStart = remember { Intro.consume() }
    val progress = remember { Animatable(if (coldStart) -1f else 0f) }
    LaunchedEffect(Unit) {
        if (!coldStart) return@LaunchedEffect
        delay(200)
        progress.animateTo(0f, Motion.sheet())
    }

    val reading = state.reading
    val level = reading?.let { AirLevel.of(it.pm25) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var settingsOpen by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.paper),
    ) {
        Atmosphere(
            level = level,
            pm25 = reading?.pm25,
            coldStart = coldStart,
            modifier = Modifier.fillMaxSize(),
        )

        TopBar(
            state = state,
            onReconnect = onReconnect,
            onSettings = { settingsOpen = true },
            progress = progress,
            playEnter = coldStart,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CompactHeader(
            state = state,
            progress = progress,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 18.dp, start = 24.dp),
        )

        InfoBlock(
            state = state,
            progress = progress,
            playEnter = coldStart,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = SheetPeek + navBottom + 20.dp),
        )

        TrendSheet(
            state = state,
            progress = progress,
            modifier = Modifier.fillMaxSize(),
        )

        if (settingsOpen) {
            SettingsSheet(onDismiss = { settingsOpen = false })
        }
    }
}
