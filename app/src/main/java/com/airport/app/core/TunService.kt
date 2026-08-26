package com.airport.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.airport.app.MainActivity
import com.airport.app.R
import libbox.libbox.BoxService
import libbox.libbox.ConnectionOwner
import libbox.libbox.InterfaceUpdateListener
import libbox.libbox.Libbox
import libbox.libbox.LocalDNSTransport
import libbox.libbox.NetworkInterfaceIterator
import libbox.libbox.Notification as LibboxNotification
import libbox.libbox.PlatformInterface
import libbox.libbox.ServiceHandler
import libbox.libbox.SetupOptions
import libbox.libbox.StringIterator
import libbox.libbox.SystemProxyStatus
import libbox.libbox.TunOptions
import libbox.libbox.WIFIState
import java.io.File
import java.net.InetSocketAddress

/**
 * TUN 隧道前台服务：承载 sing-box 核心。
 *
 * 同时实现 [PlatformInterface]（TUN 建立、socket 保护、网络信息上报）
 * 与 [ServiceHandler]（系统代理等回调，MVP 为空实现）。
 */
class TunService : VpnService(), PlatformInterface, ServiceHandler {

    companion object {
        private const val TAG = "TunService"
        private const val CHANNEL_ID = "tun"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.airport.app.action.START"
        private const val ACTION_STOP = "com.airport.app.action.STOP"
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_PROFILE_NAME = "profile_name"

        /** 启动隧道（配置由 profile 生成） */
        fun start(context: Context, profile: com.airport.app.data.entity.ProfileEntity) {
            val intent = Intent(context, TunService::class.java).apply {
                action = ACTION_START
                putExtra(
                    EXTRA_CONFIG,
                    ConfigBuilder.build(
                        profile.outbound,
                        logPath = File(context.filesDir, "sing-box.log").absolutePath,
                    ),
                )
                putExtra(EXTRA_PROFILE_ID, profile.id)
                putExtra(EXTRA_PROFILE_NAME, profile.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 请求停止隧道（仅在运行中有效，避免后台启动服务限制） */
        fun stop(context: Context) {
            if (CoreController.isRunning.value) {
                ContextCompat.startForegroundService(context, Intent(context, TunService::class.java).apply {
                    action = ACTION_STOP
                })
            }
        }
    }

    private var boxService: BoxService? = null
    private var configContent: String? = null
    private var profileId: Long = 0
    private var profileName: String = ""
    private var tunnelFd: ParcelFileDescriptor? = null
    private val defaultNetworkMonitor = DefaultNetworkMonitor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                // 不使用 START_STICKY：崩溃后由系统重启会导致无限崩溃循环
                return START_NOT_STICKY
            }
            ACTION_START -> {
                configContent = intent.getStringExtra(EXTRA_CONFIG)
                profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0)
                profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "未知节点"
                startForeground(NOTIFICATION_ID, buildNotification("正在连接 $profileName…"))
                if (initialize()) {
                    CoreController.updateRunningState(true, profileId)
                    updateNotification("已连接 · $profileName")
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun initialize(): Boolean {
        return try {
            val setupOptions = SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = filesDir.absolutePath
                tempPath = cacheDir.absolutePath
                logMaxLines = 1000L
                // Android 信号上下文执行 Go 代码会导致 native 崩溃（go issue 68760），
                // SFA 在 debug 构建同样总是启用此 workaround
                fixAndroidStack = true
            }
            Libbox.setup(setupOptions)
            val box = Libbox.newService(this, this)
            boxService = box
            box.start()
            box.startService(configContent!!)
            true
        } catch (e: Exception) {
            Log.e(TAG, "start sing-box failed", e)
            val message = e.message ?: "未知错误"
            updateNotification("连接失败：$message")
            CoreController.reportError("连接失败：$message")
            false
        }
    }

    override fun onDestroy() {
        try {
            boxService?.stopService()
        } catch (_: Exception) {
        }
        try {
            boxService?.close()
        } catch (_: Exception) {
        }
        boxService = null
        try {
            tunnelFd?.close()
        } catch (_: Exception) {
        }
        tunnelFd = null
        CoreController.updateRunningState(false, null)
        super.onDestroy()
    }

    // ---------- 通知 ----------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "代理隧道", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TunService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${getString(R.string.app_name)} · 代理隧道")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "断开", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ---------- libbox.PlatformInterface ----------

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    /** 由 sing-box 调用：使用配置建立 Android VpnService TUN，返回 fd */
    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(options.mtu)

        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val prefix = inet4.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }
        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val prefix = inet6.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        if (options.autoRoute) {
            options.dnsServerAddress?.let { builder.addDnsServer(it.value) }
            val v4Routes = options.inet4RouteRange
            if (v4Routes.hasNext()) {
                while (v4Routes.hasNext()) {
                    val prefix = v4Routes.next()
                    builder.addRoute(prefix.address(), prefix.prefix())
                }
            } else if (inet4.hasNext()) {
                builder.addRoute("0.0.0.0", 0)
            }
            val v6Routes = options.inet6RouteRange
            if (v6Routes.hasNext()) {
                while (v6Routes.hasNext()) {
                    val prefix = v6Routes.next()
                    builder.addRoute(prefix.address(), prefix.prefix())
                }
            } else if (inet6.hasNext()) {
                builder.addRoute("::", 0)
            }
        }

        val fd = builder.establish() ?: error("android: vpn establish failed")
        tunnelFd = fd
        return fd.fd
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cm = getSystemService(ConnectivityManager::class.java)
            val uid = cm.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
            if (uid == Process.INVALID_UID) error("android: connection owner not found")
            return ConnectionOwner().apply { userId = uid }
        }
        error("android: find connection owner not supported")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultNetworkMonitor.setListener(this, listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultNetworkMonitor.setListener(this, null)
    }

