package com.powerflow.battery.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.powerflow.battery.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** 功率数据来源。OPPO/部分机型会屏蔽某些传感器，需要多级兜底。 */
enum class PowerSource { SENSOR, ESTIMATE_CHARGE, ESTIMATE_ENERGY, NONE }

/**
 * 一次电池采样结果。功率 = 电压 × 电流。
 * 读取顺序：电流传感器 → 电流平均值 → 电量计差分估算 → 能量计差分估算。
 */
data class BatterySnapshot(
    val level: Int,
    val voltageMv: Int,
    /** 电流，单位：安培（A） */
    val currentA: Double,
    val powerW: Double,
    val charging: Boolean,
    val full: Boolean,
    val plugged: Int,
    val tempC: Double,
    val estimated: Boolean,
    val available: Boolean,
    val source: PowerSource,
    val timestamp: Long,
    val voltageEstimated: Boolean = false
) {
    companion object {
        val NONE = BatterySnapshot(0, 0, 0.0, 0.0, false, false, 0, 0.0, false, false, PowerSource.NONE, 0L)
    }
}

object PowerReader {
    private data class Sample(val timeMs: Long, val value: Long, val voltageMv: Int, val level: Int)

    private val chargeSamples = ArrayDeque<Sample>()
    private val energySamples = ArrayDeque<Sample>()
    private const val MAX_SAMPLES = 600
    @Volatile
    private var lastLevel = 0
    @Volatile
    private var lastCounterUah = 0L

