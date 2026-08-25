package com.airport.app.model

/**
 * 订阅解析器输出的统一节点模型。
 *
 * @param name 节点显示名称
 * @param protocol 协议类型（vless/vmess/trojan/shadowsocks/hysteria2/tuic）
 * @param server 服务器地址（不含端口）
 * @param port 服务器端口
 * @param link 原始分享链接
 * @param outbound 转换后的 sing-box outbound JSON（tag 固定为 "proxy"）
 */
data class Node(
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val link: String,
    val outbound: String,
)
