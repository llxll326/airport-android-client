@echo off
REM ============================================================
REM  build_libbox.bat — 构建 sing-box 核心绑定 libbox.aar
REM
REM  前置条件：
REM    1. Go 1.24+        （go version 验证）
REM    2. Android SDK     （ANDROID_HOME 环境变量，含 NDK）
REM  产物：app\libs\libbox.aar
REM ============================================================
setlocal

set "GO_BIN=%USERPROFILE%\go\bin"

where go >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Go，请先安装 https://go.dev/dl/
    exit /b 1
)

if "%ANDROID_HOME%"=="" (
    echo [错误] 未设置 ANDROID_HOME 环境变量
    exit /b 1
)

echo [1/3] 检查 gomobile...
if not exist "%GO_BIN%\gomobile.exe" (
    echo       安装 gomobile...
    go install golang.org/x/mobile/cmd/gomobile@latest || exit /b 1
    "%GO_BIN%\gomobile.exe" init || exit /b 1
)

echo [2/3] 构建 libbox.aar（首次约 5-15 分钟）...
cd /d "%~dp0..\core"
REM -checklinkname=0：pidfd_android.go 通过 go:linkname 引用 os.checkPidfdOnce，Go 1.23+ 交叉编译需关闭检查
REM -tags：启用 uTLS 指纹（含 reality）/ QUIC（tuic、hysteria2）/ gRPC 传输 / WireGuard / gvisor 协议栈
REM 注意：with_reality_server 已并入 with_utls、with_ech 已迁移 stdlib，二者不可再传
"%GO_BIN%\gomobile.exe" bind -v -androidapi 21 -javapkg libbox -tags with_quic,with_grpc,with_dhcp,with_wireguard,with_utls,with_gvisor -ldflags="-checklinkname=0" -o ..\app\libs\libbox.aar .\libbox || exit /b 1

echo [3/3] 完成：app\libs\libbox.aar
endlocal
