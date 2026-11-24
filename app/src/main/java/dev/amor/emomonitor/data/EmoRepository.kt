package dev.amor.emomonitor.data

import android.content.Context
import dev.amor.emomonitor.ble.EmoBleClient
import dev.amor.emomonitor.ble.FakeEmoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

object EmoRepository {

    private const val PERSIST_INTERVAL_MS = 55_000L
    private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(EmoUiState())
    val state = _state.asStateFlow()

    private var ble: EmoBleClient? = null
    private var fake: FakeEmoSource? = null
    private var db: SampleDb? = null
    private var collectJob: Job? = null
    private var lastPersist = 0L

    @Synchronized
    fun start(context: Context, useMock: Boolean) {
        if (collectJob != null) return
        val app = context.applicationContext
        db = SampleDb(app).also { it.pruneBefore(System.currentTimeMillis() - RETENTION_MS) }

        val source = if (useMock) {
            FakeEmoSource(app).also { fake = it; it.start() }.state
        } else {
            EmoBleClient(app).also { ble = it; it.start() }.state
        }

        collectJob = scope.launch {
            source.collect { publish(it) }
        }
    }

    @Synchronized
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        ble?.shutdown()
        ble = null
        fake?.shutdown()
        fake = null
        lastPersist = 0
        _state.value = EmoUiState()
    }

    fun reconnect() {
        ble?.let { client ->
            client.stop()
            client.start()
        }
        fake?.let { source ->
            source.stop()
            source.start()
        }
    }

    fun history(days: Int, hourly: Boolean): List<EmoReading> {
        val now = System.currentTimeMillis()
        return historyBetween(now - days * 24L * 60 * 60 * 1000, now, hourly)
    }

    fun historyBetween(from: Long, to: Long, hourly: Boolean): List<EmoReading> {
        val samples = db?.samplesSince(from).orEmpty().filter { it.timestamp <= to }
        if (!hourly) return samples
        return samples
            .groupBy { it.timestamp / (60 * 60 * 1000) }
            .map { (_, group) ->
                EmoReading(
                    pm25 = group.map { it.pm25 }.average(),
                    particles = group.map { it.particles }.average().toInt(),
                    timestamp = group.last().timestamp,
                )
            }
    }

    fun clearHistory() {
        db?.clear()
    }

    private fun publish(state: EmoUiState) {
        _state.value = state
        val reading = state.reading ?: return
        if (reading.timestamp - lastPersist < PERSIST_INTERVAL_MS) return
        lastPersist = reading.timestamp
        db?.insert(reading)
    }
}
