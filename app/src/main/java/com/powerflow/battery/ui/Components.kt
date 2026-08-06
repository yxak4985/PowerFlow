package com.powerflow.battery.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerflow.battery.R
import com.powerflow.battery.ui.theme.AccentCharging
import com.powerflow.battery.ui.theme.AccentDischarging
import com.powerflow.battery.ui.theme.LiquidAqua
import com.powerflow.battery.ui.theme.LiquidAmber
import com.powerflow.battery.ui.theme.LiquidBlue
import com.powerflow.battery.ui.theme.LiquidPink
import com.powerflow.battery.ui.theme.LiquidViolet
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

/** 一天中的天空配色节点（小时 → 天空上下渐变色 + 主光斑色），跟随手机时钟平滑变化。 */
private data class SkyStop(val hour: Float, val top: Color, val bottom: Color, val accent: Color)

private val dayStops = listOf(
    SkyStop(0f, Color(0xFF0A1530), Color(0xFF060D20), Color(0xFF4FA3FF)),
    SkyStop(4.5f, Color(0xFF0E1B3E), Color(0xFF081028), Color(0xFF4FA3FF)),
    SkyStop(6.5f, Color(0xFFBFE0FF), Color(0xFFE8F4FF), Color(0xFF35D9C0)),
    SkyStop(9.5f, Color(0xFFDCEFFF), Color(0xFFF6FBFF), Color(0xFF4FA3FF)),
    SkyStop(12.5f, Color(0xFFFFF3D6), Color(0xFFFDFAF2), Color(0xFFFFB74D)),
    SkyStop(15.5f, Color(0xFFD6ECFF), Color(0xFFF2FAFF), Color(0xFF35D9C0)),
    SkyStop(18f, Color(0xFFFFE2B8), Color(0xFFFFE3D6), Color(0xFFFFB74D)),
    SkyStop(19.5f, Color(0xFFFFB87A), Color(0xFFFFB9C8), Color(0xFFFF7EB6)),
    SkyStop(21.5f, Color(0xFF6B5B96), Color(0xFF3B2F63), Color(0xFF8E7CFF)),
    SkyStop(23f, Color(0xFF101B3C), Color(0xFF081029), Color(0xFF4FA3FF)),
    SkyStop(24f, Color(0xFF0A1530), Color(0xFF060D20), Color(0xFF4FA3FF))
)

/** 读取手机当前时间，在两个相邻配色节点间平滑插值。 */
private fun currentSky(): SkyStop {
    val now = LocalTime.now()
    val hour = now.hour + now.minute / 60f + now.second / 3600f
    val stops = dayStops
    var i = 0
    while (i < stops.size - 2 && hour > stops[i + 1].hour) i++
    val a = stops[i]
    val b = stops[i + 1]
    val raw = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
    val f = raw * raw * (3f - 2f * raw) // smoothstep：过渡不生硬
    return SkyStop(
        hour = hour,
        top = lerp(a.top, b.top, f),
        bottom = lerp(a.bottom, b.bottom, f),
        accent = lerp(a.accent, b.accent, f)
    )
}

/** 白天强度曲线：正午最大、清晨与黄昏递减、深夜最小，用于调节光斑明暗。 */
private fun daylightFactor(hour: Float): Float {
    val v = sin((hour - 6f) / 24f * 2f * PI.toFloat())
    return ((v + 1f) / 2f).coerceIn(0f, 1f)
}

/**
 * 动态极光背景：模拟 ColorOS 16 光场设计。
 * 配色跟随手机时钟一天内平滑变化（正午明亮、下午清爽、傍晚黄昏、夜里深蓝），
 * 多层光斑持续缓慢漂移并轻微呼吸，更具灵动感。
 */
@Composable
fun AuroraBackground(dark: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_t1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_t2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_t3"
    )

    val sky = currentSky()
    // 白天色调叠加到主题底色上：深色模式保持暗色可读，浅色模式保持明亮
    val baseTop = if (dark) lerp(Color(0xFF081426), sky.top, 0.30f) else lerp(Color.White, sky.top, 0.55f)
    val baseBottom = if (dark) lerp(Color(0xFF0E2438), sky.bottom, 0.26f) else lerp(Color.White, sky.bottom, 0.50f)
    val alpha = (0.10f + 0.22f * daylightFactor(sky.hour)).coerceIn(0f, 0.34f)

    Box(modifier.background(Brush.verticalGradient(listOf(baseTop, baseBottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(center: Offset, radiusBase: Float, t: Float, color: Color, a: Float) {
                val rr = radiusBase * (1f + 0.10f * sin(t * 2f * PI.toFloat()))
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center, rr),
                    radius = rr,
                    center = center
                )
            }
            blob(
                Offset(w * (0.10f + 0.24f * t1), h * (0.06f + 0.14f * t2)),
                w * 0.62f, t1, sky.accent, alpha
            )
            blob(
                Offset(w * (0.88f - 0.26f * t2), h * (0.24f + 0.20f * t3)),
                w * 0.55f, t2, LiquidAqua, alpha * 0.85f
            )
            blob(
                Offset(w * (0.40f + 0.22f * t3), h * (0.96f - 0.26f * t1)),
                w * 0.60f, t3, LiquidViolet, alpha * 0.80f
            )
            blob(
                Offset(w * (0.72f + 0.16f * t1), h * (0.55f + 0.22f * t3)),
                w * 0.50f, t1, LiquidPink, alpha * 0.65f
            )
            blob(
                Offset(w * (0.04f + 0.12f * t3), h * (0.72f - 0.18f * t2)),
                w * 0.45f, t2, LiquidAmber, alpha * 0.55f
            )
        }
    }
}

private data class NavItem(
    val label: String,
    val unselected: ImageVector,
    val selected: ImageVector
)

/** 底部悬浮玻璃导航栏（微信式：图标 + 文字）。 */
@Composable
fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    val items = listOf(
        NavItem(stringResource(R.string.tab_measure), Icons.Outlined.Speed, Icons.Filled.Speed),
        NavItem(stringResource(R.string.tab_settings), Icons.Outlined.Settings, Icons.Filled.Settings)
    )
    NavigationBar(
        containerColor = if (dark) Color(0xE6122438) else Color(0xEFFFFFFF),
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.65f),
                RoundedCornerShape(26.dp)
            )
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selected == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selected else item.unselected,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** 液态玻璃卡片。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(corner)
    val fill = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.46f)
    val border = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
    Box(
        modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color(0xFF2B5C8A).copy(alpha = 0.25f),
                spotColor = Color(0xFF1B3E62).copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(fill, fill.copy(alpha = fill.alpha * 0.55f))))
            .border(1.dp, border, shape)
            .padding(padding)
    ) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        modifier = modifier
    )
}

@Composable
fun GlassSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.35f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val dark = isSystemInDarkTheme()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.4f),
            contentColor = if (dark) Color.White else Color(0xFF10233D),
            disabledContainerColor = Color.Gray.copy(alpha = 0.18f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusPill(running: Boolean, modifier: Modifier = Modifier) {
    val color = if (running) AccentCharging else MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (running) "运行中" else "已停止"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (running) 0.16f else 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 电量环形进度。 */
@Composable
fun BatteryRing(
    level: Int,
    charging: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val accent = when {
        charging -> AccentCharging
        else -> AccentDischarging
    }
    val track = if (dark) Color.White.copy(alpha = 0.22f) else Color(0xFF10233D).copy(alpha = 0.14f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.10f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * level.coerceIn(0, 100) / 100f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$level",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
