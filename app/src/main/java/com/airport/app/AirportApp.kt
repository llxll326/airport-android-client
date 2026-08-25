package com.airport.app

import android.app.Application
import com.airport.app.data.AppDatabase
import com.airport.app.data.SettingsRepository
import com.airport.app.subscription.SubscriptionRepository

class AirportApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val subscriptions: SubscriptionRepository by lazy { SubscriptionRepository(this) }

    companion object {
        /** 供无 Context 的地方（如部分单例）便捷获取 */
        lateinit var instance: AirportApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
