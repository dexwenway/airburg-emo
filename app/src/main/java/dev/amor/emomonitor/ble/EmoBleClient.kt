package dev.amor.emomonitor.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.amor.emomonitor.data.EmoUiState
import dev.amor.emomonitor.data.Phase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class EmoBleClient(private val context: Context) {

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readLock = Mutex()

    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var pollJob: Job? = null
    private var pendingRead: CancellableContinuation<ByteArray?>? = null
    private var running = false
    private var attempt = 0

    private val _state = MutableStateFlow(EmoUiState())
    val state = _state.asStateFlow()

    fun start() {
        if (running) return
        running = true
        attempt = 0
        beginScan()
    }

    fun stop() {
        running = false
        pollJob?.cancel()
        pollJob = null
        stopScan()
        closeGatt()
        _state.update { it.copy(phase = Phase.IDLE, message = null) }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun hasPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun beginScan() {
        if (!hasPermissions()) {
            fail("缺少蓝牙权限")
            return
        }
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            fail("请打开蓝牙")
            return
        }
        scanner = adapter.bluetoothLeScanner ?: run {
            fail("设备不支持 BLE 扫描")
            return
        }
        _state.update { it.copy(phase = Phase.SCANNING, message = null) }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name
                if (name?.contains(EmoProtocol.DEVICE_NAME) == true) {
                    stopScan()
                    connect(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                fail("扫描失败 ($errorCode)")
            }
        }
        scanCallback = callback
        scanner?.startScan(callback)

        scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_state.value.phase == Phase.SCANNING) {
                stopScan()
                fail("没有找到 EMO 设备")
            }
        }
    }

    private fun stopScan() {
        scanCallback?.let { callback ->
            runCatching { scanner?.stopScan(callback) }
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        _state.update { it.copy(phase = Phase.CONNECTING) }
        closeGatt()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                attempt = 0
                g.discoverServices()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingRead?.takeIf { it.isActive }?.resume(null)
                pendingRead = null
                closeGatt()
                if (running) reconnectLater() else _state.update { it.copy(phase = Phase.IDLE) }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("服务发现失败 ($status)")
                return
            }
            startPolling(g)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) value else null
            pendingRead?.takeIf { it.isActive }?.resume(result)
            pendingRead = null
        }

        // API 26–32 只回调旧签名。
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
            pendingRead?.takeIf { it.isActive }?.resume(result)
            pendingRead = null
        }
    }

    private fun startPolling(g: BluetoothGatt) {
        pollJob?.cancel()
        pollJob = scope.launch {
            _state.update { it.copy(phase = Phase.STREAMING, message = null) }
            var ticks = 0
            while (isActive) {
                readFrame(EmoProtocol.SERVICE, EmoProtocol.CHAR_DATA)?.let { frame ->
                    EmoProtocol.parse(frame)?.let { reading ->
                        _state.update { state ->
                            state.copy(
                                reading = reading,
                                recent = (state.recent + reading).takeLast(MAX_RECENT),
                            )
                        }
                    }
                }
                if (ticks % BATTERY_EVERY_TICKS == 0) {
                    readFrame(EmoProtocol.BATTERY_SERVICE, EmoProtocol.CHAR_BATTERY)?.firstOrNull()?.let { level ->
                        _state.update { it.copy(battery = level.toInt() and 0xFF) }
                    }
                }
                ticks++
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun readFrame(serviceUuid: java.util.UUID, charUuid: java.util.UUID): ByteArray? =
        readLock.withLock {
            val g = gatt ?: return null
            val service: BluetoothGattService = g.getService(serviceUuid) ?: return null
            val characteristic = service.getCharacteristic(charUuid) ?: return null
            suspendCancellableCoroutine { continuation ->
                pendingRead = continuation
                val started = runCatching { g.readCharacteristic(characteristic) }.getOrDefault(false)
                if (!started) {
                    pendingRead = null
                    continuation.resume(null)
                }
            }
        }

    private fun reconnectLater() {
        val delayMs = (1_000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
        attempt++
        _state.update { it.copy(phase = Phase.SCANNING, message = "连接断开，重试中") }
        scope.launch {
            delay(delayMs)
            if (running) beginScan()
        }
    }

    private fun fail(message: String) {
        stopScan()
        closeGatt()
        _state.update { it.copy(phase = Phase.ERROR, message = message) }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let { g ->
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val POLL_INTERVAL_MS = 2_000L
        private const val BATTERY_EVERY_TICKS = 15
        private const val RETRY_DELAY_MS = 5_000L
        private const val MAX_RECENT = 180
    }
}
