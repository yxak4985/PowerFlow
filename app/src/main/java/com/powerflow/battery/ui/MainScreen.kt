package com.powerflow.battery.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.powerflow.battery.R
import com.powerflow.battery.battery.BatterySnapshot
import com.powerflow.battery.battery.HistoryStore
import com.powerflow.battery.battery.HealthStore
import com.powerflow.battery.battery.PowerStore
import com.powerflow.battery.battery.PowerSource
import com.powerflow.battery.service.PowerMonitorService
import com.powerflow.battery.ui.components.AuroraBackground
import com.powerflow.battery.ui.components.BatteryRing
import com.powerflow.battery.ui.components.GlassButton
import com.powerflow.battery.ui.components.GlassCard
import com.powerflow.battery.ui.components.GlassNavBar
import com.powerflow.battery.ui.components.GlassSwitchRow
import com.powerflow.battery.ui.components.MetricsChart
import com.powerflow.battery.ui.components.SectionTitle
import com.powerflow.battery.ui.components.StatusPill
import com.powerflow.battery.ui.theme.AccentCharging
import com.powerflow.battery.ui.theme.AccentDischarging
import com.powerflow.battery.ui.theme.AccentFull
import com.powerflow.battery.ui.theme.LiquidAmber
import com.powerflow.battery.util.Format
import com.powerflow.battery.util.OppoHelper
import com.powerflow.battery.util.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val snapshot by PowerStore.flow.collectAsState()
    val health by HealthStore.flow.collectAsState()

    var selectedPage by rememberSaveable { mutableStateOf(0) }
    var monitorOn by remember { mutableStateOf(Prefs.monitorEnabled) }
    var chipOn by remember { mutableStateOf(Prefs.statusBarChip) }
    var capsuleOn by remember { mutableStateOf(Prefs.capsuleEnabled) }
    var lockCapsuleOn by remember { mutableStateOf(Prefs.capsuleLockScreen) }
    var refreshMs by remember { mutableStateOf(Prefs.refreshMs) }
    var dualCellOn by remember { mutableStateOf(Prefs.dualCell) }
    var notifGranted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showDesignDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var designInput by remember { mutableStateOf("") }
    var showOverlayPrompt by remember { mutableStateOf(false) }
    var showBatteryPrompt by remember { mutableStateOf(false) }
    val promotedAvailable = Build.VERSION.SDK_INT >= 36
    var promotedGranted by remember {
        mutableStateOf(!promotedAvailable || OppoHelper.canPostPromoted(context))
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LaunchedEffect(Unit) {
        // 启动时自动请求正常运行所需权限
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 &&
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!Settings.canDrawOverlays(context) && Prefs.capsuleEnabled) {
                showOverlayPrompt = true
            }
            val pm = context.getSystemService(PowerManager::class.java)
            if (Prefs.monitorEnabled &&
                Build.VERSION.SDK_INT >= 23 &&
                !pm.isIgnoringBatteryOptimizations(context.packageName)
            ) {
                showBatteryPrompt = true
            }
        }
        while (isActive) {
            runCatching {
                // 监控服务运行中由服务每秒刷新数据，界面只做权限等轻量检查
                if (!Prefs.monitorEnabled) {
                    PowerStore.refresh(context)
                    HealthStore.refresh()
                }
                notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                overlayGranted = Settings.canDrawOverlays(context)
                if (promotedAvailable) {
                    promotedGranted = OppoHelper.canPostPromoted(context)
                }
            }
            delay(5000)
        }
    }

    val toggleMonitor: (Boolean) -> Unit = { on ->
        Prefs.monitorEnabled = on
        monitorOn = on
        if (on) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PowerMonitorService::class.java)
                )
            }
        } else {
            runCatching {
                context.startService(
                    Intent(context, PowerMonitorService::class.java)
                        .setAction(PowerMonitorService.ACTION_STOP)
                )
            }
        }
    }

    val sendCapsuleUpdate: () -> Unit = {
        runCatching {
            context.startService(
                Intent(context, PowerMonitorService::class.java)
                    .setAction(PowerMonitorService.ACTION_UPDATE_CAPSULE)
            )
        }
    }

    val toggleCapsule: (Boolean) -> Unit = { on ->
        Prefs.capsuleEnabled = on
        capsuleOn = on
        if (on && !Settings.canDrawOverlays(context)) {
            OppoHelper.openOverlaySettings(context)
        } else {
            if (on && !Prefs.monitorEnabled) toggleMonitor(true)
            sendCapsuleUpdate()
        }
    }

    val toggleLockCapsule: (Boolean) -> Unit = { on ->
        Prefs.capsuleLockScreen = on
        lockCapsuleOn = on
        if (on && !Settings.canDrawOverlays(context)) {
            OppoHelper.openOverlaySettings(context)
        }
        sendCapsuleUpdate()
    }

    val toggleDualCell: (Boolean) -> Unit = { on ->
        Prefs.dualCell = on
        dualCellOn = on
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(dark = isSystemInDarkTheme(), modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassNavBar(selected = selectedPage, onSelect = { selectedPage = it })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                when (selectedPage) {
                    0 -> MeasurePage(
                        snapshot = snapshot,
                        health = health,
                        monitorOn = monitorOn,
                        onSetDesign = {
                            designInput = if (Prefs.designCapacity > 0) Prefs.designCapacity.toString() else ""
                            showDesignDialog = true
                        },
                        onResetHealth = { showResetDialog = true }
                    )
                    else -> SettingsPage(
                        monitorOn = monitorOn,
                        chipOn = chipOn,
                        capsuleOn = capsuleOn,
                        lockCapsuleOn = lockCapsuleOn,
                        dualCellOn = dualCellOn,
                        refreshMs = refreshMs,
                        notifGranted = notifGranted,
                        overlayGranted = overlayGranted,
                        promotedGranted = promotedGranted,
                        promotedAvailable = promotedAvailable,
                        onToggleMonitor = toggleMonitor,
                        onToggleChip = { on ->
                            Prefs.statusBarChip = on
                            chipOn = on
                        },
                        onToggleCapsule = toggleCapsule,
                        onToggleLockCapsule = toggleLockCapsule,
                        onToggleDualCell = toggleDualCell,
                        onRefreshMs = { ms ->
                            Prefs.refreshMs = ms
                            refreshMs = ms
                        },
                        onRequestNotif = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onOpenOverlay = { OppoHelper.openOverlaySettings(context) },
                        onOpenAutostart = { OppoHelper.openAutoStartSettings(context) },
                        onOpenBatteryOpt = { OppoHelper.openBatteryOptimization(context) },
                        onOpenPromoted = {
                            OppoHelper.openPromotedSettings(context)
                            promotedGranted = OppoHelper.canPostPromoted(context)
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showDesignDialog) {
            AlertDialog(
                onDismissRequest = { showDesignDialog = false },
                title = { Text(stringResource(R.string.health_design_title)) },
                text = {
                    OutlinedTextField(
                        value = designInput,
                        onValueChange = { designInput = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.health_design_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        designInput.toIntOrNull()?.takeIf { it > 0 }?.let { Prefs.designCapacity = it }
                        HealthStore.refresh()
                        showDesignDialog = false
                    }) {
                        Text(stringResource(R.string.health_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDesignDialog = false }) {
                        Text(stringResource(R.string.health_cancel))
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.health_reset_title)) },
                text = { Text(stringResource(R.string.health_reset_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        Prefs.resetHealthData()
                        HealthStore.refresh()
                        showResetDialog = false
                    }) {
                        Text(stringResource(R.string.health_reset_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringResource(R.string.health_cancel))
                    }
                }
            )
        }

        if (showOverlayPrompt) {
            AlertDialog(
                onDismissRequest = { showOverlayPrompt = false },
                title = { Text(stringResource(R.string.perm_overlay_title)) },
                text = { Text(stringResource(R.string.perm_overlay_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showOverlayPrompt = false
                        OppoHelper.openOverlaySettings(context)
                    }) {
                        Text(stringResource(R.string.perm_go))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayPrompt = false }) {
                        Text(stringResource(R.string.perm_later))
                    }
                }
            )
        }

        if (showBatteryPrompt) {
            AlertDialog(
                onDismissRequest = { showBatteryPrompt = false },
                title = { Text(stringResource(R.string.perm_battery_title)) },
                text = { Text(stringResource(R.string.perm_battery_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showBatteryPrompt = false
                        OppoHelper.openBatteryOptimization(context)
                    }) {
                        Text(stringResource(R.string.perm_go))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryPrompt = false }) {
                        Text(stringResource(R.string.perm_later))
                    }
                }
            )
        }
    }
}

/** 第一页：测量数据展示（功率、电量电压电流温度、电池健康）。 */
@Composable
private fun MeasurePage(
    snapshot: BatterySnapshot,
    health: HealthStore.HealthInfo,
    monitorOn: Boolean,
    onSetDesign: () -> Unit,
    onResetHealth: () -> Unit
) {
    val history by HistoryStore.flow.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (monitorOn) {
                        stringResource(R.string.main_hint_running)
                    } else {
                        stringResource(R.string.main_hint_stopped)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            StatusPill(running = monitorOn)
        }

        // 功率主卡片
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hero_power_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        val animated by animateFloatAsState(
                            targetValue = snapshot.powerW.toFloat(),
                            animationSpec = tween(700),
                            label = "power"
                        )
                        Text(
                            text = if (snapshot.available) Format.power(animated.toDouble()) else "—",
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "W",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when {
                            snapshot.full -> AccentFull
                            snapshot.charging -> AccentCharging
                            else -> AccentDischarging
                        }
                        val statusText = when {
                            snapshot.full -> stringResource(R.string.hero_full)
                            snapshot.charging -> stringResource(R.string.hero_charging)
                            else -> stringResource(R.string.hero_discharging)
                        }
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (snapshot.estimated) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.hero_estimate),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val sourceText = when (snapshot.source) {
                        PowerSource.SENSOR -> stringResource(R.string.source_sensor)
                        PowerSource.ESTIMATE_CHARGE -> stringResource(R.string.source_charge)
                        PowerSource.ESTIMATE_ENERGY -> stringResource(R.string.source_energy)
                        PowerSource.NONE -> stringResource(R.string.source_none)
                    }
                    Text(
                        text = sourceText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Box(Modifier.size(92.dp)) {
                    BatteryRing(
                        level = snapshot.level,
                        charging = snapshot.charging,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 数据卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                label = stringResource(R.string.tile_level),
                value = "${snapshot.level}%",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.tile_voltage),
                value = if (snapshot.voltageEstimated) {
                    "${Format.voltage(snapshot.voltageMv)} 估"
                } else {
                    Format.voltage(snapshot.voltageMv)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                label = stringResource(R.string.tile_current),
                value = Format.current(snapshot.currentA),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.tile_temp),
                value = Format.temp(snapshot.tempC),
                modifier = Modifier.weight(1f)
            )
        }

        // 实时曲线（功率 / 电流 / 电压 三线合一）
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(stringResource(R.string.section_chart))
                Spacer(Modifier.height(10.dp))
                if (history.size < 2) {
                    Text(
                        text = stringResource(R.string.chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 36.dp)
                    )
                } else {
                    MetricsChart(
                        points = history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                }
            }
        }

        // 电池健康
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(stringResource(R.string.section_health))
                Spacer(Modifier.height(10.dp))
                if (health.available) {
                    val healthColor = when {
                        health.sohPct >= 90 -> AccentCharging
                        health.sohPct >= 80 -> LiquidAmber
                        else -> MaterialTheme.colorScheme.error
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "电池健康度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.CHINA, "%.1f", health.sohPct) + "%",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Light,
                                color = healthColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${stringResource(R.string.health_capacity)} ${health.fccMah.toInt()} mAh（估算）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${stringResource(R.string.health_design)} ${health.designMah} mAh" +
                                    if (health.designFallback) {
                                        "（${stringResource(R.string.health_reference)}）"
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.CHINA, stringResource(R.string.health_samples), health.sampleCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.health_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.health_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = onSetDesign) {
                        Text(stringResource(R.string.health_set_design))
                    }
                    TextButton(onClick = onResetHealth) {
                        Text(
                            text = stringResource(R.string.health_reset),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/** 第二页：设置项（监控设置、权限与 OPPO 设置、数据说明）。 */
@Composable
private fun SettingsPage(
    monitorOn: Boolean,
    chipOn: Boolean,
    capsuleOn: Boolean,
    lockCapsuleOn: Boolean,
    dualCellOn: Boolean,
    refreshMs: Int,
    notifGranted: Boolean,
    overlayGranted: Boolean,
    promotedGranted: Boolean,
    promotedAvailable: Boolean,
    onToggleMonitor: (Boolean) -> Unit,
    onToggleChip: (Boolean) -> Unit,
    onToggleCapsule: (Boolean) -> Unit,
    onToggleLockCapsule: (Boolean) -> Unit,
    onToggleDualCell: (Boolean) -> Unit,
    onRefreshMs: (Int) -> Unit,
    onRequestNotif: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenBatteryOpt: () -> Unit,
    onOpenPromoted: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 监控设置
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(stringResource(R.string.section_monitor))
                Spacer(Modifier.height(8.dp))
                GlassSwitchRow(
                    title = stringResource(R.string.switch_monitor),
                    subtitle = stringResource(R.string.switch_monitor_sub),
                    checked = monitorOn,
                    onCheckedChange = onToggleMonitor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                GlassSwitchRow(
                    title = stringResource(R.string.switch_chip),
                    subtitle = stringResource(R.string.switch_chip_sub),
                    checked = chipOn && monitorOn,
                    enabled = monitorOn,
                    onCheckedChange = onToggleChip
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                GlassSwitchRow(
                    title = stringResource(R.string.switch_capsule),
                    subtitle = stringResource(R.string.switch_capsule_sub),
                    checked = capsuleOn && monitorOn,
                    enabled = monitorOn,
                    onCheckedChange = onToggleCapsule
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                GlassSwitchRow(
                    title = stringResource(R.string.switch_lock_capsule),
                    subtitle = stringResource(R.string.switch_lock_capsule_sub),
                    checked = lockCapsuleOn,
                    enabled = monitorOn,
                    onCheckedChange = onToggleLockCapsule
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                GlassSwitchRow(
                    title = stringResource(R.string.switch_dual_cell),
                    subtitle = stringResource(R.string.switch_dual_cell_sub),
                    checked = dualCellOn,
                    onCheckedChange = onToggleDualCell
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.refresh_interval),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000 to "1 秒", 2000 to "2 秒", 5000 to "5 秒").forEach { (ms, label) ->
                        FilterChip(
                            selected = refreshMs == ms,
                            onClick = { onRefreshMs(ms) },
                            label = { Text(label) },
                            enabled = monitorOn,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.25f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 权限与 ColorOS
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(stringResource(R.string.section_permissions))
                Spacer(Modifier.height(10.dp))
                if (!notifGranted) {
                    WarnText(stringResource(R.string.notif_missing))
                }
                if (monitorOn && capsuleOn && !overlayGranted) {
                    WarnText(stringResource(R.string.overlay_missing))
                }
                if (promotedAvailable && monitorOn && chipOn && !promotedGranted) {
                    WarnText(stringResource(R.string.promoted_disabled))
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.btn_notification),
                        onClick = onRequestNotif,
                        modifier = Modifier.weight(1f),
                        enabled = !notifGranted
                    )
                    GlassButton(
                        text = stringResource(R.string.btn_overlay),
                        onClick = onOpenOverlay,
                        modifier = Modifier.weight(1f),
                        enabled = !overlayGranted
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = stringResource(R.string.btn_autostart),
                        onClick = onOpenAutostart,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.btn_battery_opt),
                        onClick = onOpenBatteryOpt,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (promotedAvailable) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = stringResource(R.string.btn_promoted),
                        onClick = onOpenPromoted,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !promotedGranted
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.coloros_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 数据说明
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(stringResource(R.string.section_about))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        corner = 22.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WarnText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
