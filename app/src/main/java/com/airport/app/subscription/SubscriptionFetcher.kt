package com.airport.app.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 订阅抓取结果：内容 + 订阅信息头 */
data class FetchResult(
    val content: String,
    /** Subscription-Userinfo 头解析出的流量/到期信息（可能缺失） */
    val trafficUsed: Long? = null,
    val trafficTotal: Long? = null,
    val expireAt: Long? = null,
)

/** 订阅下载/解析过程中的业务异常 */
class SubscriptionException(message: String) : Exception(message)

/** 订阅下载器：HTTP GET 订阅 URL，读取订阅信息头 */
class SubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    /** 解析 Subscription-Userinfo 头：upload=..; download=..; total=..; expire=.. */
    private fun parseUserinfo(header: String?): FetchResult? {
        if (header.isNullOrBlank()) return null
        var used: Long? = null
        var total: Long? = null
        var expire: Long? = null
        header.split(';').forEach { item ->
            val kv = item.trim().split('=', limit = 2)
            if (kv.size != 2) return@forEach
            when (kv[0]) {
                "upload", "download" -> used = (used ?: 0L) + (kv[1].toLongOrNull() ?: 0L)
                "total" -> total = kv[1].toLongOrNull()
                "expire" -> expire = kv[1].toLongOrNull()
            }
        }
        return FetchResult("", used, total, expire)
    }

    /** 下载订阅内容，网络异常/HTTP 错误时抛 [SubscriptionException] */
    suspend fun fetch(url: String, userAgent: String): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SubscriptionException("订阅下载失败：HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw SubscriptionException("订阅内容为空")
                val info = parseUserinfo(response.header("Subscription-Userinfo"))
                FetchResult(
                    content = body,
                    trafficUsed = info?.trafficUsed,
                    trafficTotal = info?.trafficTotal,
                    expireAt = info?.expireAt,
                )
            }
        } catch (e: SubscriptionException) {
            throw e
        } catch (e: Exception) {
            throw SubscriptionException("订阅下载失败：${e.message ?: "网络错误"}")
        }
    }
}
