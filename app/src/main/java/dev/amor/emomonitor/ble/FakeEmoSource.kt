package dev.amor.emomonitor.ble

import android.content.Context
import dev.amor.emomonitor.data.EmoReading
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.data.Phase
import dev.amor.emomonitor.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** 用正弦波模拟室内读数，供模拟器与无设备环境验证 UI。 */
class FakeEmoSource(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow(EmoUiState(usingMock = true))
    val state = _state.asStateFlow()

    fun start() {
        if (job != null) return
        job = scope.launch {
            _state.update { it.copy(phase = Phase.SCANNING, message = null) }
            delay(1_200)
            _state.update { it.copy(phase = Phase.CONNECTING) }
            delay(800)
            _state.update { it.copy(phase = Phase.STREAMING, battery = 82) }

            var step = 0
            while (isActive) {
                // 设置里的「模拟浓度」作为基准值，叠加小幅波动，方便逐档预览七种大气状态。
                val base = Prefs.mockPm(context).takeIf { it >= 0 }?.toDouble() ?: 46.0
                val wave = base +
                    5 * sin(step / 45.0) +
                    2.5 * sin(step / 13.0) +
                    (Random.nextDouble() - 0.5) * 1.2
                val pm = wave.coerceIn(2.0, 500.0)
                val reading = EmoReading(pm25 = pm, particles = (pm * 340).roundToInt())
                _state.update { state ->
                    state.copy(reading = reading, recent = (state.recent + reading).takeLast(180))
                }
                if (step % 30 == 0) {
                    _state.update { it.copy(battery = (82 - step / 120).coerceAtLeast(45)) }
                }
                step++
                delay(2_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(phase = Phase.IDLE) }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
