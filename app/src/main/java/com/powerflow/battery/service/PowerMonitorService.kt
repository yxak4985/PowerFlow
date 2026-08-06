package com.powerflow.battery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.powerflow.battery.MainActivity
import com.powerflow.battery.R
import com.powerflow.battery.battery.HistoryStore
import com.powerflow.battery.battery.HealthStore
import com.powerflow.battery.battery.PowerStore
import com.powerflow.battery.util.Format
import com.powerflow.battery.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 核心服务：每秒读取电池功率。
 * 1. 前台通知（Android 16+ 提升为 Live Updates 实时活动，适配 ColorOS 流体云 / 锁屏岛；
 *    状态栏胶囊文本通过 setShortCriticalText 显示）；
 * 2. 自绘悬浮胶囊（液态玻璃风格，可拖动，可选锁屏显示）。
 *
 * 注意：悬浮胶囊必须使用原生 View 而不是 ComposeView——服务窗口没有 ViewTreeLifecycleOwner，
 * Compose 1.7+ 在窗口挂载时会抛 IllegalStateException 导致崩溃（已实测踩坑）。
 */
class PowerMonitorService : Service() {

    companion object {
        const val ACTION_STOP = "com.powerflow.battery.action.STOP"
        const val ACTION_UPDATE_CAPSULE = "com.powerflow.battery.action.UPDATE_CAPSULE"
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "power_monitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null
    private var screenOn = true
    private lateinit var notificationManager: NotificationManager
    private var lastNotifKey = ""

    private var windowManager: WindowManager? = null
    private var capsuleView: LinearLayout? = null
    private var capsuleIcon: ImageView? = null
    private var capsuleText: TextView? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    private var capsuleJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    restartPolling()
                    syncCapsuleWindow()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    restartPolling()
                    syncCapsuleWindow()
                }
                Intent.ACTION_BATTERY_CHANGED -> refreshNow()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
        syncCapsuleWindow()
        restartPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        removeCapsule()
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = getString(R.string.about_note)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun restartPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                refreshNow()
                delay(if (screenOn) Prefs.refreshMs.toLong() else 5000L)
            }
        }
    }

    private fun refreshNow() {
        runCatching { PowerStore.refresh(this) }
        runCatching { HealthStore.refresh() }
        val s = PowerStore.flow.value
        if (s.available) {
            HistoryStore.add(HistoryStore.Point(s.timestamp, s.powerW, s.currentA, s.voltageMv))
        }
        updateNotification()
    }

    private fun updateNotification() {
        val s = PowerStore.flow.value
        val key = "${s.level}|${s.charging}|${s.full}|${s.available}|${s.estimated}|" +
            "${Format.powerWithUnit(s.powerW)}|${s.source}"
        if (key == lastNotifKey) return
        lastNotifKey = key
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val snapshot = PowerStore.flow.value
        val statusText = when {
            snapshot.full -> getString(R.string.hero_full)
            snapshot.charging -> getString(R.string.hero_charging)
            else -> getString(R.string.hero_discharging)
        }
        val body = if (snapshot.available) {
            getString(
                R.string.notif_body,
                statusText,
                Format.powerWithUnit(snapshot.powerW),
                snapshot.level,
                Format.voltage(snapshot.voltageMv),
                Format.current(snapshot.currentA)
            )
        } else {
            getString(R.string.notif_reading)
        }
        val chip = if (snapshot.available) Format.powerWithUnit(snapshot.powerW) else "…"

        val contentPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, PowerMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.action_stop),
                    stopPi
                ).build()
            )

        if (Build.VERSION.SDK_INT >= 36) {
            val promote = Prefs.statusBarChip
            builder.setRequestPromotedOngoing(promote)
            if (promote) builder.setShortCriticalText(chip)
            val style = Notification.ProgressStyle().apply {
                setProgress(snapshot.level.coerceIn(0, 100))
                setStyledByProgress(true)
                setProgressTrackerIcon(Icon.createWithResource(this@PowerMonitorService, R.drawable.ic_power))
            }
            builder.setStyle(style)
        } else {
            builder.setStyle(Notification.BigTextStyle().bigText(body))
        }
        return builder.build()
    }

    // ---------- 悬浮胶囊（原生 View，避免服务窗口无 LifecycleOwner 崩溃） ----------

    private fun syncCapsuleWindow() {
        // 悬浮胶囊与锁屏胶囊互不绑定：任一开启都需要胶囊窗口（平时 / 锁屏按需显示）
        if (!Prefs.capsuleEnabled && !Prefs.capsuleLockScreen) {
            removeCapsule()
            return
        }
        if (capsuleView != null) {
            updateCapsuleVisibility()
            return
        }
        val wm = runCatching { getSystemService(WINDOW_SERVICE) as WindowManager }.getOrNull() ?: return
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + (4 * density).roundToInt()
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching { blurBehindRadius = 28 }
            }
        }

        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val textColor = if (dark) Color.WHITE else Color.rgb(16, 35, 61)
        val pillColor = if (dark) Color.argb(230, 16, 32, 56) else Color.argb(232, 255, 255, 255)

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_charge)
            imageTintList = ColorStateList.valueOf(Color.rgb(22, 199, 154))
            layoutParams = LinearLayout.LayoutParams(
                (18 * density).roundToInt(),
                (18 * density).roundToInt()
            )
        }
        val text = TextView(this).apply {
            this.text = "…"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6 * density).roundToInt()
            }
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14 * density).roundToInt(),
                (9 * density).roundToInt(),
                (14 * density).roundToInt(),
                (9 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 30 * density
                setColor(pillColor)
                setStroke((1 * density).roundToInt(), Color.argb(71, 255, 255, 255))
            }
            addView(icon)
            addView(text)
            isClickable = true
            setOnClickListener { openApp() }
        }

        // 拖动
        val startRawX = floatArrayOf(0f)
        val startRawY = floatArrayOf(0f)
        val startX = intArrayOf(0)
        val startY = intArrayOf(0)
        pill.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX[0] = event.rawX
                    startRawY[0] = event.rawY
                    startX[0] = params.x
                    startY[0] = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX[0] + (event.rawX - startRawX[0]).roundToInt()
                    params.y = startY[0] + (event.rawY - startRawY[0]).roundToInt()
                    runCatching { wm.updateViewLayout(v, params) }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(pill, params) }.onFailure { return }
        windowManager = wm
        capsuleView = pill
        capsuleIcon = icon
        capsuleText = text
        capsuleParams = params
        updateCapsuleVisibility()
        startCapsuleUpdates()
    }

    /** 平时只显示悬浮胶囊，锁屏只显示锁屏胶囊，两者互不绑定。 */
    private fun updateCapsuleVisibility() {
        val view = capsuleView ?: return
        val visible = (Prefs.capsuleEnabled && screenOn) || (Prefs.capsuleLockScreen && !screenOn)
        runCatching { view.visibility = if (visible) View.VISIBLE else View.GONE }
        updateCapsuleFlags()
    }

    private fun startCapsuleUpdates() {
        capsuleJob?.cancel()
        capsuleJob = mainScope.launch {
            PowerStore.flow.collect { s ->
                val text = capsuleText ?: return@collect
                val icon = capsuleIcon ?: return@collect
                text.text = if (s.available) Format.powerWithUnit(s.powerW) else "…"
                val charging = s.charging || s.full
                icon.setImageResource(if (charging) R.drawable.ic_charge else R.drawable.ic_discharge)
                icon.imageTintList = ColorStateList.valueOf(
                    if (charging) Color.rgb(22, 199, 154) else Color.rgb(255, 169, 64)
                )
            }
        }
    }

    private fun updateCapsuleFlags() {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        val wm = windowManager ?: return
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            if (Prefs.capsuleLockScreen) WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun removeCapsule() {
        capsuleJob?.cancel()
        capsuleJob = null
        val view = capsuleView ?: return
        val wm = windowManager ?: return
        runCatching { wm.removeView(view) }
        capsuleView = null
        capsuleIcon = null
        capsuleText = null
        capsuleParams = null
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            resources.getDimensionPixelSize(id)
        } else {
            (28 * resources.displayMetrics.density).roundToInt()
        }
    }

    private fun openApp() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun stopEverything() {
        pollJob?.cancel()
        removeCapsule()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
