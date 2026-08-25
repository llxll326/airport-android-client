package com.airport.app.subscription.parser

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun `vless ws tls 链接解析`() {
        val link = "vless://6f6e6f6e-6170-706c-652d-7061-7373776f7264@example.com:443?type=ws&security=tls&sni=cdn.example.com&fp=chrome&path=%2Fws&host=cdn.example.com#香港-节点"
        val node = ShareLinkParser.parse(link)
        assertNotNull(node)
        node!!
        assertEquals("vless", node.protocol)
        assertEquals("example.com", node.server)
        assertEquals(443, node.port)
        assertEquals("香港-节点", node.name)

        val ob = JSONObject(node.outbound)
        assertEquals("vless", ob.getString("type"))
        assertEquals("proxy", ob.getString("tag"))
        assertEquals("6f6e6f6e-6170-706c-652d-7061-7373776f7264", ob.getString("uuid"))
        val tls = ob.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("cdn.example.com", tls.getString("server_name"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        val transport = ob.getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/ws", transport.getString("path"))
        assertEquals("cdn.example.com", transport.getJSONObject("headers").getString("Host"))
    }

    @Test
    fun `vless reality 链接解析`() {
        val link = "vless://uuid@example.com:443?security=reality&sni=www.microsoft.com&fp=chrome&pbk=publickey&sid=abcd&flow=xtls-rprx-vision&type=tcp#Reality"
        val node = ShareLinkParser.parse(link)
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("xtls-rprx-vision", ob.getString("flow"))
        val reality = ob.getJSONObject("tls").getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals("publickey", reality.getString("public_key"))
        assertEquals("abcd", reality.getString("short_id"))
    }

    @Test
    fun `vmess base64 json 链接解析`() {
        val json = """{"v":"2","ps":"日本-东京","add":"jp.example.com","port":"443","id":"uuid-1234","aid":"0","scy":"auto","net":"ws","type":"none","host":"jp.example.com","path":"/vmess","tls":"tls","sni":"jp.example.com"}"""
        val b64 = java.util.Base64.getEncoder().encodeToString(json.toByteArray())
        val node = ShareLinkParser.parse("vmess://$b64")
        assertNotNull(node)
        node!!
        assertEquals("vmess", node.protocol)
        assertEquals("日本-东京", node.name)
        assertEquals("jp.example.com", node.server)
        assertEquals(443, node.port)

        val ob = JSONObject(node.outbound)
        assertEquals("uuid-1234", ob.getString("uuid"))
        assertEquals("auto", ob.getString("security"))
        assertTrue(ob.getJSONObject("tls").getBoolean("enabled"))
        assertEquals("ws", ob.getJSONObject("transport").getString("type"))
    }

    @Test
    fun `trojan 链接解析`() {
        val link = "trojan://password123@trojan.example.com:443?sni=trojan.example.com&type=grpc&serviceName=svc#Trojan"
        val node = ShareLinkParser.parse(link)
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("trojan", ob.getString("type"))
        assertEquals("password123", ob.getString("password"))
        assertTrue(ob.getJSONObject("tls").getBoolean("enabled"))
        assertEquals("grpc", ob.getJSONObject("transport").getString("type"))
    }

    @Test
    fun `ss sip002 链接解析`() {
        val cred = java.util.Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        val node = ShareLinkParser.parse("ss://$cred@ss.example.com:8388#新加坡")
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("shadowsocks", ob.getString("type"))
        assertEquals("aes-256-gcm", ob.getString("method"))
        assertEquals("secret", ob.getString("password"))
        assertEquals("新加坡", node.name)
    }

    @Test
    fun `ss legacy 链接解析`() {
        val raw = java.util.Base64.getEncoder()
            .encodeToString("chacha20-ietf-poly1305:pw@legacy.example.com:1234".toByteArray())
        val node = ShareLinkParser.parse("ss://$raw#老式")
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("chacha20-ietf-poly1305", ob.getString("method"))
        assertEquals("legacy.example.com", ob.getString("server"))
    }

    @Test
    fun `ss 带 plugin 的节点跳过`() {
        val cred = java.util.Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        val node = ShareLinkParser.parse("ss://$cred@ss.example.com:8388?plugin=v2ray-plugin%3Bmode%3Dwebsocket#插件")
        assertNull(node)
    }

    @Test
    fun `hysteria2 链接解析`() {
        val link = "hysteria2://hy-password@hy.example.com:8443?sni=hy.example.com&insecure=1&obfs=salamander&obfs-password=obfs-secret#Hysteria2"
        val node = ShareLinkParser.parse(link)
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("hysteria2", ob.getString("type"))
        assertEquals("hy-password", ob.getString("password"))
        assertTrue(ob.getJSONObject("tls").getBoolean("insecure"))
        assertEquals("salamander", ob.getJSONObject("obfs").getString("type"))
    }

    @Test
    fun `tuic 链接解析`() {
        val link = "tuic://uuid-tuic:tuic-pw@tuic.example.com:7777?sni=tuic.example.com&alpn=h3&congestion_control=bbr#TUIC"
        val node = ShareLinkParser.parse(link)
        assertNotNull(node)
        node!!
        val ob = JSONObject(node.outbound)
        assertEquals("tuic", ob.getString("type"))
        assertEquals("uuid-tuic", ob.getString("uuid"))
        assertEquals("tuic-pw", ob.getString("password"))
        assertEquals("bbr", ob.getString("congestion_control"))
        assertEquals("h3", ob.getJSONObject("tls").getJSONArray("alpn").getString(0))
    }

    @Test
    fun `不支持的协议返回 null`() {
        assertNull(ShareLinkParser.parse("ssr://abc123"))
        assertNull(ShareLinkParser.parse("wireguard://not-support"))
        assertNull(ShareLinkParser.parse("ftp://example.com"))
    }
}