    fun read(context: Context): BatterySnapshot {
        val appContext = context.applicationContext
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        // OPPO 等系统可能对第三方应用的部分电池属性读取抛异常，全部包保护
        val intent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = (intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0).coerceIn(0, 100)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        var voltageMv = rawVoltage
        val voltageEstimated: Boolean
        val dualCell = Prefs.dualCell
        // OPPO 部分机型(如 OPD2506 / ColorOS 16.1)的电压广播长期不更新，
        // 实测广播值恒为 4（单位不明），真实电池电压充电时约 4.4V，需要兜底；
        // 串联双电芯机型电压约翻倍（满电约 8.4~9V），校验范围与兜底值都要 ×2：
        val validVoltageRange = if (dualCell) 1000..12000 else 1000..6000
        if (voltageMv !in validVoltageRange) {
            // 充电时电池电压接近满电电压；放电时按电量用典型锂电曲线估算
            voltageMv = if (charging) {
                if (dualCell) 8800 else 4400
            } else {
                if (dualCell) 6600 + level * 18 else 3300 + level * 9
            }
            voltageEstimated = true
        } else {
            voltageEstimated = false
        }
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val now = System.currentTimeMillis()
        val chargeCounter = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toLong()
        }.getOrDefault(0L)
        lastLevel = level
        lastCounterUah = chargeCounter
        val energyCounter = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        }.getOrDefault(0L)
        if (chargeCounter > 0) {
            pushSample(chargeSamples, Sample(now, chargeCounter, voltageMv, level))
            persistCalibration(level, chargeCounter)
        }
        if (energyCounter > 0 && energyCounter != Long.MAX_VALUE) {
            pushSample(energySamples, Sample(now, energyCounter, voltageMv, level))
        }

        val currentUa = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0)
        val averageUa = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }.getOrDefault(0)
        var powerW = 0.0
        var source = PowerSource.NONE
        var estimated = false

        // 电流传感器在部分机型上是噪声（实测 ±1.5mA 内随机跳），
        // 只有当“电流 × 电压”达到 0.05W 以上才采信，否则一律走估算
        if (voltageMv > 0 && abs(currentUa.toLong()) in 1000..100_000_000) {
            val p = abs(currentUa).toDouble() * voltageMv / 1_000_000_000.0
            if (p >= 0.05) {
                powerW = p
                source = PowerSource.SENSOR
            }
        }
        if (source == PowerSource.NONE && voltageMv > 0 && abs(averageUa.toLong()) in 1000..100_000_000) {
            val p = abs(averageUa).toDouble() * voltageMv / 1_000_000_000.0
            if (p >= 0.05) {
                powerW = p
                source = PowerSource.SENSOR
            }
        }
        if (source == PowerSource.NONE) {
            estimateFromCharge(voltageMv)?.let {
                powerW = it
                source = PowerSource.ESTIMATE_CHARGE
                estimated = true
            } ?: estimateFromEnergy()?.let {
                powerW = it
                source = PowerSource.ESTIMATE_ENERGY
                estimated = true
            }
        }

        if (voltageMv <= 0 || powerW <= 0 || powerW > 500) {
            powerW = 0.0
            estimated = false
            source = PowerSource.NONE
        }
        val currentA = if (powerW > 0 && voltageMv > 0) powerW * 1000.0 / voltageMv else 0.0

        return BatterySnapshot(
            level = level,
            voltageMv = voltageMv,
            currentA = currentA,
            powerW = powerW,
            charging = charging,
            full = full,
            plugged = plugged,
            tempC = tempC,
            estimated = estimated,
            available = powerW > 0.001,
            source = source,
            timestamp = now,
            voltageEstimated = voltageEstimated
        )
    }

    private fun pushSample(queue: ArrayDeque<Sample>, sample: Sample) {
        synchronized(queue) {
            queue.addLast(sample)
            while (queue.size > MAX_SAMPLES) queue.removeFirst()
        }
    }

    /**
     * 用电量计（µAh）在一段时间内的变化量估算平均功率。
     * ColorOS 的电量计数值可能 5~10 分钟才跳变一次，因此估算窗口要拉长：
     * 优先取“值与最新不同且跨度 >= 30 秒”的采样对，兜底取 8 分钟前的采样。
     */
    private fun estimateFromCharge(voltageMv: Int): Double? {
        val copy: List<Sample>
        synchronized(chargeSamples) { copy = chargeSamples.toList() }
        if (copy.size < 2 || voltageMv <= 0) return null
        val newest = copy.last()
        var oldest: Sample? = null
        // 优先：值与最新不同、跨度 >= 30s
        for (i in copy.size - 2 downTo 0) {
            val s = copy[i]
            if (newest.value != s.value && newest.timeMs - s.timeMs >= 30_000) {
                oldest = s
                break
            }
        }
        // 兜底：跨度 >= 8 分钟（即使值相同，说明窗口内无跳变，会返回 null）
        if (oldest == null) {
            for (i in copy.size - 2 downTo 0) {
                val s = copy[i]
                if (newest.timeMs - s.timeMs >= 480_000) {
                    oldest = s
                    break
                }
            }
        }
        val o = oldest ?: return null
        val dtSec = (newest.timeMs - o.timeMs) / 1000.0
        if (dtSec < 25.0) return null
        val dqUah = newest.value - o.value
        if (dqUah == 0L) return null
        val power = abs(voltageMv.toDouble() * dqUah * 3600.0 / (1_000_000_000.0 * dtSec))
        return if (power in 0.05..500.0) power else null
    }

    /** 用能量计（nWh，若系统提供）差分估算功率：dE/dt。窗口策略同上。 */
    private fun estimateFromEnergy(): Double? {
        val copy: List<Sample>
        synchronized(energySamples) { copy = energySamples.toList() }
        if (copy.size < 2) return null
        val newest = copy.last()
        var oldest: Sample? = null
        for (i in copy.size - 2 downTo 0) {
            val s = copy[i]
            if (newest.value != s.value && newest.timeMs - s.timeMs >= 30_000) {
                oldest = s
                break
            }
        }
        if (oldest == null) {
            for (i in copy.size - 2 downTo 0) {
                val s = copy[i]
                if (newest.timeMs - s.timeMs >= 480_000) {
                    oldest = s
                    break
                }
            }
        }
        val o = oldest ?: return null
        val dtSec = (newest.timeMs - o.timeMs) / 1000.0
        if (dtSec < 25.0) return null
        val dNwh = newest.value - o.value
        if (dNwh == 0L) return null
        val power = abs(dNwh) / dtSec / 1_000_000_000.0
        return if (power in 0.05..500.0) power else null
    }

    private var lastCalibLevel = -1

    /** 电量变化一个百分点就记录一个校准点，持久化后重启也能用于健康度计算。 */
    private fun persistCalibration(level: Int, counterUah: Long) {
        // 健康度检测只在电量 20%~85% 区间进行，区间外不采集校准点
        if (level !in 20..85) return
        if (level == lastCalibLevel || counterUah <= 0) return
        lastCalibLevel = level
        Prefs.addCalibrationPoint(level, counterUah)
    }

    /**
     * 用“电量变化量 ÷ 电量百分比变化”估算满电容量（mAh）：
     * FCC = Δ电量(µAh) / Δ电量百分比 × 100 / 1000。
     * 合并历史持久化校准点与本次会话采样，取电量跨度最大的采样对。
     */
    fun estimateFccMah(): Double? {
        val current: List<Sample>
        synchronized(chargeSamples) { current = chargeSamples.toList() }
        val calib = Prefs.getCalibrationPoints()
        val all = buildList {
            calib.forEach { (lv, counter) ->
                add(Sample(0L, counter, 0, lv))
            }
            addAll(current)
        }.filter { it.level in 20..85 } // 只使用电量 20%~85% 区间的采样对
        if (all.size < 2) return null
        var bestFcc = 0.0
        var bestSpan = 0
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]
                val b = all[j]
                val span = abs(b.level - a.level)
                if (span >= 1) {
                    val dq = abs(b.value - a.value).toDouble() // µAh
                    // 双电芯机型的电量计通常只对应单电芯，容量按整机口径需要 ×2
                    val fcc = dq / span * 100.0 / 1000.0 * (if (Prefs.dualCell) 2.0 else 1.0) // mAh
                    if (fcc in 2000.0..30000.0 && span > bestSpan) {
                        bestFcc = fcc
                        bestSpan = span
                    }
                }
            }
        }
        return if (bestFcc > 0) bestFcc else null
    }

    /**
     * 用“电量计读数 ÷ 当前电量 × 100”即时估算满电容量（mAh）。
     * 电量百分比本来就是电量计按 rm/fcc 算出来的，反推即可得到满电容量，
     * 无需等待电量变化。电量在 5%~95% 之间时才可信。
     */
    fun estimateFccFromLevel(): Double? {
        val lv = lastLevel
        val counter = lastCounterUah
        // 电量 20%~85% 区间外（如涓流区/快充末期）电量计换算不可信
        if (lv in 20..85 && counter > 0) {
            // 双电芯机型：电量计对应单电芯容量，换算整机口径需 ×2
            val fcc = counter / lv.toDouble() * 100.0 / 1000.0 * (if (Prefs.dualCell) 2.0 else 1.0)
            if (fcc in 2000.0..30000.0) return fcc
        }
        return null
    }
}

