package com.airport.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * sing-box 配置生成器：将单个代理出站装配为完整的 TUN 模式配置。
 *
 * 生成结构：
 * - dns：DoH 服务器（经代理出站 detour，避免 DNS 污染）
 * - inbounds：tun（auto_route + strict_route，gvisor 协议栈）
 * - outbounds：proxy / direct / block
 * - route：私网直连 + DNS 走 dns-out，其余全部走代理
 */
object ConfigBuilder {

    /**
     * 生成完整 sing-box 配置。
     * @param proxyOutboundJson 代理节点 outbound JSON
     * @param logPath 日志文件路径（null 则仅输出到内存）
     */
    fun build(proxyOutboundJson: String, logPath: String? = null): String {
        // 确保出站 tag 固定为 proxy
        val proxy = JSONObject(proxyOutboundJson).put("tag", "proxy")

        val log = JSONObject().put("level", "info").put("timestamp", true)
        if (!logPath.isNullOrBlank()) log.put("output", logPath)

        val config = JSONObject()
            .put("log", log)
            .put("dns", buildDns())
            .put("inbounds", JSONArray().put(buildTunInbound()))
            .put("outbounds", JSONArray()
                .put(proxy)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
                .put(JSONObject().put("type", "dns").put("tag", "dns-out")))
            .put("route", buildRoute())
        return config.toString()
    }

    private fun buildDns(): JSONObject {
        // remote：DoH 走代理（防污染）；local：国内 DNS 直连兜底（DoH 不可用时保证可解析）
        val servers = JSONArray()
            .put(JSONObject()
                .put("tag", "dns-remote")
                .put("address", "https://1.1.1.1/dns-query")
                .put("detour", "proxy"))
            .put(JSONObject()
                .put("tag", "dns-local")
                .put("address", "223.5.5.5")
                .put("detour", "direct"))
        return JSONObject()
            .put("servers", servers)
            .put("final", "dns-remote")
            .put("strategy", "ipv4_only")
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
            .put("auto_detect_interface", true)
    }
}
