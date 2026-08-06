package com.powerflow.battery.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.isActive
import kotlin.math.min

/**
 * 功率 / 电压 / 电流三区曲线：上区功率、中区电压、下区电流，各区独立缩放。
 * 纯时间轴滚动：每个点按时间戳逐帧连续左移（右缘预留一个刷新间隔），没有归零跳变；
 * 胶囊带惯性指数平滑，像球一样平滑滚到新数据位置。数据保留原始起伏。
 */
@Composable
fun MetricsChart(points: List<HistoryStore.Point>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val gridColor = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF10233D).copy(alpha = 0.08f)
    val textMeasurer = rememberTextMeasurer()
    val powerName = stringResource(R.string.chart_power)
    val voltageName = stringResource(R.string.chart_voltage)
    val currentName = stringResource(R.string.chart_current)
    val refreshMs = Prefs.refreshMs

    // 逐帧刷新时钟：曲线按时间连续滚动
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { }
            now = System.currentTimeMillis()
        }
    }
    // 胶囊的水平位置（px），带惯性平滑，避免新点到达时瞬移
    val capsuleX = remember { FloatArray(1) { -1f } }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pillW = 54.dp.toPx()
        val pillH = 18.dp.toPx()
        val plotLeft = 10.dp.toPx()
        val plotRight = (w - pillW - 6.dp.toPx()).coerceAtLeast(plotLeft + 1f)
        val windowMs = 60_000L // 显示最近 60 秒
        val speed = (plotRight - plotLeft) / windowMs.toFloat()

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

        val lastTs = points.last().timestamp
        // 无新数据时曲线停在原位（最多停在最新点后两个刷新周期）
        val anchorNow = minOf(now, lastTs + refreshMs * 2L)
        val win = points.filter { anchorNow + refreshMs - it.timestamp <= windowMs }
        if (win.size < 2) return@Canvas

        // 最新点横向目标位置（右缘预留一个刷新间隔）
        val targetX = plotRight - (anchorNow + refreshMs - lastTs) * speed
        if (capsuleX[0] < 0f) capsuleX[0] = targetX
        capsuleX[0] += (targetX - capsuleX[0]) * 0.22f // 惯性追赶，像球滚动

        fun xOf(t: Long): Float = plotRight - (anchorNow + refreshMs - t) * speed

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
            val pts = win.mapIndexed { i, p ->
                Offset(xOf(p.timestamp), zb - pad - ((values[i] - lo) / range) * (zoneHh - 2 * pad))
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

            // 头部小胶囊：水平位置惯性平滑，垂直跟随最新值
            val headY = pts.last().y
            val left = (capsuleX[0] + 2.dp.toPx()).coerceIn(plotLeft, w - pillW)
            val top = (headY - pillH / 2).coerceIn(zt + 2.dp.toPx(), zb - pillH - 2.dp.toPx())
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