    /**
     * 上报当前网络接口列表（供 sing-box 选择出站接口）。
     * 直接枚举 java.net.NetworkInterface（不依赖 ConnectivityManager.allNetworks，
     * 避免 TUN 建立瞬间物理网络信息缺失导致接口列表为空/不全）。
     */
    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = getSystemService(ConnectivityManager::class.java)
        val interfaces = mutableListOf<libbox.libbox.NetworkInterface>()
        runCatching {
            val javaInterfaces = java.net.NetworkInterface.getNetworkInterfaces().toList()
            for (javaInterface in javaInterfaces) {
                val name = javaInterface.name ?: continue
                // 跳过回环与虚拟接口，避免干扰出站选择
                if (javaInterface.isLoopback) continue
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("dummy")) continue
                val boxInterface = libbox.libbox.NetworkInterface().apply {
                    this.name = name
                    index = javaInterface.index
                    runCatching { mtu = javaInterface.mtu }
                    addresses = StringArray(
                        javaInterface.interfaceAddresses.map { addr ->
                            // IPv6 link-local 的 hostAddress 带 %scope（如 fe80::1%wlan0），
                            // 会令 Go 侧 netip.MustParsePrefix panic，必须剥离
                            val host = addr.address.hostAddress?.stripIpv6Scope()
                            "$host/${addr.networkPrefixLength}"
                        },
                    )
                    type = Libbox.InterfaceTypeOther
                    var dumpFlags = 0
                    if (javaInterface.isUp) dumpFlags = dumpFlags or OsConstants.IFF_UP
                    if (javaInterface.isLoopback) dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
                    if (javaInterface.isPointToPoint) dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
                    if (javaInterface.supportsMulticast()) dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
                    flags = dumpFlags
                }
                interfaces.add(boxInterface)
            }
        }
        // 补充默认网络的 DNS 与类型信息（尽力而为，不影响列表完整性）
        runCatching {
            val defaultNetwork = cm.activeNetwork
            val lp = defaultNetwork?.let { cm.getLinkProperties(it) }
            val caps = defaultNetwork?.let { cm.getNetworkCapabilities(it) }
            if (lp != null && caps != null) {
                val boxInterface = interfaces.firstOrNull { it.name == lp.interfaceName }
                if (boxInterface != null) {
                    boxInterface.dnsServer = StringArray(
                        lp.dnsServers.mapNotNull { it.hostAddress?.stripIpv6Scope() },
                    )
                    boxInterface.type = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                        else -> Libbox.InterfaceTypeOther
                    }
                    boxInterface.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }
        }
        return InterfaceArray(interfaces)
    }

    /** 剥离 IPv6 scope（%wlan0 等），避免 Go 侧解析 panic */
    private fun String.stripIpv6Scope(): String {
        val idx = indexOf('%')
        return if (idx > 0) substring(0, idx) else this
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun systemCertificates(): StringIterator? = null

    override fun clearDNSCache() {
    }

    override fun sendNotification(notification: LibboxNotification) {
    }

    // ---------- libbox.ServiceHandler（MVP 空实现） ----------

    override fun serviceStop() {
    }

    override fun serviceReload() {
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? =
        SystemProxyStatus().apply {
            available = false
            enabled = false
        }

    override fun setSystemProxyEnabled(enabled: Boolean) {
    }

    override fun writeDebugMessage(message: String) {
        Log.d(TAG, message)
    }
}

/** 默认网络监控：监听系统默认网络变化并上报给 sing-box */
private class DefaultNetworkMonitor {

    private var listener: InterfaceUpdateListener? = null
    private var cm: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            report(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            report(network)
        }

        override fun onLost(network: Network) {
            listener?.updateDefaultInterface("", -1, false, false)
        }
    }

    fun setListener(context: Context, listener: InterfaceUpdateListener?) {
        this.listener = listener
        if (listener != null) {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            cm = manager
            manager.registerDefaultNetworkCallback(callback)
            report(manager.activeNetwork)
        } else {
            cm?.unregisterNetworkCallback(callback)
            cm = null
        }
    }

    /**
     * 上报默认接口。接口信息在 TUN 刚建立时可能尚未就绪，
     * 参照 SFA 做法：获取失败时最多重试 10 次（每次 100ms）。
     */
    private fun report(network: Network?) {
        val manager = cm ?: return
        if (network == null) return
        for (attempt in 0 until 10) {
            val lp = manager.getLinkProperties(network)
            val name = lp?.interfaceName
            val index = if (!name.isNullOrEmpty()) {
                runCatching { java.net.NetworkInterface.getByName(name)?.index ?: 0 }
                    .getOrDefault(0)
            } else 0
            if (index > 0) {
                val caps = manager.getNetworkCapabilities(network)
                listener?.updateDefaultInterface(
                    name!!,
                    index,
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
                    false,
                )
                return
            }
            Thread.sleep(100)
        }
    }
}

/** StringIterator 简易实现 */
class StringArray(private val values: List<String>) : StringIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < values.size
    override fun len(): Int = values.size
    override fun next(): String = values[index++]
}

/** NetworkInterfaceIterator 简易实现 */
class InterfaceArray(private val values: List<libbox.libbox.NetworkInterface>) : NetworkInterfaceIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < values.size
    override fun next(): libbox.libbox.NetworkInterface = values[index++]
}
