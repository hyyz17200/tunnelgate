# TunnelGate

TunnelGate 是一个用于 **Android Root 设备** 的轻量级 LSPosed 模块与设置界面组合，用来根据 **当前网络类型与 Wi‑Fi SSID 策略** 自动开启或关闭代理/隧道软件。

本项目最初围绕 V2RayTun 进行了适配，但架构上保留了 **通用 Shell 后端**，因此也可以扩展到其他代理软件。

---

## 功能特性

- 仅 Hook `System Framework (android)`，不需要常驻自动化平台。
- 基于 `ConnectivityManager.registerDefaultNetworkCallback()` 监听默认网络变化。
- 支持按以下场景分别设置动作：
  - 蜂窝网络
  - 黑名单 Wi‑Fi
  - 非黑名单 Wi‑Fi
  - 无网络 / 未验证网络
- 支持全局 **自动化总开关**。
- 支持 **开机后延迟启动策略** 与 **防抖时间**。
- 支持两种控制后端：
  - **通用 Shell 命令**
  - **V2RayTun Tasker 插件**（静默执行，推荐）
- GUI 内置：
  - SU 状态检测
  - LSPosed / 系统 Hook 在线状态检测
  - 简单日志窗口

---

## 为什么要做这个项目

MacroDroid、Tasker 等自动化工具功能很强，但很多用户只需要“按网络环境自动切换代理”这一件事。

TunnelGate 的目标是：

- 尽量少装额外常驻服务
- 避免复杂自动化规则
- 在 Root + LSPosed 环境中，提供一个足够轻量、直接、可调试的方案

---

## 工作原理

### 1. 系统侧

LSPosed 模块注入 `android` 进程中的 `SystemServer`，在系统服务就绪后注册网络回调，监听默认网络变化。

### 2. 策略判断

当网络变化时，模块读取当前配置并决定应该执行：

- `start`
- `stop`
- 或保持当前状态

### 3. 应用侧执行

系统侧不会直接去启动目标代理 App，而是向 TunnelGate 自己的 `ControlReceiver` 发送显式广播。

`ControlReceiver` 再根据当前选择的后端执行：

- Shell 命令
- V2RayTun Tasker 插件广播

这种结构有两个好处：

- 系统侧 Hook 更小更稳
- 具体控制方式可替换，可扩展到其他代理软件

---

## 控制后端说明

## A. 通用 Shell 命令

适合任意代理软件，只要它能通过 shell / am / cmd / broadcast 等方式被控制。

你可以在 GUI 中分别填写：

- Start 命令
- Stop 命令

示例（V2RayTun deeplink，仅作兼容保留）：

```bash
am start -W -a android.intent.action.VIEW -d v2raytun://control/start -p com.v2raytun.android
am start -W -a android.intent.action.VIEW -d v2raytun://control/stop -p com.v2raytun.android
```

> 注意：某些 App 的 deeplink 会把主界面带到前台，因此不一定适合长期使用。

## B. V2RayTun Tasker 插件

这是当前推荐方案。

V2RayTun 暴露了 Tasker / Locale 插件接口，TunnelGate 可以直接：

- 调起 `TaskerActivity` 捕获动作配置
- 之后静默向 `TaskerReceiver` 发送 `FIRE_SETTING` 广播

这样就能避免 deeplink Activity 带前台的问题。

---

## 安装要求

- Android 8.0+
- 已 Root
- 已安装并正常运行的 **LSPosed**
- 建议使用 KSU / Magisk 提供 `su`

---

## 安装步骤

1. 用 Android Studio 编译并安装 APK。
2. 打开 **LSPosed**，启用 TunnelGate 模块。
3. 作用域只勾选：
   - `System Framework (android)`
4. 重启手机。
5. 打开 TunnelGate。
6. 查看 GUI 顶部诊断信息，确认：
   - SU 状态正常
   - LSPosed 系统 Hook 在线

---

## 使用说明

### 通用设置

在 GUI 中可以设置：

- 是否启用自动化策略
- 控制后端
- 蜂窝 / 黑名单 Wi‑Fi / 非黑名单 Wi‑Fi / 无网络时的动作
- 是否仅在 `VALIDATED` 网络下应用 Wi‑Fi / 蜂窝规则
- SSID 黑名单
- 防抖时间
- 开机延迟时间

### V2RayTun Tasker 模式配置步骤

1. 将控制后端切换到 **V2RayTun Tasker 插件**。
2. 点击 **捕获 Start**。
3. 在 V2RayTun 打开的 Tasker 配置页里保存“启动”动作。
4. 回到 TunnelGate。
5. 点击 **捕获 Stop**。
6. 在 V2RayTun 中保存“停止”动作。
7. 点击 **保存**。

之后系统自动切换时，将静默执行，不会再把 V2RayTun 主界面拉到前台。

---

## GUI 诊断说明

TunnelGate 启动时会检查两件事：

### SU 状态

通过执行 `su -c id` 检查是否已授权。

- 正常：显示已授权
- 异常：说明当前没有拿到 Root，或 Root 管理器未放行

### LSPosed 状态

GUI 会向系统侧 Hook 发送状态查询广播。

如果模块工作正常，系统侧会返回：

- 是否在线
- 是否已经开始监听网络
- 最近一条状态消息

如果没有返回，通常表示：

- 模块未启用
- 作用域配置错误
- 系统尚未完成启动

---

## 日志说明

GUI 底部提供了一个简单日志窗口。

日志来源包括：

- 系统 Hook 事件
- 应用侧控制执行结果
- Tasker 动作执行摘要
- 错误信息

这比单纯依赖 `logcat` 更方便分享和排查问题。

---
## 已知限制

- 当前 Tasker 静默模式是专门为 **V2RayTun** 适配的。
- 如果 V2RayTun 未来更改其 Tasker 组件名或 Bundle 结构，可能需要更新代码。
- GUI 中的 LSPosed 在线状态依赖系统侧 Hook 回报，如果系统刚启动完，状态可能会略有延迟。
- 通用 Shell 模式的可靠性取决于目标代理 App 自身是否提供稳定的控制接口。
