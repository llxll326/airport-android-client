package com.airport.app.core

import android.content.Context
import android.net.VpnService
import com.airport.app.data.entity.ProfileEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 连接控制中心：管理代理隧道的启动/停止与状态。
 * 实际工作由 [TunService]（前台服务 + VpnService + sing-box）执行。
 */
object CoreController {

    private val _isRunning = MutableStateFlow(false)
    private val _currentProfileId = MutableStateFlow<Long?>(null)

    /** 隧道是否运行中（由 TunService 更新） */
    val isRunning: StateFlow<Boolean> = _isRunning

    /** 当前连接使用的节点 ID */
    val currentProfileId: StateFlow<Long?> = _currentProfileId

    /** 需要 VPN 授权（MainActivity 收集后启动授权界面） */
    val vpnAuthRequired = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** 启动/运行错误消息（UI 用 Snackbar 展示后清空） */
    val errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private var pendingProfile: ProfileEntity? = null

    /** 启动代理隧道；若未获得 VPN 授权则先触发授权流程 */
    fun start(context: Context, profile: ProfileEntity) {
        if (VpnService.prepare(context) != null) {
            pendingProfile = profile
            vpnAuthRequired.tryEmit(Unit)
            return
        }
        TunService.start(context, profile)
    }

    /** 停止代理隧道 */
    fun stop(context: Context) {
        TunService.stop(context)
    }

    /** VPN 授权结果回调（由 MainActivity 调用） */
    fun onVpnAuthResult(context: Context, granted: Boolean) {
        if (!granted) {
            pendingProfile = null
            return
        }
        val profile = pendingProfile
        pendingProfile = null
        profile?.let { TunService.start(context, it) }
    }

    /** 切换节点：断开后使用新节点重新连接 */
    fun switchTo(context: Context, profile: ProfileEntity) {
        stop(context)
        start(context, profile)
    }

    /** 由 TunService 更新运行状态 */
    internal fun updateRunningState(running: Boolean, profileId: Long?) {
        _isRunning.value = running
        _currentProfileId.value = profileId
    }

    /** 由 TunService 上报启动/运行错误（供 UI 展示） */
    internal fun reportError(message: String) {
        errorMessage.tryEmit(message)
    }
}
