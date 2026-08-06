package com.powerflow.battery.util

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

object Prefs {
    private const val FILE = "powerflow_prefs"
    private const val KEY_MONITOR = "monitor_enabled"
    private const val KEY_CHIP = "status_bar_chip"
    private const val KEY_CAPSULE = "capsule_enabled"
    private const val KEY_LOCK_CAPSULE = "capsule_lock_screen"
    private const val KEY_REFRESH = "refresh_ms"
    private const val KEY_DUAL_CELL = "dual_cell"
    private const val KEY_DESIGN_CAPACITY = "design_capacity"
    private const val KEY_MAX_FCC = "max_fcc"
    private const val KEY_LAST_FCC = "last_fcc"
    private const val KEY_CALIB_POINTS = "calib_points"
    private const val KEY_FCC_SUM = "fcc_sum"
    private const val KEY_FCC_COUNT = "fcc_count"
    private const val KEY_FCC_LAST_VALUE = "fcc_last_value"

    private lateinit var appContext: Context
    private val sp: SharedPreferences
        get() = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    var monitorEnabled: Boolean
        get() = sp.getBoolean(KEY_MONITOR, false)
        set(value) { sp.edit().putBoolean(KEY_MONITOR, value).apply() }

    var statusBarChip: Boolean
        get() = sp.getBoolean(KEY_CHIP, true)
        set(value) { sp.edit().putBoolean(KEY_CHIP, value).apply() }

    var capsuleEnabled: Boolean
        get() = sp.getBoolean(KEY_CAPSULE, true)
        set(value) { sp.edit().putBoolean(KEY_CAPSULE, value).apply() }

    var capsuleLockScreen: Boolean
        get() = sp.getBoolean(KEY_LOCK_CAPSULE, true)
        set(value) { sp.edit().putBoolean(KEY_LOCK_CAPSULE, value).apply() }

    var refreshMs: Int
        get() = sp.getInt(KEY_REFRESH, 1000)
        set(value) { sp.edit().putInt(KEY_REFRESH, value).apply() }

    /** 手机是否为串联双电芯方案：电压约翻倍，容量计量方式与单电芯不同。 */
    var dualCell: Boolean
        get() = sp.getBoolean(KEY_DUAL_CELL, false)
        set(value) { sp.edit().putBoolean(KEY_DUAL_CELL, value).apply() }

    /** 用户手动设置的设计容量（mAh），0 表示未知。 */
    var designCapacity: Int
        get() = sp.getInt(KEY_DESIGN_CAPACITY, 0)
        set(value) { sp.edit().putInt(KEY_DESIGN_CAPACITY, value).apply() }

    /** 观测到的最大满电容量（mAh），只增不减，用于未知机型的设计容量参考。 */
    var maxFcc: Int
        get() = sp.getInt(KEY_MAX_FCC, 0)
        set(value) {
            if (value > sp.getInt(KEY_MAX_FCC, 0)) {
                sp.edit().putInt(KEY_MAX_FCC, value).apply()
            }
        }

    /** 平滑后的满电容量估算值（mAh），跨会话保留。 */
    var lastFcc: Double
        get() = sp.getFloat(KEY_LAST_FCC, 0f).toDouble()
        set(value) { sp.edit().putFloat(KEY_LAST_FCC, value.toFloat()).apply() }

    /**
     * 满电容量采样：每次测量值都累加，平均值 = 总和 ÷ 次数，采样越多越准确。
     * 相近的重复测量值只计一次，避免同一个电量平台期反复计数。
     */
    fun addFccSample(value: Double) {
        if (value <= 0) return
        val last = sp.getFloat(KEY_FCC_LAST_VALUE, -1f)
        if (last > 0 && abs(value - last) < 1.0) return
        val sum = sp.getString(KEY_FCC_SUM, "0.0")?.toDoubleOrNull() ?: 0.0
        val count = sp.getInt(KEY_FCC_COUNT, 0)
        sp.edit()
            .putString(KEY_FCC_SUM, (sum + value).toString())
            .putInt(KEY_FCC_COUNT, count + 1)
            .putFloat(KEY_FCC_LAST_VALUE, value.toFloat())
            .apply()
    }

    /** 满电容量平均值（mAh）；无新采样时返回旧版本遗留的平滑值。 */
    fun avgFcc(): Double {
        val count = sp.getInt(KEY_FCC_COUNT, 0)
        if (count > 0) {
            val sum = sp.getString(KEY_FCC_SUM, "0.0")?.toDoubleOrNull() ?: 0.0
            return sum / count
        }
        return sp.getFloat(KEY_LAST_FCC, 0f).toDouble()
    }

    fun fccSampleCount(): Int = sp.getInt(KEY_FCC_COUNT, 0)

    /** 电量-电量计校准点，格式 "level:counterUah,level:counterUah,..."。 */
    fun addCalibrationPoint(level: Int, counterUah: Long) {
        val list = getCalibrationPoints().toMutableList()
        list.removeAll { it.first == level }
        list.add(level to counterUah)
        if (list.size > 40) {
            // 只保留最近的 40 个校准点
            list.sortBy { it.first }
            while (list.size > 40) list.removeAt(0)
        }
        sp.edit().putString(KEY_CALIB_POINTS, list.joinToString(",") { "${it.first}:${it.second}" }).apply()
    }

    fun getCalibrationPoints(): List<Pair<Int, Long>> {
        return sp.getString(KEY_CALIB_POINTS, "")?.split(",")
            ?.mapNotNull { part ->
                val idx = part.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val lv = part.substring(0, idx).toIntOrNull() ?: return@mapNotNull null
                val counter = part.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
                lv to counter
            } ?: emptyList()
    }

    /**
     * 清空电池健康检测的全部采样与校准数据（保留手动设置的设计容量）。
     * 切换双电芯开关或用户主动重置时调用，避免新旧量纲不同的采样混在一起取平均。
     */
    fun resetHealthData() {
        sp.edit()
            .remove(KEY_MAX_FCC)
            .remove(KEY_LAST_FCC)
            .remove(KEY_CALIB_POINTS)
            .remove(KEY_FCC_SUM)
            .remove(KEY_FCC_COUNT)
            .remove(KEY_FCC_LAST_VALUE)
            .apply()
    }
}
