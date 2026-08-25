package com.airport.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airport.app.AirportApp
import com.airport.app.data.dao.SubscriptionCount
import com.airport.app.data.entity.SubscriptionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 订阅管理 ViewModel */
class SubscriptionsViewModel : ViewModel() {

    private val app = AirportApp.instance
    private val repo = app.subscriptions

    val subscriptions: StateFlow<List<SubscriptionEntity>> =
        repo.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counts: StateFlow<List<SubscriptionCount>> =
        app.database.profileDao().observeCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 操作结果提示（Snackbar 用），null 表示无提示 */
    val message = MutableStateFlow<String?>(null)

    /** 是否正在处理（添加/刷新） */
    val busy = MutableStateFlow(false)

    fun addSubscription(url: String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                val error = repo.addSubscription(url.trim())
                message.value = error ?: "订阅添加成功"
            } finally {
                busy.value = false
            }
        }
    }

    fun refresh(subscription: SubscriptionEntity) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                val error = repo.refreshSubscription(subscription)
                message.value = error ?: "订阅已更新"
            } finally {
                busy.value = false
            }
        }
    }

    fun delete(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            repo.deleteSubscription(subscription)
            message.value = "已删除订阅"
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
