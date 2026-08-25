package com.airport.app.subscription.parser

import com.airport.app.model.Node
import org.json.JSONArray
import org.json.JSONObject

/**
 * 分享链接解析器：将 vmess:// vless:// trojan:// ss:// hysteria2:// tuic://
 * 转换为统一的 [Node]（内含 sing-box outbound JSON）。
 *
 * 不支持的协议（ssr/wireguard 等）返回 null，由调用方跳过。
 */
object ShareLinkParser {

    /** 出站 tag 固定为 proxy，便于 ConfigBuilder 直接装配 */
    private const val TAG = "proxy"

    fun parse(link: String): Node? {
        val trimmed = link.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val rest = trimmed.substring(schemeEnd + 3)
        return try {
            when (scheme) {
                "vless" -> parseVless(rest)
                "vmess" -> parseVmess(rest)
                "trojan" -> parseTrojan(rest)
                "ss" -> parseShadowsocks(rest)
                "hysteria2", "hy2" -> parseHysteria2(rest)
                "tuic" -> parseTuic(rest)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- 链接通用拆解 ----------

    /** 拆出 userinfo、host:port、query、fragment */
    private data class Parts(
        val userinfo: String?,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val name: String?,
    )

    private fun splitParts(rest: String): Parts {
        val fragment = rest.substringAfter('#', "")
        val noFragment = rest.substringBefore('#')
        val queryPart = noFragment.substringAfter('?', "")
        val noQuery = noFragment.substringBefore('?')
        val atIndex = noQuery.lastIndexOf('@')
        val userinfo = if (atIndex >= 0) UriDecoder.percentDecode(noQuery.substring(0, atIndex)) else null
        val hostPort = if (atIndex >= 0) noQuery.substring(atIndex + 1) else noQuery
        val (host, port) = parseHostPort(hostPort)
        val query = if (queryPart.isEmpty()) emptyMap() else
            queryPart.split('&').mapNotNull { kv ->
                val idx = kv.indexOf('=')
                if (idx < 0) null else kv.substring(0, idx) to UriDecoder.percentDecode(kv.substring(idx + 1))
            }.toMap()
        return Parts(
            userinfo = userinfo,
            host = host,
            port = port,
            query = query,
            name = fragment.ifEmpty { null }?.let { UriDecoder.percentDecode(it) },
        )
    }

    private fun parseHostPort(hostPort: String): Pair<String, Int> {
        val s = hostPort.trim()
        if (s.startsWith("[")) {
            // IPv6：[addr]:port
            val end = s.indexOf(']')
            if (end > 0) {
                val host = s.substring(1, end)
                val rest = s.substring(end + 1).removePrefix(":")
                return host to (rest.toIntOrNull() ?: 0)
            }
        }
        val idx = s.lastIndexOf(':')
        if (idx < 0) return s to 0
        return s.substring(0, idx) to (s.substring(idx + 1).toIntOrNull() ?: 0)
    }

    /** 节点显示名：fragment 优先，缺省 host:port */
    private fun displayName(parts: Parts): String =
        parts.name?.ifBlank { null } ?: "${parts.host}:${parts.port}"

    private fun makeNode(parts: Parts, protocol: String, outbound: JSONObject): Node =
        Node(
            name = displayName(parts),
            protocol = protocol,
            server = parts.host,
            port = parts.port,
            link = originalLink(protocol, parts),
            outbound = outbound.toString(),
        )

    private fun originalLink(scheme: String, parts: Parts): String =
        "$scheme://${parts.userinfo?.let { it + "@" } ?: ""}${parts.host}:${parts.port}"

    // ---------- 传输层（transport）构造 ----------

    private fun buildTransport(
        type: String?,
        path: String?,
        host: String?,
        serviceName: String?,
    ): JSONObject? = when (type?.lowercase()) {
        null, "", "tcp", "none" -> null
        "ws" -> {
            val t = JSONObject().put("type", "ws")
            if (!path.isNullOrEmpty()) t.put("path", path)
            if (!host.isNullOrEmpty()) t.put("headers", JSONObject().put("Host", host))
            t
        }
        "grpc" -> JSONObject().put("type", "grpc")
            .put("service_name", serviceName?.takeIf { it.isNotBlank() } ?: host ?: "")
        "http", "h2" -> {
            val t = JSONObject().put("type", "http")
            if (!host.isNullOrEmpty()) t.put("host", JSONArray().put(host))
            if (!path.isNullOrEmpty()) t.put("path", path)
            t
        }
        else -> null
    }

    private fun buildAlpn(alpn: String?): JSONArray? {
        val list = alpn?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        return if (list.isEmpty()) null else JSONArray(list)
    }

    private fun buildTls(
        enabled: Boolean,
        serverName: String?,
        alpn: String?,
        fingerprint: String?,
        insecure: Boolean = false,
        reality: JSONObject? = null,
    ): JSONObject? {
        if (!enabled) return null
        val tls = JSONObject().put("enabled", true)
        if (!insecure) {
            tls.put("server_name", serverName ?: "")
        } else {
            tls.put("server_name", serverName ?: "")
            tls.put("insecure", true)
        }
        buildAlpn(alpn)?.let { tls.put("alpn", it) }
        if (!fingerprint.isNullOrBlank()) {
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fingerprint))
        }
        if (reality != null) tls.put("reality", reality)
        return tls
    }

    // ---------- 各协议解析 ----------

    private fun parseVless(rest: String): Node? {
        val parts = splitParts(rest)
        if (parts.port <= 0) return null
        val uuid = parts.userinfo ?: return null
        val q = parts.query
        val security = q["security"] ?: "none"
        val isReality = security == "reality"

        val outbound = JSONObject()
            .put("type", "vless")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("uuid", uuid)
        q["flow"]?.takeIf { it.isNotBlank() }?.let { outbound.put("flow", it) }

        val tlsEnabled = security == "tls" || isReality
        if (tlsEnabled) {
            val reality = if (isReality) {
                val r = JSONObject().put("enabled", true)
                q["pbk"]?.takeIf { it.isNotBlank() }?.let { r.put("public_key", it) }
                q["sid"]?.takeIf { it.isNotBlank() }?.let { r.put("short_id", it) }
                r
            } else null
            buildTls(
                enabled = true,
                serverName = q["sni"] ?: parts.host,
                alpn = q["alpn"],
                fingerprint = q["fp"],
                reality = reality,
            )?.let { outbound.put("tls", it) }
        }
        buildTransport(q["type"], q["path"], q["host"], q["serviceName"])?.let {
            outbound.put("transport", it)
        }
        return makeNode(parts, "vless", outbound)
    }

    private fun parseVmess(rest: String): Node? {
        // 两种形式：base64 JSON 或 query 形式（uuid@host:port?query）
        val queryForm = rest.contains('?') && rest.contains('@')
        val jsonText = if (!queryForm) UriDecoder.decodeBase64(rest.substringBefore('?').substringBefore('#')) else null

        val parts: Parts
        val fields: Map<String, String>
        if (jsonText != null) {
            // JSON 形式
            val json = JSONObject(jsonText)
            val host = json.optString("add", "")
            val port = json.optInt("port", 0)
            if (host.isEmpty() || port <= 0) return null
            parts = Parts(
                userinfo = null,
                host = host,
                port = port,
                query = emptyMap(),
                name = json.optString("ps").ifBlank { null },
            )
            fields = buildMap {
                json.keys().forEach { k -> put(k, json.optString(k)) }
            }
        } else {
            // query 形式（v2rayN 风格）
            val p = splitParts(rest)
            if (p.port <= 0) return null
            parts = p
            fields = p.query
        }

        val outbound = JSONObject()
            .put("type", "vmess")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("uuid", fields["id"] ?: return null)
            .put("security", fields["scy"] ?: "auto")
            .put("alter_id", fields["aid"]?.toIntOrNull() ?: 0)

        val tlsEnabled = fields["tls"] == "tls"
        if (tlsEnabled) {
            buildTls(
                enabled = true,
                serverName = fields["sni"] ?: parts.host,
                alpn = fields["alpn"],
                fingerprint = fields["fp"],
            )?.let { outbound.put("tls", it) }
        }
        buildTransport(fields["net"], fields["path"], fields["host"], fields["serviceName"])?.let {
            outbound.put("transport", it)
        }
        return makeNode(parts, "vmess", outbound)
    }

    private fun parseTrojan(rest: String): Node? {
        val parts = splitParts(rest)
        if (parts.port <= 0) return null
        val password = parts.userinfo ?: return null
        val q = parts.query

        val outbound = JSONObject()
            .put("type", "trojan")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("password", password)
        buildTls(
            enabled = true,
            serverName = q["sni"] ?: parts.host,
            alpn = q["alpn"],
            fingerprint = q["fp"],
        )?.let { outbound.put("tls", it) }
        buildTransport(q["type"], q["path"], q["host"], q["serviceName"])?.let {
            outbound.put("transport", it)
        }
        return makeNode(parts, "trojan", outbound)
    }

    private fun parseShadowsocks(rest: String): Node? {
        // SIP002：ss://base64(method:password)@host:port#name
        // legacy ：ss://base64(method:password@host:port)#name
        val atIndex = rest.indexOf('@')
        val method: String
        val password: String
        val parts: Parts
        if (atIndex > 0) {
            val userB64 = rest.substring(0, atIndex)
            val decoded = UriDecoder.decodeBase64(userB64) ?: return null
            val sep = decoded.indexOf(':')
            if (sep <= 0) return null
            method = decoded.substring(0, sep)
            password = decoded.substring(sep + 1)
            parts = splitParts(rest.substring(atIndex + 1))
        } else {
            val b64 = rest.substringBefore('?').substringBefore('#')
            val decoded = UriDecoder.decodeBase64(b64) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at <= 0) return null
            val cred = decoded.substring(0, at)
            val sep = cred.indexOf(':')
            if (sep <= 0) return null
            method = cred.substring(0, sep)
            password = cred.substring(sep + 1)
            parts = splitParts(decoded.substring(at + 1) + "#" + rest.substringAfter('#', ""))
        }
        if (parts.port <= 0) return null

        // v2ray-plugin / obfs 插件暂不支持
        val plugin = parts.query["plugin"]
        if (!plugin.isNullOrEmpty()) return null

        val outbound = JSONObject()
            .put("type", "shadowsocks")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("method", method)
            .put("password", password)
        return makeNode(parts, "shadowsocks", outbound)
    }

