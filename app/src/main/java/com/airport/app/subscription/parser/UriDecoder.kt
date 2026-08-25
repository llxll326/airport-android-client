package com.airport.app.subscription.parser

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** 订阅解析用编解码工具（纯 JVM 实现，单元测试可直接使用） */
object UriDecoder {

    /**
     * 解码 base64 字符串，兼容：
     * - 标准 base64（可能带换行）
     * - URL-safe base64（`-`/`_`）
     * - 缺少 padding 的写法
     */
    fun decodeBase64(input: String): String? {
        val cleaned = input.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
        for (candidate in listOf(cleaned, cleaned.replace('-', '+').replace('_', '/'))) {
            try {
                val padded = candidate + "=".repeat((4 - candidate.length % 4) % 4)
                return String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                // 尝试下一种形式
            }
        }
        return null
    }

    /** percent 解码（用于 query 参数值） */
    fun percentDecode(input: String): String =
        try {
            URLDecoder.decode(input, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            input
        }

    /** 判断内容看起来像 base64（仅用于嗅探决策） */
    fun looksLikeBase64(input: String): Boolean =
        input.length >= 16 && Regex("^[A-Za-z0-9+/_=-]+$").matches(input.trim())
}
