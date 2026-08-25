package com.airport.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 节点（代理服务器）实体 */
@Entity(
    tableName = "profiles",
    indices = [Index("subscriptionId")],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属订阅 ID */
    val subscriptionId: Long,
    /** 节点显示名称 */
    val name: String,
    /** 协议类型 */
    val protocol: String,
    /** 服务器地址 */
    val server: String,
    /** 服务器端口 */
    val port: Int,
    /** 原始分享链接 */
    val link: String = "",
    /** 转换后的 sing-box outbound JSON（tag 为 "proxy"） */
    val outbound: String,
)
