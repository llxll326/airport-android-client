// daemon 路径 + CommandClient 日志订阅：观察 daemon 下连接失败的具体原因
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"time"

	"libbox/libbox"
)

type logHandler struct{}

func (h *logHandler) Connected()                              {}
func (h *logHandler) Disconnected(message string)             {}
func (h *logHandler) SetDefaultLogLevel(level int32)          {}
func (h *logHandler) ClearLogs()                              {}
func (h *logHandler) WriteLogs(messageList libbox.LogIterator) {
	for messageList.HasNext() {
		entry := messageList.Next()
		fmt.Printf("LOG[%d]: %s\n", entry.GetLevel(), entry.GetMessage())
	}
}
func (h *logHandler) WriteStatus(message *libbox.StatusMessage)       {}
func (h *logHandler) WriteGroups(message libbox.OutboundGroupIterator) {}
func (h *logHandler) InitializeClashMode(modeList libbox.StringIterator, currentMode string) {
}
func (h *logHandler) UpdateClashMode(newMode string)                  {}
func (h *logHandler) WriteConnectionEvents(events *libbox.ConnectionEvents) {
}

func main() {
	data, err := os.ReadFile("C:/Users/mushroom/.devtools/simple_config.json")
	if err != nil {
		fmt.Println("read config failed:", err)
		os.Exit(1)
	}
	var cfg map[string]any
	json.Unmarshal(data, &cfg)
	cfgJSON, _ := json.Marshal(cfg)

	if err := libbox.Setup(&libbox.SetupOptions{
		BasePath: os.TempDir(), WorkingPath: os.TempDir(), TempPath: os.TempDir(), LogMaxLines: 1000,
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

	// 订阅日志
	client := libbox.NewCommandClient(&logHandler{}, &libbox.CommandClientOptions{})
	if err := client.Connect(); err != nil {
		fmt.Println("CLIENT CONNECT FAILED:", err)
	} else {
		fmt.Println("LOG SUBSCRIBED")
	}

	if err := service.StartService(string(cfgJSON)); err != nil {
		fmt.Println("STARTSERVICE FAILED:", err)
		service.Close()
		os.Exit(1)
	}
	fmt.Println("SERVICE STARTED (daemon + log subscription)")
	select {}
}

type stubPlatform struct{}

func (s stubPlatform) LocalDNSTransport() libbox.LocalDNSTransport { return nil }
func (s stubPlatform) UsePlatformAutoDetectInterfaceControl() bool { return true }
func (s stubPlatform) AutoDetectInterfaceControl(fd int32) error   { return nil }
func (s stubPlatform) OpenTun(options libbox.TunOptions) (int32, error) {
	return 0, fmt.Errorf("no tun")
}
func (s stubPlatform) UseProcFS() bool { return false }
func (s stubPlatform) FindConnectionOwner(int32, string, int32, string, int32) (*libbox.ConnectionOwner, error) {
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
