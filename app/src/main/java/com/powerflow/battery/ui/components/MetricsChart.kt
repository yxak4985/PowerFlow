package com.powerflow.battery.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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

/**
 * 功率 / 电压 / 电流三区曲线：上区功率、中区电压、下区电流，各区独立缩放。
 * 数据先做滑动平均，再用二次贝塞尔中点插值平滑，视觉上更流畅、刷新更连贯；
 * 每条线末端是一个小胶囊，实时显示该区当前值。
 */
@Composable
fun MetricsChart(points: List<HistoryStore.Point>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val gridColor = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF10233D).copy(alpha = 0.08f)
    val textMeasurer = rememberTextMeasurer()
    val powerName = stringResource(R.string.chart_power)
    val voltageName = stringResource(R.string.chart_voltage)
    val currentName = stringResource(R.string.chart_current)

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

        // 滑动平均去抖，让曲线更平滑
        fun movingAverage(values: List<Float>): List<Float> {
            if (values.size < 3) return values
            val out = values.toMutableList()
            for (i in 1 until values.size - 1) {
                out[i] = (values[i - 1] + values[i] * 2f + values[i + 1]) / 4f
            }
            return out
        }

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
            val stepX = (plotRight - plotLeft) / (values.size - 1)
            val smooth = movingAverage(values)
            val pts = smooth.mapIndexed { i, v ->
                Offset(plotLeft + stepX * i, zb - pad - ((v - lo) / range) * (zoneHh - 2 * pad))
            }

            // 二次贝塞尔中点插值：圆滑连接所有点
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.size - 1) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    quadraticBezierTo(a.x, a.y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
            }
            drawPath(
                path, color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 头部小胶囊：显示当前值
            val last = pts.last()
            val left = plotRight + 2.dp.toPx()
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

        val last = points.last()
        drawZone(AccentCharging, points.map { it.powerW.toFloat() }, powerName, Format.powerWithUnit(last.powerW), zones[0])
        drawZone(LiquidBlue, points.map { it.voltageMv.toFloat() }, voltageName, Format.voltage(last.voltageMv), zones[1])
        drawZone(LiquidAmber, points.map { it.currentA.toFloat() }, currentName, Format.current(last.currentA), zones[2])
    }
}
