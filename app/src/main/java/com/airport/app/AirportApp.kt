package com.airport.app

import android.app.Application
import android.util.Log
import com.airport.app.data.AppDatabase
import com.airport.app.data.SettingsRepository
import com.airport.app.subscription.SubscriptionRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AirportApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val subscriptions: SubscriptionRepository by lazy { SubscriptionRepository(this) }

    companion object {
        /** 供无 Context 的地方（如部分单例）便捷获取 */
        lateinit var instance: AirportApp
            private set

        /** Java 层崩溃日志文件（用于启动时展示，辅助排查） */
        const val CRASH_LOG_FILE = "crash.log"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
    }

    /** 捕获未处理异常写入文件，供下次启动展示 */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val content = "线程: ${thread.name}\n时间: ${System.currentTimeMillis()}\n${sw}"
                File(filesDir, CRASH_LOG_FILE).writeText(content)
                Log.e("AirportApp", "uncaught exception", throwable)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 读取并清除崩溃日志（返回 null 表示没有） */
    fun consumeCrashLog(): String? {
        val file = File(filesDir, CRASH_LOG_FILE)
        if (!file.exists()) return null
        val content = file.readText()
        file.delete()
        return content
    }
}
