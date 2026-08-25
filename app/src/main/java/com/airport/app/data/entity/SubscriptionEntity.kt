package com.airport.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 订阅（机场）实体 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 订阅 URL */
    val url: String,
    /** 备注名（默认取 URL 主机名） */
    val name: String,
    /** 请求订阅时携带的 User-Agent */
    val userAgent: String = DEFAULT_USER_AGENT,
    /** 上次成功更新时间戳（毫秒） */
    val lastUpdatedAt: Long = 0,
    /** Subscription-Userinfo 流量统计（字节） */
    val trafficUsed: Long? = null,
    val trafficTotal: Long? = null,
    /** 到期时间戳（毫秒） */
    val expireAt: Long? = null,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "Airport/0.1.0"
    }
}