    private fun parseHysteria2(rest: String): Node? {
        val parts = splitParts(rest)
        if (parts.port <= 0) return null
        val password = parts.userinfo ?: return null
        val q = parts.query

        val outbound = JSONObject()
            .put("type", "hysteria2")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("password", password)
        q["obfs"]?.takeIf { it.isNotBlank() }?.let { obfs ->
            outbound.put("obfs", JSONObject().put("type", obfs)
                .put("password", q["obfs-password"] ?: q["obfs_password"] ?: ""))
        }
        buildTls(
            enabled = true,
            serverName = q["sni"] ?: parts.host,
            alpn = q["alpn"],
            fingerprint = q["fp"],
            insecure = q["insecure"] == "1" || q["insecure"] == "true",
        )?.let { outbound.put("tls", it) }
        return makeNode(parts, "hysteria2", outbound)
    }

    private fun parseTuic(rest: String): Node? {
        val parts = splitParts(rest)
        if (parts.port <= 0) return null
        val userinfo = parts.userinfo ?: return null
        val sep = userinfo.indexOf(':')
        if (sep <= 0) return null
        val uuid = userinfo.substring(0, sep)
        val password = userinfo.substring(sep + 1)
        val q = parts.query

        val outbound = JSONObject()
            .put("type", "tuic")
            .put("tag", TAG)
            .put("server", parts.host)
            .put("server_port", parts.port)
            .put("uuid", uuid)
            .put("password", password)
        q["congestion_control"]?.takeIf { it.isNotBlank() }?.let {
            outbound.put("congestion_control", it)
        }
        buildTls(
            enabled = true,
            serverName = q["sni"] ?: parts.host,
            alpn = q["alpn"] ?: "h3",
            fingerprint = q["fp"],
            insecure = q["allowInsecure"] == "1" || q["insecure"] == "1",
        )?.let { outbound.put("tls", it) }
        return makeNode(parts, "tuic", outbound)
    }
}
