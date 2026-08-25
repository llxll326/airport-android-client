package com.airport.app.subscription

import com.airport.app.model.Node
import com.airport.app.subscription.parser.ClashYamlParser
import com.airport.app.subscription.parser.ShareLinkParser
import com.airport.app.subscription.parser.SingBoxJsonParser
import com.airport.app.subscription.parser.UriDecoder

/** 订阅内容解析编排：自动嗅探格式并输出节点列表 */
object SubscriptionParser {

    data class ParseResult(
        val nodes: List<Node>,
        val error: String? = null,
    ) {
        val success: Boolean get() = error == null
    }

    fun parse(content: String): ParseResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ParseResult(emptyList(), "订阅内容为空")

        // 1. Clash YAML
        if (trimmed.startsWith("proxies:") || trimmed.contains("\nproxies:")) {
            val nodes = ClashYamlParser.parse(trimmed)
            return if (nodes.isNotEmpty()) ParseResult(nodes)
            else ParseResult(emptyList(), "Clash 配置解析失败：未找到可用的 proxies 节点")
        }

        // 2. sing-box JSON
        if (trimmed.startsWith("{")) {
            val nodes = SingBoxJsonParser.parse(trimmed)
            if (nodes.isNotEmpty()) return ParseResult(nodes)
        }

        // 3. 尝试 base64 解码的分享链接列表
        val decoded = UriDecoder.decodeBase64(trimmed)
        if (decoded != null && decoded.contains("://")) {
            val nodes = parseLinks(decoded)
            if (nodes.isNotEmpty()) return ParseResult(nodes)
        }

        // 4. 纯文本分享链接列表
        if (trimmed.contains("://")) {
            val nodes = parseLinks(trimmed)
            if (nodes.isNotEmpty()) return ParseResult(nodes)
        }

        return ParseResult(emptyList(), "无法识别的订阅格式（支持：分享链接 / base64 列表 / Clash YAML / sing-box JSON）")
    }

    /** 逐行解析分享链接文本（链接列表或已解码的 base64 内容） */
    private fun parseLinks(text: String): List<Node> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { ShareLinkParser.parse(it) }
            .toList()
}
