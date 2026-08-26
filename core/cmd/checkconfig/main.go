// 配置校验工具：本地验证 ConfigBuilder 生成的 sing-box 配置是否合法
// 用法: go run -tags with_quic,with_grpc,with_dhcp,with_wireguard,with_ech,with_utls,with_reality_server ./cmd/checkconfig
package main

import (
	"fmt"
	"os"

	"libbox/libbox"
)

const sampleConfig = `{
  "log": {"level": "info", "timestamp": true, "output": "/tmp/sing-box.log"},
  "dns": {
    "servers": [
      {"tag": "dns-remote", "address": "https://1.1.1.1/dns-query", "detour": "proxy"},
      {"tag": "dns-local", "address": "223.5.5.5", "detour": "direct"}
    ],
    "rules": [
      {"domain_suffix": [".xn--ghqu5fm27b67w.com"], "server": "dns-local"}
    ],
    "final": "dns-remote",
    "strategy": "ipv4_only"
  },
  "inbounds": [
    {
      "type": "tun",
      "tag": "tun-in",
      "interface_name": "tun0",
      "mtu": 1500,
      "address": ["172.19.0.1/30"],
      "auto_route": true,
      "strict_route": true,
      "stack": "gvisor"
    }
  ],
  "outbounds": [
    {
      "type": "vless",
      "tag": "proxy",
      "server": "example.com",
      "server_port": 443,
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "flow": "xtls-rprx-vision",
      "tls": {"enabled": true, "server_name": "example.com", "utls": {"enabled": true, "fingerprint": "chrome"}, "reality": {"enabled": true, "public_key": "iq4VN90fXsJqxRuY8UZuidZFlHyZemL0qS8kDPY3A2c", "short_id": "abcd1234"}},
      "transport": {"type": "ws", "path": "/ws", "headers": {"Host": "example.com"}}
    },
    {
      "type": "trojan",
      "tag": "trojan-1",
      "server": "t.example.com",
      "server_port": 443,
      "password": "pw",
      "tls": {"enabled": true, "server_name": "t.example.com"}
    },
    {
      "type": "shadowsocks",
      "tag": "ss-1",
      "server": "s.example.com",
      "server_port": 8388,
      "method": "aes-256-gcm",
      "password": "secret"
    },
    {
      "type": "hysteria2",
      "tag": "hy2-1",
      "server": "h.example.com",
      "server_port": 8443,
      "password": "hy-pw",
      "tls": {"enabled": true, "server_name": "h.example.com"}
    },
    {
      "type": "tuic",
      "tag": "tuic-1",
      "server": "tuic.example.com",
      "server_port": 7777,
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "password": "tuic-pw",
      "congestion_control": "bbr",
      "tls": {"enabled": true, "server_name": "tuic.example.com", "alpn": ["h3"]}
    },
    {"type": "direct", "tag": "direct"},
    {"type": "block", "tag": "block"}
  ],
  "route": {
    "rules": [
      {"action": "sniff"},
      {"protocol": "dns", "action": "hijack-dns"},
      {"ip_cidr": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "127.0.0.0/8"], "outbound": "direct"}
    ],
    "final": "proxy",
    "auto_detect_interface": true
  }
}`

func main() {
	configPath := ""
	if len(os.Args) > 1 {
		configPath = os.Args[1]
	}
	content := sampleConfig
	if configPath != "" {
		data, err := os.ReadFile(configPath)
		if err != nil {
			fmt.Println("read config failed:", err)
			os.Exit(1)
		}
		content = string(data)
	}
	if err := libbox.CheckConfig(content); err != nil {
		fmt.Println("CONFIG INVALID:", err)
		os.Exit(1)
	}
	fmt.Println("CONFIG OK")
}
