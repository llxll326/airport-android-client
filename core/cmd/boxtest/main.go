// 完整模拟 daemon：box.New + PlatformLogWriter + platform interface（含真实 monitor 回调）
package main

import (
	"context"
	"fmt"
	"net/netip"
	"os"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/include"
	singlog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	singjson "github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service/filemanager"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

type dummyPW struct{}

func (d *dummyPW) WriteMessage(level singlog.Level, message string) {}

type stubPlatform struct{}

func (s *stubPlatform) Initialize(networkManager adapter.NetworkManager) error { return nil }
func (s *stubPlatform) UsePlatformAutoDetectInterfaceControl() bool           { return true }
func (s *stubPlatform) AutoDetectInterfaceControl(fd int) error               { return nil }
func (s *stubPlatform) UsePlatformInterface() bool                            { return true }
func (s *stubPlatform) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	return nil, fmt.Errorf("no tun")
}
func (s *stubPlatform) UsePlatformDefaultInterfaceMonitor() bool { return true }
func (s *stubPlatform) CreateDefaultInterfaceMonitor(logger logger.Logger) tun.DefaultInterfaceMonitor {
	return &stubMonitor{}
}
func (s *stubPlatform) UsePlatformNetworkInterfaces() bool { return true }
func (s *stubPlatform) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	return nil, fmt.Errorf("none")
}
func (s *stubPlatform) UnderNetworkExtension() bool              { return false }
func (s *stubPlatform) NetworkExtensionIncludeAllNetworks() bool { return false }
func (s *stubPlatform) ClearDNSCache()                           {}
func (s *stubPlatform) RequestPermissionForWIFIState() error     { return nil }
func (s *stubPlatform) ReadWIFIState() adapter.WIFIState         { return adapter.WIFIState{} }
func (s *stubPlatform) SystemCertificates() []string             { return nil }
func (s *stubPlatform) UsePlatformConnectionOwnerFinder() bool   { return false }
func (s *stubPlatform) FindConnectionOwner(*adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	return nil, fmt.Errorf("not found")
}
func (s *stubPlatform) UsePlatformWIFIMonitor() bool { return false }
func (s *stubPlatform) UsePlatformNotification() bool {
	return false
}
func (s *stubPlatform) SendNotification(*adapter.Notification) error { return nil }
func (s *stubPlatform) MyInterfaceAddress() []netip.Addr             { return nil }

type stubMonitor struct {
	callbacks list.List[tun.DefaultInterfaceUpdateCallback]
}

func (m *stubMonitor) Start() error { return nil }
func (m *stubMonitor) Close() error { return nil }
func (m *stubMonitor) DefaultInterface() *control.Interface {
	return nil
}
func (m *stubMonitor) OverrideAndroidVPN() bool { return false }
func (m *stubMonitor) AndroidVPNEnabled() bool  { return false }
func (m *stubMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	return m.callbacks.PushBack(callback)
}
func (m *stubMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.callbacks.Remove(element)
}
func (m *stubMonitor) RegisterMyInterface(interfaceName string) {}
func (m *stubMonitor) MyInterfaces() []string                   { return nil }

func main() {
	data, err := os.ReadFile("C:/Users/mushroom/.devtools/simple_config.json")
	if err != nil {
		fmt.Println("read config failed:", err)
		os.Exit(1)
	}
	ctx := context.Background()
	ctx = filemanager.WithDefault(ctx, os.TempDir(), os.TempDir(), -1, -1)
	ctx = box.Context(ctx, include.InboundRegistry(), include.OutboundRegistry(), include.EndpointRegistry(), include.DNSTransportRegistry(), include.ServiceRegistry())
	service.MustRegister[adapter.PlatformInterface](ctx, &stubPlatform{})
	options, err := singjson.UnmarshalExtendedContext[option.Options](ctx, data)
	if err != nil {
		fmt.Println("parse config failed:", err)
		os.Exit(1)
	}
	instance, err := box.New(box.Options{Context: ctx, Options: options, PlatformLogWriter: &dummyPW{}})
	if err != nil {
		fmt.Println("box.New FAILED:", err)
		os.Exit(1)
	}
	if err := instance.Start(); err != nil {
		fmt.Println("instance.Start FAILED:", err)
		os.Exit(1)
	}
	fmt.Println("BOX STARTED (box.New + PLW + platform + filemanager)")
	select {}
}
