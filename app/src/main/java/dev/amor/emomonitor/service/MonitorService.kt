package dev.amor.emomonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.toArgb
import dev.amor.emomonitor.MainActivity
import dev.amor.emomonitor.R
import dev.amor.emomonitor.data.AirLevel
import dev.amor.emomonitor.data.EmoRepository
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.data.Phase
import dev.amor.emomonitor.data.Prefs
import dev.amor.emomonitor.data.adviceFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 前台服务：息屏与退到后台后继续采样，弥补原厂 App 黑屏即断的缺陷。 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EmoRepository.start(this, Prefs.effectiveMock(this))
        if (intent?.action == ACTION_RECONNECT) EmoRepository.reconnect()
        observeState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        EmoRepository.stop()
        super.onDestroy()
    }

    private fun observeState() {
        watcher?.cancel()
        watcher = scope.launch {
            EmoRepository.state.collectLatest { state ->
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    private fun notificationText(state: EmoUiState): String {
        val reading = state.reading ?: return when (state.phase) {
            Phase.SCANNING -> "正在搜索设备"
            Phase.CONNECTING -> "正在连接"
            Phase.ERROR -> state.message ?: "连接异常"
            else -> "等待连接"
        }
        return "PM2.5 ${"%.0f".format(reading.pm25)} µg/m³ · ${AirLevel.of(reading.pm25).label}"
    }

    private fun startForegroundCompat(state: EmoUiState?) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: EmoUiState?): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val reconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).setAction(ACTION_RECONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("空气堡 EMO")
            .setContentText(state?.let(::notificationText) ?: "等待连接")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "重新连接", reconnect)

        val reading = state?.reading
        if (reading != null) {
            val level = AirLevel.of(reading.pm25)
            builder
                .setColor(level.color.toArgb())
                .setSubText(level.label)
                .setProgress(PROGRESS_MAX, reading.pm25.roundToInt().coerceIn(0, PROGRESS_MAX), false)
                .setStyle(Notification.BigTextStyle().bigText(adviceFor(reading.pm25)))
        }
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "空气监测",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "emo_monitor"
        const val NOTIFICATION_ID = 1001
        const val ACTION_RECONNECT = "dev.amor.emomonitor.action.RECONNECT"
        const val PROGRESS_MAX = 350
    }
}
