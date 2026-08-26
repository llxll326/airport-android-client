package com.airport.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * sing-box 配置生成器：将单个代理出站装配为完整的 TUN 模式配置。
 *
 * 生成结构：
 * - log：日志级别 + 可选文件输出
 * - dns：DoH（经代理 detour，防污染）+ 本地 DNS 直连兜底；
 *   代理服务器域名强制走本地 DNS，避免 detour 解析循环（DNS query loopback）
 * - inbounds：tun（auto_route + strict_route，gvisor 协议栈）
 * - outbounds：proxy / direct / block
 * - route：sniff 嗅探 → hijack-dns 劫持 DNS 查询到 dns 模块 → 私网直连，其余走代理
 */
object ConfigBuilder {

    /**
     * 生成完整 sing-box 配置。
     * @param proxyOutboundJson 代理节点 outbound JSON
     * @param logPath 日志文件路径（null 则仅输出到内存）
     */
    fun build(proxyOutboundJson: String, logPath: String? = null): String {
        // 确保出站 tag 固定为 proxy，并提取服务器地址（DNS 防循环用）
        val proxy = JSONObject(proxyOutboundJson).put("tag", "proxy")
        val proxyServer = proxy.optString("server", "")

        val log = JSONObject().put("level", "info").put("timestamp", true)
        if (!logPath.isNullOrBlank()) log.put("output", logPath)

        val config = JSONObject()
            .put("log", log)
            .put("dns", buildDns(proxyServer))
            .put("inbounds", JSONArray().put(buildTunInbound()))
            .put("outbounds", JSONArray()
                .put(proxy)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block")))
            .put("route", buildRoute())
        return config.toString()
    }

    private fun buildDns(proxyServer: String): JSONObject {
        // remote：DoH 走代理（防污染）；local：国内 DNS 直连兜底
        val servers = JSONArray()
            .put(JSONObject()
                .put("tag", "dns-remote")
                .put("address", "https://1.1.1.1/dns-query")
                .put("detour", "proxy"))
            .put(JSONObject()
                .put("tag", "dns-local")
                .put("address", "223.5.5.5")
                .put("detour", "direct"))

        val rules = JSONArray()
        // 代理服务器域名走本地 DNS，防止 DoH(detour proxy) 解析代理服务器时循环
        val rootDomain = rootDomainOf(proxyServer)
        if (rootDomain != null) {
            rules.put(JSONObject()
                .put("domain_suffix", JSONArray().put("." + rootDomain))
                .put("server", "dns-local"))
        }

        return JSONObject()
            .put("servers", servers)
            .put("rules", rules)
            .put("final", "dns-remote")
            .put("strategy", "ipv4_only")
    }

    /** 从服务器地址提取根域（eTLD+1 的最后两段）；IP 地址返回 null */
    private fun rootDomainOf(server: String): String? {
        val s = server.trim()
        if (s.isEmpty() || s.isIpAddress()) return null
        val parts = s.split('.')
        if (parts.size < 2) return null
        return parts.takeLast(2).joinToString(".")
    }

    private fun String.isIpAddress(): Boolean {
        // 简单 IPv4/IPv6 判断（非纯数字分段视为域名）
        if (contains(':')) return true // IPv6
        return split('.').all { it.isNotEmpty() && it.all { c -> c.isDigit() } }
    }

    private fun buildTunInbound(): JSONObject = JSONObject()
        .put("type", "tun")
        .put("tag", "tun-in")
        .put("interface_name", "tun0")
        .put("mtu", 1500)
        .put("address", JSONArray().put("172.19.0.1/30"))
        .put("auto_route", true)
        .put("strict_route", true)
        .put("stack", "gvisor")

    private fun buildRoute(): JSONObject {
        // 私网/保留地址直连，避免绕代理
        val privateCidr = JSONArray()
            .put("10.0.0.0/8")
            .put("172.16.0.0/12")
            .put("192.168.0.0/16")
            .put("100.64.0.0/10")
            .put("127.0.0.0/8")
        return JSONObject()
            .put("rules", JSONArray()
                // 嗅探协议（sing-box 1.13 起需用 action 启用，protocol 条件依赖它）
                .put(JSONObject().put("action", "sniff"))
                // DNS 查询劫持到 dns 模块（1.13 移除 dns-out，改用 hijack-dns action）
                .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
                .put(JSONObject().put("ip_cidr", privateCidr).put("outbound", "direct")))
            .put("final", "proxy")
            // 不使用 auto_detect_interface：手机单默认网络场景下，
            // 接口绑定依赖 GetInterfaces/默认接口 index 完全匹配，任一缺失即报
            // "no available network interface"；走系统默认路由 + socket protect 更可靠
    }
}
