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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerflow.battery.ui.theme.AccentCharging
import com.powerflow.battery.ui.theme.AccentDischarging
import com.powerflow.battery.ui.theme.LiquidAqua
import com.powerflow.battery.ui.theme.LiquidBlue
import com.powerflow.battery.ui.theme.LiquidViolet

/** 动态极光背景：模拟 ColorOS 16 光场设计。 */
@Composable
fun AuroraBackground(dark: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_t"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_t2"
    )
    val base = if (dark) {
        listOf(Color(0xFF081426), Color(0xFF0E2438))
    } else {
        listOf(Color(0xFFE7F1FF), Color(0xFFF7FBFF))
    }
    val alphaScale = if (dark) 0.32f else 0.28f
    val c1 = LiquidBlue.copy(alpha = alphaScale)
    val c2 = LiquidAqua.copy(alpha = alphaScale * 0.8f)
    val c3 = LiquidViolet.copy(alpha = alphaScale * 0.75f)

    Box(modifier.background(Brush.verticalGradient(base))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p1 = Offset(w * (0.10f + 0.30f * t), h * (0.08f + 0.10f * t))
            val p2 = Offset(w * (0.88f - 0.28f * t2), h * (0.28f + 0.18f * t2))
            val p3 = Offset(w * (0.42f + 0.16f * t), h * (0.95f - 0.25f * t2))
            drawCircle(
                brush = Brush.radialGradient(listOf(c1, Color.Transparent), center = p1, radius = w * 0.55f),
                radius = w * 0.55f,
                center = p1
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(c2, Color.Transparent), center = p2, radius = w * 0.6f),
                radius = w * 0.6f,
                center = p2
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(c3, Color.Transparent), center = p3, radius = w * 0.65f),
                radius = w * 0.65f,
                center = p3
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
