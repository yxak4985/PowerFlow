package com.powerflow.battery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.powerflow.battery.service.PowerMonitorService
import com.powerflow.battery.ui.MainScreen
import com.powerflow.battery.ui.theme.PowerFlowTheme
import com.powerflow.battery.util.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 之前开启过监控的话，重新打开应用时自动恢复前台服务
        if (Prefs.monitorEnabled) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, PowerMonitorService::class.java))
            }
        }
        setContent {
            PowerFlowTheme {
                MainScreen()
            }
        }
    }
}
