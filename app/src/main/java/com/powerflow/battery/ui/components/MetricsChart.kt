package com.powerflow.battery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerflow.battery.R
import com.powerflow.battery.battery.HistoryStore
import com.powerflow.battery.ui.theme.AccentCharging
import com.powerflow.battery.ui.theme.LiquidAmber
import com.powerflow.battery.ui.theme.LiquidBlue
import com.powerflow.battery.util.Format
import com.powerflow.battery.util.Prefs

/**
 * 功率 / 电压 / 电流三区曲线：上区功率、中区电压、下区电流，各区独立缩放。
 * 数据保留原始起伏（不做平滑）；新数据点到达时整条线连续向左滑动（滚动动画），
 * 刷新观感流畅连贯。每条线末端是小胶囊，实时显示该区当前值。
 */
@Composable
fun MetricsChart(points: List<HistoryStore.Point>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val gridColor = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF10233D).copy(alpha = 0.08f)
    val textMeasurer = rememberTextMeasurer()
    val powerName = stringResource(R.string.chart_power)
    val voltageName = stringResource(R.string.chart_voltage)
    val currentName = stringResource(R.string.chart_current)

    // 滚动动画：每次新点到达，相位从 0 滑到 1（持续一个刷新周期），整条线连续左移
    val slide = remember { Animatable(0f) }
    val lastTs = points.lastOrNull()?.timestamp
    LaunchedEffect(lastTs) {
        if (lastTs != null) {
            slide.snapTo(0f)
            slide.animateTo(
                1f,
                animationSpec = tween(durationMillis = Prefs.refreshMs, easing = LinearEasing)
            )
        }
    }
    val phi = slide.value

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pillW = 54.dp.toPx()
        val pillH = 18.dp.toPx()
        val plotLeft = 10.dp.toPx()
        val plotRight = (w - pillW - 6.dp.toPx()).coerceAtLeast(plotLeft + 1f)

        // 上中下三区：功率 / 电压 / 电流
        val zoneGap = 4.dp.toPx()
        val zoneH = (h - 2 * zoneGap) / 3f
        val zones = listOf(
            0f to zoneH,
            (zoneH + zoneGap) to (2 * zoneH + zoneGap),
            (2 * zoneH + 2 * zoneGap) to h
        )

        // 区内网格线
        for ((zt, zb) in zones) {
            for (i in 1..2) {
                val y = zt + (zb - zt) * i / 3f
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
            }
        }
        if (points.size < 2) return@Canvas

        // 固定滑动窗口：始终显示最近 WINDOW 个点，新点到达时整条线左移一格
        val window = 60
        val win = points.takeLast(window)
        val stepX = (plotRight - plotLeft) / (window - 1)
        // 点数不足窗口时靠右排布，曲线从右向左生长
        val slotOffset = window - win.size

        fun drawZone(color: Color, values: List<Float>, name: String, value: String, zone: Pair<Float, Float>) {
            val (zt, zb) = zone
            val zoneHh = zb - zt
            val pad = zoneHh * 0.16f
            var lo = values[0]
            var hi = values[0]
            for (v in values) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            val range = (hi - lo).coerceAtLeast(0.0001f)
            val pts = values.mapIndexed { i, v ->
                Offset(
                    plotLeft + (i + slotOffset - phi) * stepX,
                    zb - pad - ((v - lo) / range) * (zoneHh - 2 * pad)
                )
            }

            // 原始数据直连，保留起伏
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
            }
            drawPath(
                path, color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 头部小胶囊：显示当前值
            val last = pts.last()
            val left = last.x + 2.dp.toPx()
            val top = (last.y - pillH / 2).coerceIn(zt + 2.dp.toPx(), zb - pillH - 2.dp.toPx())
            val rect = Rect(left, top, left + pillW, top + pillH)
            drawRoundRect(
                color.copy(alpha = 0.94f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(pillH / 2)
            )
            val valueLayout = textMeasurer.measure(
                AnnotatedString(value),
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                valueLayout,
                topLeft = Offset(
                    rect.left + (rect.width - valueLayout.size.width) / 2,
                    rect.top + (rect.height - valueLayout.size.height) / 2
                )
            )

            // 区内左上角指标名
            val nameLayout = textMeasurer.measure(
                AnnotatedString(name),
                style = TextStyle(color = color.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            )
            drawText(nameLayout, topLeft = Offset(plotLeft + 2.dp.toPx(), zt + 2.dp.toPx()))
        }

        val last = win.last()
        drawZone(AccentCharging, win.map { it.powerW.toFloat() }, powerName, Format.powerWithUnit(last.powerW), zones[0])
        drawZone(LiquidBlue, win.map { it.voltageMv.toFloat() }, voltageName, Format.voltage(last.voltageMv), zones[1])
        drawZone(LiquidAmber, win.map { it.currentA.toFloat() }, currentName, Format.current(last.currentA), zones[2])
    }
}
