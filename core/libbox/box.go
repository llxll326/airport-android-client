// Package libbox provides the sing-box core binding for Android.
//
// The package is a copy of the official sing-box experimental/libbox
// package with the addition of a thin service wrapper (Setup / NewService /
// BoxService) below. Built with gomobile:
//
//	gomobile bind -v -androidapi 21 -javapkg libbox -ldflags=-checklinkname=0 \
//	  -o ../app/libs/libbox.aar ./libbox
//
// The -checklinkname=0 flag is required because pidfd_android.go references
// the internal symbol os.checkPidfdOnce via go:linkname (Go 1.23+ enforces
// linkname checks during cross-compilation).
package libbox

// ServiceHandler is the platform (Kotlin) callback interface,
// corresponding to CommandServerHandler.
type ServiceHandler interface {
	ServiceStop() error
	ServiceReload() error
	GetSystemProxyStatus() (*SystemProxyStatus, error)
	SetSystemProxyEnabled(enabled bool) error
	WriteDebugMessage(message string)
}

type handlerAdapter struct {
	handler ServiceHandler
}

func (h handlerAdapter) ServiceStop() error {
	return h.handler.ServiceStop()
}

func (h handlerAdapter) ServiceReload() error {
	return h.handler.ServiceReload()
}

func (h handlerAdapter) GetSystemProxyStatus() (*SystemProxyStatus, error) {
	status, err := h.handler.GetSystemProxyStatus()
	if err != nil {
		return nil, err
	}
	if status == nil {
		return nil, nil
	}
	return &SystemProxyStatus{
		Available: status.Available,
		Enabled:   status.Enabled,
	}, nil
}
func (h handlerAdapter) SetSystemProxyEnabled(enabled bool) error {
	return h.handler.SetSystemProxyEnabled(enabled)
}

func (h handlerAdapter) WriteDebugMessage(message string) {
	h.handler.WriteDebugMessage(message)
}

// BoxService is the sing-box service handle.
type BoxService interface {
	// Start starts the internal command server (socket)
	Start() error
	// Close releases all resources
	Close() error
	// StartService starts or reloads the tunnel service with the given config
	StartService(configContent string) error
	// StopService stops the tunnel service
	StopService() error
	// WriteLog writes a log line (level: 0=error 1=warn 2=info 3=debug)
	WriteLog(level int32, message string)
	// SetError marks the service error state
	SetError(message string)
}

type boxServiceImpl struct {
	server *CommandServer
}

// NewService creates a sing-box service.
// handler may be nil; platformInterface is the platform interface
// implementation (TUN setup, socket protection, etc.).
func NewService(handler ServiceHandler, platformInterface PlatformInterface) (BoxService, error) {
	server, err := NewCommandServer(handlerAdapter{handler: handler}, platformInterface)
	if err != nil {
		return nil, err
	}
	return &boxServiceImpl{server: server}, nil
}

func (s *boxServiceImpl) Start() error {
	return s.server.Start()
}

func (s *boxServiceImpl) Close() error {
	s.server.Close()
	return nil
}

func (s *boxServiceImpl) StartService(configContent string) error {
	return s.server.StartOrReloadService(configContent, &OverrideOptions{})
}

func (s *boxServiceImpl) StopService() error {
	return s.server.CloseService()
}

func (s *boxServiceImpl) WriteLog(level int32, message string) {
	s.server.WriteMessage(level, message)
}

func (s *boxServiceImpl) SetError(message string) {
	s.server.SetError(message)
}
