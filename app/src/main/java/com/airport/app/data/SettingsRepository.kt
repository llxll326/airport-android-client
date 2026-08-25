package com.airport.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 应用设置（DataStore 实现） */
class SettingsRepository(private val context: Context) {

    private val selectedProfileIdKey = longPreferencesKey("selected_profile_id")
    private val welcomeShownKey = booleanPreferencesKey("welcome_shown")

    /** 选中的节点 ID（0 表示未选择） */
    val selectedProfileId: Flow<Long> =
        context.dataStore.data.map { it[selectedProfileIdKey] ?: 0L }

    /** 首次启动欢迎弹窗是否已展示 */
    val welcomeShown: Flow<Boolean> =
        context.dataStore.data.map { it[welcomeShownKey] ?: false }

    suspend fun setSelectedProfileId(id: Long) {
        context.dataStore.edit { it[selectedProfileIdKey] = id }
    }

    suspend fun setWelcomeShown() {
        context.dataStore.edit { it[welcomeShownKey] = true }
    }
}
