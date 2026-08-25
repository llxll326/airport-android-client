package com.airport.app.subscription.parser

import com.airport.app.subscription.SubscriptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {

    @Test
    fun `base64 分享链接列表嗅探`() {
        val links = listOf(
            "vless://uuid@example.com:443?security=tls&type=ws#节点A",
            "trojan://pw@trojan.example.com:443#节点B",
            "vmess://${Base64.getEncoder().encodeToString("""{"v":"2","ps":"C","add":"c.com","port":"443","id":"u","aid":"0","net":"tcp","tls":"none"}""".toByteArray())}",
        ).joinToString("\n")
        val b64 = Base64.getEncoder().encodeToString(links.toByteArray())
        val result = SubscriptionParser.parse(b64)
        assertTrue(result.success)
        assertEquals(3, result.nodes.size)
        assertEquals("节点A", result.nodes[0].name)
    }

    @Test
    fun `纯文本分享链接嗅探`() {
        val content = "vless://uuid@example.com:443#直链节点"
        val result = SubscriptionParser.parse(content)
        assertTrue(result.success)
        assertEquals(1, result.nodes.size)
    }

    @Test
    fun `clash yaml 嗅探`() {
        val yaml = """
            proxies:
              - name: "日本1"
                type: vmess
                server: jp1.example.com
                port: 443
                uuid: 1234
                alterId: 0
                cipher: auto
                tls: true
                servername: jp1.example.com
              - name: "美国1"
                type: trojan
                server: us1.example.com
                port: 443
                password: pw
                sni: us1.example.com
              - name: "不支持类型"
                type: wireguard
                server: wg.example.com
                port: 51820
        """.trimIndent()
        val result = SubscriptionParser.parse(yaml)
        assertTrue(result.success)
        assertEquals(2, result.nodes.size)
        assertEquals("日本1", result.nodes[0].name)
        assertEquals("trojan", result.nodes[1].protocol)
    }

    @Test
    fun `sing-box json 嗅探`() {
        val json = """
            {
              "outbounds": [
                {"type":"vless","tag":"proxy-1","server":"a.com","server_port":443,"uuid":"u"},
                {"type":"direct","tag":"direct"},
                {"type":"selector","tag":"proxy","outbounds":["proxy-1"]},
                {"type":"vless","tag":"proxy-2","server":"b.com","server_port":8443,"uuid":"u2"}
              ]
            }
        """.trimIndent()
        val result = SubscriptionParser.parse(json)
        assertTrue(result.success)
        // direct/selector 被过滤，只保留代理出站
        assertEquals(2, result.nodes.size)
    }

    @Test
    fun `无法识别的格式报错`() {
        val result = SubscriptionParser.parse("这不是订阅内容")
        assertFalse(result.success)
        assertTrue(result.error != null)
    }

    @Test
    fun `空内容报错`() {
        assertFalse(SubscriptionParser.parse("").success)
    }
}
