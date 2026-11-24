package dev.amor.emomonitor.data

import android.content.Context
import android.content.pm.PackageManager

object Prefs {
    private const val FILE = "emo_prefs"
    private const val KEY_MOCK = "mock_source"
    private const val KEY_BACKGROUND = "background_record"
    private const val KEY_MOCK_PM = "mock_pm"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 模拟器与无 BLE 的机器上自动回退到模拟数据。 */
    fun effectiveMock(context: Context): Boolean =
        useMock(context) || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun useMock(context: Context): Boolean = prefs(context).getBoolean(KEY_MOCK, false)

    fun setMock(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MOCK, enabled).apply()
    }

    fun backgroundRecord(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND, true)

    fun setBackgroundRecord(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND, enabled).apply()
    }

    /** 模拟浓度基准值；-1 表示没设置过，由播放曲线自己给默认值。 */
    fun mockPm(context: Context): Int = prefs(context).getInt(KEY_MOCK_PM, -1)

    fun setMockPm(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_MOCK_PM, value.coerceIn(0, 500)).apply()
    }
}
