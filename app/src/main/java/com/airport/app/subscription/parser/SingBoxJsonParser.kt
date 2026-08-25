package com.airport.app.subscription.parser

import com.airport.app.model.Node
import org.json.JSONObject

/** 直接提供 sing-box JSON 配置的订阅解析器（取 outbounds 中的可代理出站） */
object SingBoxJsonParser {

    private val PROXY_TYPES = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "socks", "http",
    )

    fun parse(content: String): List<Node> {
        val root = try {
            JSONObject(content)
        } catch (_: Exception) {
            return emptyList()
        }
        val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
        return buildList {
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val type = ob.optString("type")
                if (type !in PROXY_TYPES) continue
                val server = ob.optString("server")
                val port = ob.optInt("server_port")
                if (server.isEmpty() || port <= 0) continue
                // 复制一份并固定 tag
                val copy = JSONObject(ob.toString()).put("tag", "proxy")
                add(
                    Node(
                        name = ob.optString("tag", "$server:$port"),
                        protocol = type,
                        server = server,
                        port = port,
                        link = "singbox://$type/$server:$port",
                        outbound = copy.toString(),
                    )
                )
            }
        }
    }
}