/** 进程内共享的功率数据流：服务写入，主界面与悬浮胶囊读取。 */
object PowerStore {
    private val _flow = MutableStateFlow(BatterySnapshot.NONE)
    val flow: StateFlow<BatterySnapshot> = _flow

    @Volatile
    var lastReadMs: Long = 0L
        private set

    // 估算值平滑：电量计按 1mAh 步进跳变，直接显示会来回跳
    @Volatile
    private var smoothedW = 0.0
    @Volatile
    private var lastEstimateMs = 0L
    @Volatile
    private var lastEstimateCharging = false
    private const val HOLD_MS = 480_000L

    fun refresh(context: Context) {
        val raw = runCatching { PowerReader.read(context) }.getOrDefault(BatterySnapshot.NONE)
        val now = System.currentTimeMillis()
        val out: BatterySnapshot = when {
            raw.estimated && raw.powerW > 0 -> {
                val s = if (smoothedW > 0) smoothedW * 0.6 + raw.powerW * 0.4 else raw.powerW
                smoothedW = s
                lastEstimateMs = now
                lastEstimateCharging = raw.charging
                raw.copy(
                    powerW = s,
                    currentA = if (raw.voltageMv > 0) s * 1000.0 / raw.voltageMv else 0.0,
                    available = s > 0.001
                )
            }
            // 电量计长时间不跳变：保持上一次估算值，避免显示闪烁
            raw.source == PowerSource.NONE && smoothedW > 0 &&
                raw.charging == lastEstimateCharging && now - lastEstimateMs < HOLD_MS -> {
                raw.copy(
                    powerW = smoothedW,
                    currentA = if (raw.voltageMv > 0) smoothedW * 1000.0 / raw.voltageMv else 0.0,
                    estimated = true,
                    available = true,
                    source = PowerSource.ESTIMATE_CHARGE
                )
            }
            else -> {
                smoothedW = 0.0
                raw
            }
        }
        _flow.value = out
        lastReadMs = now
    }
}
