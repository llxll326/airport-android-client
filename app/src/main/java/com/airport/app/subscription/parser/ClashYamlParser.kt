package com.airport.app.subscription.parser

import com.airport.app.model.Node
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

/**
 * Clash / Mihomo YAML 订阅解析器：读取 proxies 列表并转换为 sing-box outbound。
 * 支持 ss / vmess / vless / trojan / hysteria2 / tuic / socks5 / http。
 */
object ClashYamlParser {

    fun parse(content: String): List<Node> {
        val root = try {
            Yaml().load<Map<String, Any?>>(content)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        val proxies = root["proxies"] as? List<*> ?: return emptyList()
        return proxies.mapNotNull { it as? Map<*, *> }.mapNotNull { convert(it) }.toList()
    }

    private fun convert(p: Map<*, *>): Node? {
        val name = (p["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val server = (p["server"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val port = (p["port"] as? Number)?.toInt() ?: return null
        val type = (p["type"] as? String)?.lowercase() ?: return null

        val outbound = try {
            when (type) {
                "ss", "shadowsocks" -> JSONObject()
                    .put("type", "shadowsocks")
                    .put("server", server)
                    .put("server_port", port)
                    .put("method", p["cipher"] as? String ?: return null)
                    .put("password", p["password"] as? String ?: return null)
                "vmess" -> vmessOutbound(p, server, port)
                "vless" -> vlessOutbound(p, server, port)
                "trojan" -> trojanOutbound(p, server, port)
                "hysteria2", "hy2" -> hysteria2Outbound(p, server, port)
                "tuic" -> tuicOutbound(p, server, port)
                "socks5" -> JSONObject()
                    .put("type", "socks")
                    .put("server", server)
                    .put("server_port", port)
                    .put("username", p["username"] ?: "")
                    .put("password", p["password"] ?: "")
                "http" -> JSONObject()
                    .put("type", "http")
                    .put("server", server)
                    .put("server_port", port)
                    .put("username", p["username"] ?: "")
                    .put("password", p["password"] ?: "")
                else -> return null // ssr/wireguard 等暂不支持
            }.put("tag", "proxy")
        } catch (_: Exception) {
            return null
        }
        return Node(
            name = name,
            protocol = type,
            server = server,
            port = port,
            link = "clash://$type/$server:$port",
            outbound = outbound.toString(),
        )
    }

    private fun wsOpts(p: Map<*, *>): Pair<String?, String?> {
        val ws = p["ws-opts"] as? Map<*, *> ?: return null to null
        val path = ws["path"] as? String
        val host = (ws["headers"] as? Map<*, *>)?.get("Host") as? String
        return path to host
    }

    private fun transportOf(p: Map<*, *>): JSONObject? {
        val network = (p["network"] as? String)?.lowercase() ?: return null
        val (path, host) = wsOpts(p)
        val serviceName = (p["grpc-opts"] as? Map<*, *>)?.get("grpc-service-name") as? String
        return when (network) {
            "ws" -> {
                val t = JSONObject().put("type", "ws")
                path?.takeIf { it.isNotBlank() }?.let { t.put("path", it) }
                host?.takeIf { it.isNotBlank() }?.let { t.put("headers", JSONObject().put("Host", it)) }
                t
            }
            "grpc" -> JSONObject().put("type", "grpc").put("service_name", serviceName ?: "")
            "http", "h2" -> {
                val t = JSONObject().put("type", "http")
                host?.takeIf { it.isNotBlank() }?.let { t.put("host", JSONArray().put(it)) }
                path?.takeIf { it.isNotBlank() }?.let { t.put("path", it) }
                t
            }
            else -> null
        }
    }

    private fun tlsOf(
        p: Map<*, *>,
        server: String,
        force: Boolean = false,
        reality: JSONObject? = null,
    ): JSONObject? {
        val tls = p["tls"] as? Boolean ?: force
        val servername = (p["servername"] as? String)?.takeIf { it.isNotBlank() } ?: server
        if (!tls) return null
        val t = JSONObject().put("enabled", true).put("server_name", servername)
        val insecure = p["skip-cert-verify"] as? Boolean ?: false
        if (insecure) t.put("insecure", true)
        val fp = p["client-fingerprint"] as? String
        if (!fp.isNullOrBlank()) t.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
        val alpn = p["alpn"] as? List<*>
        if (!alpn.isNullOrEmpty()) t.put("alpn", JSONArray(alpn))
        if (reality != null) t.put("reality", reality)
        return t
    }

    private fun vmessOutbound(p: Map<*, *>, server: String, port: Int): JSONObject {
        val o = JSONObject()
            .put("type", "vmess")
            .put("server", server)
            .put("server_port", port)
            .put("uuid", p["uuid"] as? String ?: "")
            .put("security", (p["cipher"] as? String)?.takeIf { it != "auto" } ?: "auto")
            .put("alter_id", (p["alterId"] as? Number)?.toInt() ?: 0)
        tlsOf(p, server, force = p["tls"] == true)?.let { o.put("tls", it) }
        transportOf(p)?.let { o.put("transport", it) }
        return o
    }

    private fun vlessOutbound(p: Map<*, *>, server: String, port: Int): JSONObject {
        val o = JSONObject()
            .put("type", "vless")
            .put("server", server)
            .put("server_port", port)
            .put("uuid", p["uuid"] as? String ?: "")
        (p["flow"] as? String)?.takeIf { it.isNotBlank() }?.let { o.put("flow", it) }
        val reality = if ((p["reality-opts"] as? Map<*, *>) != null) {
            val r = JSONObject().put("enabled", true)
            val ro = p["reality-opts"] as Map<*, *>
            (ro["public-key"] as? String)?.let { r.put("public_key", it) }
            (ro["short-id"] as? String)?.let { r.put("short_id", it) }
            r
        } else null
        val isReality = p["tls"] == true && reality != null
        tlsOf(p, server, force = p["tls"] == true, reality = if (isReality) reality else null)?.let { o.put("tls", it) }
        transportOf(p)?.let { o.put("transport", it) }
        return o
    }

    private fun trojanOutbound(p: Map<*, *>, server: String, port: Int): JSONObject {
        val o = JSONObject()
            .put("type", "trojan")
            .put("server", server)
            .put("server_port", port)
            .put("password", p["password"] as? String ?: "")
        tlsOf(p, server, force = true)?.let { o.put("tls", it) }
        transportOf(p)?.let { o.put("transport", it) }
        return o
    }

    private fun hysteria2Outbound(p: Map<*, *>, server: String, port: Int): JSONObject {
        val o = JSONObject()
            .put("type", "hysteria2")
            .put("server", server)
            .put("server_port", port)
            .put("password", p["password"] as? String ?: "")
        (p["obfs"] as? String)?.takeIf { it.isNotBlank() }?.let {
            o.put("obfs", JSONObject().put("type", it)
                .put("password", (p["obfs-password"] as? String) ?: ""))
        }
        tlsOf(p, server, force = true)?.let { o.put("tls", it) }
        return o
    }

    private fun tuicOutbound(p: Map<*, *>, server: String, port: Int): JSONObject {
        val o = JSONObject()
            .put("type", "tuic")
            .put("server", server)
            .put("server_port", port)
            .put("uuid", p["uuid"] as? String ?: "")
            .put("password", p["password"] as? String ?: "")
        (p["congestion-controller"] as? String)?.takeIf { it.isNotBlank() }?.let {
            o.put("congestion_control", it)
        }
        tlsOf(p, server, force = true)?.let { o.put("tls", it) }
        return o
    }
}
