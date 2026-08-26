// 节点连通性测试：用真实节点出站启动本地 HTTP 代理，验证节点可用性与配置正确性。
// 用法:
//   go run -tags ... ./cmd/nodetest <outbound.json> [listen_port]
// 示例 outbound.json: {"type":"vless","server":"x.com","server_port":443,"uuid":"...","tls":{...}}
package main

import (
	"encoding/json"
	"fmt"
	"net"
	"os"
	"time"

	"libbox/libbox"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Println("usage: nodetest <outbound.json> [listen_port]")
		os.Exit(1)
	}
	data, err := os.ReadFile(os.Args[1])
	if err != nil {
		fmt.Println("read outbound failed:", err)
		os.Exit(1)
	}
	var ob map[string]any
	if err := json.Unmarshal(data, &ob); err != nil {
		fmt.Println("invalid outbound json:", err)
		os.Exit(1)
	}
	ob["tag"] = "proxy"
	obJSON, _ := json.Marshal(ob)

	port := 10810
	if len(os.Args) > 2 {
		fmt.Sscanf(os.Args[2], "%d", &port)
	}

	config := fmt.Sprintf(`{
  "log": {"level": "info", "timestamp": true},
  "dns": {
    "servers": [
      {"tag": "dns-remote", "address": "https://1.1.1.1/dns-query", "detour": "proxy"},
      {"tag": "dns-local", "address": "223.5.5.5", "detour": "direct"}
    ],
    "final": "dns-remote",
    "strategy": "ipv4_only"
  },
  "inbounds": [
    {"type": "http", "tag": "http-in", "listen": "127.0.0.1", "listen_port": %d},
    {"type": "socks", "tag": "socks-in", "listen": "127.0.0.1", "listen_port": %d}
  ],
  "outbounds": [
    %s,
    {"type": "direct", "tag": "direct"}
  ],
  "route": {
    "rules": [
      {"protocol": "dns", "action": "hijack-dns"},
      {"ip_cidr": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "127.0.0.0/8"], "outbound": "direct"}
    ],
    "final": "proxy",
  }
}`, port, port+1, string(obJSON))

	if err := libbox.Setup(&libbox.SetupOptions{
		BasePath:    os.TempDir(),
		WorkingPath: os.TempDir(),
		TempPath:    os.TempDir(),
		LogMaxLines: 1000,
	}); err != nil {
		fmt.Println("SETUP FAILED:", err)
		os.Exit(1)
	}
	service, err := libbox.NewService(stubHandler{}, stubPlatform{})
	if err != nil {
		fmt.Println("NEWSERVICE FAILED:", err)
		os.Exit(1)
	}
	if err := service.Start(); err != nil {
		fmt.Println("START FAILED:", err)
		os.Exit(1)
	}
	if err := service.StartService(config); err != nil {
		fmt.Println("STARTSERVICE FAILED:", err)
		service.Close()
		os.Exit(1)
	}
	fmt.Printf("SERVICE STARTED: http://127.0.0.1:%d (proxy outbound: %s)\n", port, ob["server"])

	// 等待监听端口就绪
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		conn, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", port))
		if err == nil {
			conn.Close()
			fmt.Println("HTTP PROXY LISTENING OK")
			break
		}
		time.Sleep(200 * time.Millisecond)
	}

	select {}
}

type stubPlatform struct{}

func (s stubPlatform) LocalDNSTransport() libbox.LocalDNSTransport { return nil }
func (s stubPlatform) UsePlatformAutoDetectInterfaceControl() bool { return true }
func (s stubPlatform) AutoDetectInterfaceControl(fd int32) error   { return nil }
func (s stubPlatform) OpenTun(options libbox.TunOptions) (int32, error) {
	return 0, fmt.Errorf("no tun in this test")
}
func (s stubPlatform) UseProcFS() bool { return false }
func (s stubPlatform) FindConnectionOwner(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (*libbox.ConnectionOwner, error) {
	return nil, fmt.Errorf("not found")
}
func (s stubPlatform) StartDefaultInterfaceMonitor(listener libbox.InterfaceUpdateListener) error {
	return nil
}
func (s stubPlatform) CloseDefaultInterfaceMonitor(listener libbox.InterfaceUpdateListener) error {
	return nil
}
func (s stubPlatform) GetInterfaces() (libbox.NetworkInterfaceIterator, error) {
	return nil, fmt.Errorf("none")
}
func (s stubPlatform) UnderNetworkExtension() bool      { return false }
func (s stubPlatform) IncludeAllNetworks() bool         { return false }
func (s stubPlatform) ReadWIFIState() *libbox.WIFIState { return nil }
func (s stubPlatform) SystemCertificates() libbox.StringIterator {
	return nil
}
func (s stubPlatform) ClearDNSCache() {}
func (s stubPlatform) SendNotification(notification *libbox.Notification) error {
	return nil
}

type stubHandler struct{}

func (h stubHandler) ServiceStop() error   { return nil }
func (h stubHandler) ServiceReload() error { return nil }
func (h stubHandler) GetSystemProxyStatus() (*libbox.SystemProxyStatus, error) {
	return &libbox.SystemProxyStatus{}, nil
}
func (h stubHandler) SetSystemProxyEnabled(enabled bool) error { return nil }
func (h stubHandler) WriteDebugMessage(message string)         {}
