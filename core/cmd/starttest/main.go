// 启动路径测试：验证 daemon（CommandServer.StartOrReloadService）能否真正启动服务。
// 使用本地 HTTP 代理配置（无 TUN，Windows 可运行），复现用户遇到的 PlatformLogWriter → needClashAPI 场景。
// 用法: go run -tags with_quic,with_grpc,with_dhcp,with_wireguard,with_utls,with_gvisor,with_clash_api ./cmd/starttest
package main

import (
	"fmt"
	"os"
	"time"

	"libbox/libbox"
)

const startConfig = `{
  "log": {"level": "info", "timestamp": true},
  "inbounds": [
    {"type": "http", "tag": "http-in", "listen": "127.0.0.1", "listen_port": 10808}
  ],
  "outbounds": [
    {"type": "direct", "tag": "direct"}
  ],
  "route": {"final": "direct"}
}`

// stubPlatform：无 TUN 场景下的最小 PlatformInterface 实现
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
func (s stubPlatform) UnderNetworkExtension() bool       { return false }
func (s stubPlatform) IncludeAllNetworks() bool          { return false }
func (s stubPlatform) ReadWIFIState() *libbox.WIFIState  { return nil }
func (s stubPlatform) SystemCertificates() libbox.StringIterator {
	return nil
}
func (s stubPlatform) ClearDNSCache() {}
func (s stubPlatform) SendNotification(notification *libbox.Notification) error {
	return nil
}

// stubHandler：最小 ServiceHandler 实现
type stubHandler struct{}

func (h stubHandler) ServiceStop() error                          { return nil }
func (h stubHandler) ServiceReload() error                        { return nil }
func (h stubHandler) GetSystemProxyStatus() (*libbox.SystemProxyStatus, error) {
	return &libbox.SystemProxyStatus{}, nil
}
func (h stubHandler) SetSystemProxyEnabled(enabled bool) error { return nil }
func (h stubHandler) WriteDebugMessage(message string)         {}

func main() {
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
	if err := service.StartService(startConfig); err != nil {
		fmt.Println("STARTSERVICE FAILED:", err)
		service.Close()
		os.Exit(1)
	}
	fmt.Println("SERVICE STARTED OK")
	time.Sleep(500 * time.Millisecond)
	if err := service.StopService(); err != nil {
		fmt.Println("STOPSERVICE FAILED:", err)
		os.Exit(1)
	}
	service.Close()
	fmt.Println("SERVICE STOPPED OK")
}
