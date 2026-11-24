package dev.amor.emomonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.amor.emomonitor.data.EmoRepository
import dev.amor.emomonitor.data.Prefs
import dev.amor.emomonitor.service.MonitorService
import dev.amor.emomonitor.ui.EmoTheme
import dev.amor.emomonitor.ui.now.NowScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            EmoRepository.reconnect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyMockPm(intent)
        setContent {
            EmoTheme {
                val state by EmoRepository.state.collectAsStateWithLifecycle()
                NowScreen(state = state, onReconnect = EmoRepository::reconnect)
            }
        }
        requestPermissions()
        startMonitor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMockPm(intent)
    }

    /** 调试入口：`adb shell am start -n dev.amor.emomonitor/.MainActivity --ei mock_pm 180`。 */
    private fun applyMockPm(intent: Intent?) {
        val pm = intent?.getIntExtra("mock_pm", -1) ?: -1
        if (pm >= 0) Prefs.setMockPm(this, pm)
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun requestPermissions() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startMonitor() {
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
    }
}
