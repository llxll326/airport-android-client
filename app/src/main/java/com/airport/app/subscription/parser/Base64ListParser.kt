package com.airport.app.subscription.parser

import com.airport.app.model.Node

/** base64 分享链接列表解析器：解码后逐行解析分享链接 */
object Base64ListParser {

    fun parse(content: String): List<Node> {
        val decoded = UriDecoder.decodeBase64(content) ?: return emptyList()
        return decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { ShareLinkParser.parse(it) }
            .toList()
    }
}
