# Codex AI for Android

<div align="center">
  <p><b>Codex CLI 全栈安卓化 — 让 AI 编码代理在你的 Android 设备上原生运行</b></p>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen" alt="Platform">
    <img src="https://img.shields.io/badge/Kotlin-2.2.21-purple" alt="Kotlin">
    <img src="https://img.shields.io/badge/AGP-8.13.2-blue" alt="AGP">
  </p>
</div>

---

## 项目介绍

**Codex AI for Android** 是基于 [Operit](https://github.com/AAswordman/Operit) 底座进行深度改造的 Codex CLI 安卓化项目。

### 核心目标
- **取 Operit 之长**：保留其强大的 Android 系统工具层（Shell/终端/Shizuku/MCP/文件系统）
- **去 Operit 之短**：移除 Operit 特有的聊天/角色卡/工作流等业务逻辑
- **实现 Codex 原生体验**：以 Codex CLI 的 Web UI 为核心界面，通过 WebSocket + MCP 桥接 Android 原生能力

### 项目定位
不是 Operit 的简单复刻，而是为 Codex CLI 打造一个完整的 Android 运行环境，让 Codex 不仅可以在电脑端运行，在手机/平板上也能获得完整的 Agent 体验。

---

## 架构

| 层 | 技术 | 说明 |
|----|------|------|
| **UI 层** | WebView + Codex Web UI | Codex CLI 官方 Web 界面，内嵌 WebView |
| **通信层** | OkHttp WebSocket + JSON-RPC | 与 Codex exec-server 双向通信 |
| **MCP 层** | Android System MCP Bridge | 将 Android 系统功能暴露为 MCP 工具 |
| **系统层** | Shizuku / Root / Shell | 权限提升、命令行执行 |
| **平台层** | Terminal / FileSystem / MediaProjection | Android 原生能力封装 |
| **引擎层** | Codex CLI (aarch64) | OpenAI Codex AI 编码代理 |

### 保留的 Operit 底座能力

- ✅ Shell 执行器（Standard/Root/Shizuku/ADB）
- ✅ 终端模拟器
- ✅ MCP 框架（本地服务器 + 插件部署）
- ✅ 文件系统工具（SAF + Linux 文件系统）
- ✅ 剪贴板 / 网络 / 系统信息
- ✅ 屏幕捕获与 MediaProjection
- ✅ Shower 设备互联
- ✅ QuickJS JavaScript 引擎
- ✅ ObjectBox 记忆存储
- ✅ OCR / QR 码识别
- ✅ APK 构建/签名工具
- ✅ Filament 3D 渲染

---

## 快速开始

### 构建 APK

```bash
# 确保已配置 local.properties
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### local.properties 配置

```properties
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
```

### 运行要求

- Android 8.0 (API 26) 及以上
- arm64-v8a 架构
- Shizuku 或 Root（可选，用于增强权限）
- 网络连接（用于下载 Codex CLI 二进制）

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 2.2.21 |
| Android Gradle Plugin | 8.13.2 |
| Jetpack Compose BOM | 2026.02.01 |
| Shizuku | 13.1.5 |
| libsu (Root) | 6.0.0 |
| OkHttp | 4.12.0 |
| MCP SDK | 1.1.0 |
| ObjectBox | 4.0+ (stubs) |

---

## 参考项目

- [OpenAI Codex CLI](https://github.com/openai/codex) — Codex 编码代理
- [CLI-Anything](https://github.com/HKUDS/CLI-Anything) — Agent-Native CLI 市场 (41k⭐)
- [Mobile-MCP](https://github.com/mobile-next/mobile-mcp) — 移动设备 MCP 服务器 (5k⭐)
- [Android MCP Server](https://github.com/minhalvp/android-mcp-server) — Android ADB MCP (755⭐)
- [wshobson/agents](https://github.com/wshobson/agents) — 多平台 Agent 插件市场 (36k⭐)

---

## License

本项目基于 [GNU LGPLv3](LICENSE) 许可证发布。
基于 [Operit](https://github.com/AAswordman/Operit) (LGPLv3) 改造。
