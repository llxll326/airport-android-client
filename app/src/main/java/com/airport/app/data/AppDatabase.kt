package com.airport.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.airport.app.data.dao.ProfileDao
import com.airport.app.data.dao.SubscriptionDao
import com.airport.app.data.entity.ProfileEntity
import com.airport.app.data.entity.SubscriptionEntity

@Database(
    entities = [SubscriptionEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun profileDao(): ProfileDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "airport.db").build()
    }
}
