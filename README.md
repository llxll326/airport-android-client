# 机场（Airport）

基于 **sing-box** 的安卓代理客户端。支持通过 **订阅 URL** 导入节点（vless / vmess / trojan / shadowsocks / hysteria2 / tuic），一键连接（TUN 隧道），切换节点。

## 快速开始（已有 APK）

1. 将 `app/build/outputs/apk/debug/app-debug.apk` 拷贝到手机
2. 手机上允许"安装未知来源应用"，点击安装
3. 打开 App → 「订阅」页 → 右下角 **+** → 粘贴订阅 URL → 添加
4. 「节点」页选择节点 → 打开开关 → 首次会弹出 **VPN 授权**，允许即可

> 已连接安卓设备时也可直接：`adb install app-debug.apk`

## 从源码构建

### 前置环境（首次一次性准备）

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 构建 Gradle 工程 |
| Go | 1.24+ | 编译 sing-box 核心 |
| Android SDK | API 35 | 含 NDK（gomobile 交叉编译需要） |

本仓库开发环境已将工具链安装在 `%USERPROFILE%\.devtools\`（JDK 17 / gradle 8.13 / android-sdk），
`local.properties` 指向 `sdk.dir=C:\Users\<用户>\.devtools\android-sdk`。
如果你已有 Android Studio，直接改 `local.properties` 指向自己的 SDK 即可。

### 构建步骤

```bat
:: 1. 构建 sing-box 核心绑定（首次 5-15 分钟，产物 app\libs\libbox.aar）
scripts\build_libbox.bat

:: 2. 打包 APK（产物 app\build\outputs\apk\debug\app-debug.apk）
gradlew.bat assembleDebug
```

### 构建产物

| 产物 | 路径 |
|---|---|
| APK | `app/build/outputs/apk/debug/app-debug.apk`（arm64-v8a，约 80MB） |
| 核心绑定 | `app/libs/libbox.aar`（Go 编译，勿删） |

## 单元测试

解析器测试位于 `app/src/test/`（ShareLinkParser / SubscriptionParser）。

> ⚠️ Windows 中文路径注意：Gradle 测试 worker 的 classpath 文件以 UTF-8 写入、按系统编码（GBK）读取，
> 项目路径含中文会导致测试类找不到（`ClassNotFoundException`）。APK 构建不受影响。
> 如需在本机跑单测，将项目复制到纯 ASCII 路径（如 `D:\airport`）后执行
> `gradlew.bat :app:testDebugUnitTest`。

## 目录结构

```
机场/
├── PLAN.md                  # 设计文档（架构/模块/迭代路线）
├── core/                    # Go 绑定：sing-box experimental/libbox 薄封装
│   └── libbox.go            #   Setup / NewService / BoxService（gomobile bind）
├── scripts/build_libbox.bat # AAR 构建脚本
└── app/src/main/java/com/airport/app/
    ├── subscription/        # 订阅抓取 + 解析（base64/Clash YAML/分享链接/sing-box JSON）
    ├── data/                # Room 实体/DAO、DataStore 设置
    ├── core/                # ConfigBuilder、TunService（VpnService+PlatformInterface）、CoreController
    └── ui/                  # Compose 界面（节点/订阅/设置）
```

## 功能状态（MVP 0.1.0）

- ✅ 订阅 URL 导入 / 手动更新 / 删除
- ✅ 六种协议解析：vless、vmess、trojan、ss、hysteria2、tuic（+Clash YAML：socks5/http）
- ✅ 一键连接 / 断开（VPN 授权流程、前台服务通知）
- ✅ 节点切换（运行中点击节点自动重连）
- ✅ 订阅信息展示：节点数、更新时间、流量/到期（Subscription-Userinfo）
- ⏳ 待迭代：延迟测速、定时更新、规则路由、分应用代理、SSR/WireGuard（见 PLAN.md）

## 许可

GPL-3.0（核心依赖 sing-box，其许可为 GPL-3.0）。
