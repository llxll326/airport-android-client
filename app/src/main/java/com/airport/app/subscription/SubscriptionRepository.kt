package com.airport.app.subscription

import android.content.Context
import com.airport.app.AirportApp
import com.airport.app.data.entity.ProfileEntity
import com.airport.app.data.entity.SubscriptionEntity
import com.airport.app.model.Node
import java.net.URL
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

/** 订阅仓库：抓取 → 解析 → 入库，向 UI 提供订阅/节点数据 */
class SubscriptionRepository(context: Context) {

    private val database = (context.applicationContext as AirportApp).database
    private val subscriptionDao = database.subscriptionDao()
    private val profileDao = database.profileDao()
    private val fetcher = SubscriptionFetcher()

    val subscriptions = subscriptionDao.observeAll()

    fun observeProfiles() = profileDao.observeAll()

    suspend fun getProfile(id: Long) = profileDao.getById(id)

    suspend fun getSubscription(id: Long) = subscriptionDao.getById(id)

    /** 添加订阅：下载并解析，成功后入库 */
    suspend fun addSubscription(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "订阅 URL 必须以 http:// 或 https:// 开头"
        }
        val result = try {
            fetchAndParse(url, SubscriptionEntity.DEFAULT_USER_AGENT)
        } catch (e: SubscriptionException) {
            return e.message
        }
        val subscription = SubscriptionEntity(
            url = url,
            name = nameFromUrl(url),
            lastUpdatedAt = System.currentTimeMillis(),
            trafficUsed = result.trafficUsed,
            trafficTotal = result.trafficTotal,
            expireAt = result.expireAt,
        )
        val id = subscriptionDao.insert(subscription)
        profileDao.insertAll(result.nodes.map { it.toEntity(id) })
        return null
    }

    /** 更新订阅：重新下载解析并替换该订阅下的节点 */
    suspend fun refreshSubscription(subscription: SubscriptionEntity): String? {
        val result = try {
            fetchAndParse(subscription.url, subscription.userAgent)
        } catch (e: SubscriptionException) {
            return e.message
        }
        profileDao.deleteBySubscription(subscription.id)
        profileDao.insertAll(result.nodes.map { it.toEntity(subscription.id) })
        subscriptionDao.update(
            subscription.copy(
                lastUpdatedAt = System.currentTimeMillis(),
                trafficUsed = result.trafficUsed,
                trafficTotal = result.trafficTotal,
                expireAt = result.expireAt,
            )
        )
        return null
    }

    suspend fun deleteSubscription(subscription: SubscriptionEntity) {
        profileDao.deleteBySubscription(subscription.id)
        subscriptionDao.delete(subscription)
    }

    /** 订阅抓取+解析结果（nodes + 订阅信息头） */
    private data class ParsedFetch(
        val nodes: List<Node>,
        val trafficUsed: Long?,
        val trafficTotal: Long?,
        val expireAt: Long?,
    )

    private suspend fun fetchAndParse(url: String, userAgent: String): ParsedFetch {
        val fetched = fetcher.fetch(url, userAgent)
        val parsed = SubscriptionParser.parse(fetched.content)
        if (!parsed.success || parsed.nodes.isEmpty()) {
            throw SubscriptionException(parsed.error ?: "订阅解析失败")
        }
        return ParsedFetch(
            nodes = parsed.nodes,
            trafficUsed = fetched.trafficUsed,
            trafficTotal = fetched.trafficTotal,
            expireAt = fetched.expireAt,
        )
    }

    private fun nameFromUrl(url: String): String =
        try {
            URL(url).host.ifBlank { url }
        } catch (_: Exception) {
            url
        }
}

private fun Node.toEntity(subscriptionId: Long) = ProfileEntity(
    subscriptionId = subscriptionId,
    name = name,
    protocol = protocol,
    server = server,
    port = port,
    link = link,
    outbound = outbound,
)

/** 工具：把时间戳格式化为易读文本（供 UI 展示） */
fun Long.formatDateTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
