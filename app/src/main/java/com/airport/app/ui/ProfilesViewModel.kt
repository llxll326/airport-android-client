package com.airport.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airport.app.AirportApp
import com.airport.app.core.CoreController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页（节点列表 + 连接控制）ViewModel */
class ProfilesViewModel : ViewModel() {

    private val app = AirportApp.instance
    private val repo = app.subscriptions

    val subscriptions: StateFlow<List<com.airport.app.data.entity.SubscriptionEntity>> =
        repo.subscriptions.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<com.airport.app.data.entity.ProfileEntity>> =
        repo.observeProfiles().stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProfileId: StateFlow<Long> =
        app.settings.selectedProfileId.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0L)

    val isRunning = CoreController.isRunning
    val currentProfileId = CoreController.currentProfileId

    /** 一次性提示消息（如"请先选择节点"） */
    val message = MutableStateFlow<String?>(null)

    fun selectProfile(id: Long) {
        viewModelScope.launch { app.settings.setSelectedProfileId(id) }
    }

    /** 点击节点：选中；若已连接且节点不同则自动切换 */
    fun onProfileClick(profile: com.airport.app.data.entity.ProfileEntity) {
        viewModelScope.launch {
            app.settings.setSelectedProfileId(profile.id)
            if (CoreController.isRunning.value && CoreController.currentProfileId.value != profile.id) {
                CoreController.switchTo(app, profile)
            }
        }
    }

    /** 连接开关：未连接 → 启动；已连接 → 停止 */
    fun toggleConnection() {
        val context = app
        if (CoreController.isRunning.value) {
            CoreController.stop(context)
            return
        }
        viewModelScope.launch {
            val id = selectedProfileId.value
            if (id <= 0) {
                message.value = "请先在列表中选择一个节点"
                return@launch
            }
            val profile = repo.getProfile(id)
            if (profile == null) {
                message.value = "节点不存在，请重新选择"
                return@launch
            }
            CoreController.start(context, profile)
        }
    }
}
