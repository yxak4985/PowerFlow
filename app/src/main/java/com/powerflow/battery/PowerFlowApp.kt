package com.powerflow.battery

import android.app.Application
import android.util.Log
import com.powerflow.battery.util.Prefs
import java.io.File
import java.util.Date

class PowerFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        installCrashLogger()
    }

    /**
     * 崩溃时把堆栈写到应用文件目录，便于排查（路径：
     * Android/data/com.powerflow.battery/files/crash.txt）。
     */
    private fun installCrashLogger() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(getExternalFilesDir(null), "crash.txt")
                if (file.length() > 512 * 1024) file.delete()
                file.appendText(
                    "${Date()}\n${thread.name}\n${Log.getStackTraceString(throwable)}\n---\n"
                )
            } catch (_: Exception) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
