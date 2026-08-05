package com.powerflow.battery.util

import java.util.Locale

object Format {
    fun power(watts: Double): String = when {
        watts <= 0 -> "0.0"
        watts >= 100 -> String.format(Locale.CHINA, "%.0f", watts)
        else -> String.format(Locale.CHINA, "%.1f", watts)
    }

    fun powerWithUnit(watts: Double): String = "${power(watts)}W"

    /** 电流入参单位为安培（A），自动选择 A 或 mA 显示。 */
    fun current(currentA: Double): String = when {
        currentA <= 0 -> "—"
        currentA >= 1 -> String.format(Locale.CHINA, "%.2fA", currentA)
        else -> String.format(Locale.CHINA, "%.0fmA", currentA * 1000)
    }

    fun voltage(mv: Int): String = if (mv > 0) String.format(Locale.CHINA, "%.2fV", mv / 1000.0) else "—"

    fun temp(c: Double): String = if (c > 0) String.format(Locale.CHINA, "%.1f°C", c) else "—"
}
