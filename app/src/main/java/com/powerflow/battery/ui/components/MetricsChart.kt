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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerflow.battery.battery.HistoryStore
import com.powerflow.battery.ui.theme.AccentCharging
import com.powerflow.battery.ui.theme.LiquidAmber
import com.powerflow.battery.ui.theme.LiquidBlue
import com.powerflow.battery.util.Format
import kotlin.math.max
import kotlin.math.min

/**
 * 功率 / 电流 / 电压三线合一曲线。
 * 每条线末端是一个显示当前数值的胶囊头部（形似精子游动），不同颜色区分，各自独立缩放。
 */
@Composable
fun MetricsChart(points: List<HistoryStore.Point>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val gridColor = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF10233D).copy(alpha = 0.10f)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pillW = 64.dp.toPx()
        val pillH = 20.dp.toPx()
        val plotLeft = 10.dp.toPx()
        val plotRight = (w - pillW - 8.dp.toPx()).coerceAtLeast(plotLeft + 1f)

        // 横向网格
        for (i in 0..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
        }
        if (points.size < 2) return@Canvas

        // 每个序列独立映射到绘图区（避免量级差异互相压扁），
        // biasPx 给三条线分配泳道：功率在中、电压偏上、电流偏下，互不遮挡
        fun seriesOffsets(values: List<Float>, biasPx: Float): List<Offset> {
            var lo = values[0]
            var hi = values[0]
            for (v in values) {
                lo = min(lo, v)
                hi = max(hi, v)
            }
            val range = (hi - lo).coerceAtLeast(0.0001f)
            val stepX = (plotRight - plotLeft) / (values.size - 1)
            val topPad = h * 0.12f
            val bottomPad = h * 0.12f
            return values.mapIndexed { i, v ->
                Offset(
                    plotLeft + stepX * i,
                    h - bottomPad - ((v - lo) / range) * (h - topPad - bottomPad) + biasPx
                )
            }
        }

        // 尾线 + 头部胶囊：胶囊内显示该指标当前值
        fun drawSperm(color: Color, values: List<Float>, label: String, biasPx: Float) {
            val pts = seriesOffsets(values, biasPx)
            if (pts.size < 2) return
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
            }
            drawPath(
                path, color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            val last = pts.last()
            val left = plotRight + 2.dp.toPx()
            val top = (last.y - pillH / 2).coerceIn(h * 0.02f, h - pillH - h * 0.02f)
            val rect = Rect(left, top, left + pillW, top + pillH)
            drawRoundRect(
                color.copy(alpha = 0.94f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(pillH / 2)
            )
            val layout = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                layout,
                topLeft = Offset(
                    rect.left + (rect.width - layout.size.width) / 2,
                    rect.top + (rect.height - layout.size.height) / 2
                )
            )
        }

        val lane = h * 0.11f
        drawSperm(LiquidBlue, points.map { it.voltageMv.toFloat() }, Format.voltage(points.last().voltageMv), -lane)
        drawSperm(LiquidAmber, points.map { it.currentA.toFloat() }, Format.current(points.last().currentA), lane)
        drawSperm(AccentCharging, points.map { it.powerW.toFloat() }, Format.powerWithUnit(points.last().powerW), 0f)
    }
}
