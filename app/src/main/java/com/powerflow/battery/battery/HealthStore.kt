package com.powerflow.battery.battery

import android.os.Build
import com.powerflow.battery.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 电池健康度（SOH）= 当前满电容量（batteryFcc）÷ 设计容量。
 * 设计容量优先用用户设置 → 内置机型表 → 观测到的最大满电容量兜底。
 */
object HealthStore {
    data class HealthInfo(
        val fccMah: Double,
        val designMah: Int,
        val sohPct: Double,
        val available: Boolean,
        val designFallback: Boolean,
        val sampleCount: Int
    ) {
        companion object {
            val NONE = HealthInfo(0.0, 0, 0.0, false, false, 0)
        }
    }

    private val _flow = MutableStateFlow(HealthInfo.NONE)
    val flow: StateFlow<HealthInfo> = _flow

    // 内置机型设计容量表（mAh，典型值）
    private val designTable = mapOf(
        "OPD2506" to 10420 // OPPO Pad 5
    )

    fun refresh() {
        // 满电容量估算：电量计÷电量×100 即时估算 → 电量差分法估算
        // 每次测量都入平均，采样越多越准确
        val fccLevel = PowerReader.estimateFccFromLevel()
        val fccDelta = PowerReader.estimateFccMah()
        fccLevel?.let { Prefs.addFccSample(it) }
        fccDelta?.let { Prefs.addFccSample(it) }
        val fcc = Prefs.avgFcc()
        if (fcc <= 0) return
        // 学习观测到的最大满电容量，作为未知机型的设计容量参考
        if (fcc.toInt() > Prefs.maxFcc) Prefs.maxFcc = fcc.toInt()
        var design = Prefs.designCapacity
        var fallback = false
        if (design <= 0) {
            design = designTable[Build.MODEL.uppercase()] ?: 0
        }
        if (design <= 0) {
            design = Prefs.maxFcc
            fallback = true
        }
        if (design <= 0) return
        val soh = fcc / design * 100.0
        _flow.value = HealthInfo(
            fccMah = fcc,
            designMah = design,
            sohPct = soh.coerceIn(0.0, 120.0),
            available = true,
            designFallback = fallback,
            sampleCount = Prefs.fccSampleCount()
        )
    }
}
