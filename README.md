# 去他妈的胖乖生活（fuck-pangguai）

非官方、极简的校园饮水机客户端。目标是把“扫码 / 碰 NFC → 确认 → 解锁 → 接水 → 设备结算 → 支付状态”压缩成最短路径，不包含广告、推送、IM、会员和社区模块。

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/12)
[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/WindDrift/fuck-pangguai/releases)

> [!WARNING]
> 本项目与胖乖生活、学校及设备运营方均无关联。它直接调用官方接口完成扫码、解锁和支付状态查询，不绕过定位、实名或风控校验，使用风险由使用者承担。
>
> 仓库不提供官方客户端凭据。未配置凭据时应用可以编译，但登录和扫码等联网功能不可用。

当前 1.1.0：核心扫码、解锁、接水与结算链路已在真机验证；BLE 与自动扣款仍有明确限制。

## 功能

- 扫码、相册识别、NFC 与手动输入，兼容常见的设备二维码格式。
- 手机号 + 验证码登录，登录凭证保存在本机并使用 Android Keystore 加密。
- 确认页展示设备信息、套餐与支付通道，支持演练模式和二次确认后的一键解锁。
- 前台服务持续同步接水状态、设备结算与支付结果，切到后台也能收到本地通知。
- 接水历史仅保存在本机，支持查看、删除和导出 UTF-8 CSV。
- Miuix 界面，支持跟随系统深浅色与 Monet 动态取色。
- 无广告、无第三方统计、无推送 SDK、无 IM；接水状态只使用本地前台服务通知。

## 下载与安装

- 系统要求：Android 12（API 31）及以上。
- 首个公开版本发布后，可从 [GitHub Releases](https://github.com/WindDrift/fuck-pangguai/releases) 下载 APK。
- 自行构建的版本与官方发布包签名不同，不能直接覆盖安装。

## 快速构建

需要 JDK 21 与 Android SDK 37。

```bash
cp secrets.properties.example secrets.properties
# 在 secrets.properties 中填入你有权使用的 API 凭据
./gradlew :app:assembleDebug
```

未配置凭据时仍可编译，但登录与联网功能不可用。完整环境、测试和发布签名说明见[构建文档](docs/build.md)与[发布文档](docs/release.md)。

## 界面预览

截图待补充：扫码、确认、使用中。

<!-- 添加真实截图后，将下方注释替换为图片：
<p align="center">
  <img src="docs/assets/screenshots/scan.png" width="240" alt="扫码页">
  <img src="docs/assets/screenshots/confirm.png" width="240" alt="确认页">
  <img src="docs/assets/screenshots/using.png" width="240" alt="使用中页面">
</p>
-->

## 已知限制

- BLE 解锁尚未实现，`isBluetooth=1` 的设备当前不可解锁。
- 后付费自动扣款路径未经动态验证，支付状态未知时请回到原 App 核对。
- 需要自备可用的 API 凭据；仓库不提供，也不说明获取方式。
- NFC `t → SN` 映射需要首次手动绑定，不同学校或标签格式可能需要调整。

## 文档

| 文档 | 内容 |
| --- | --- |
| [文档索引](docs/README.md) | 按用户、开发者与维护者分类的入口 |
| [使用指南](docs/usage.md) | 登录、扫码、NFC、确认、接水、历史与设置 |
| [构建指南](docs/build.md) | 环境、凭据配置、构建、测试与排错 |
| [发布指南](docs/release.md) | 签名、CI Secret、版本与发布流程 |
| [调试指南](docs/debug.md) | 演练模式、隐藏诊断页、请求日志与 NFC 映射 |
| [架构说明](docs/architecture.md) | 技术栈、模块职责、数据流与存储 |
| [测试与验收](docs/testing.md) | 单元测试、真机验收清单与回归范围 |
| [常见问题](docs/faq.md) | 构建、登录、设备、支付与数据问题 |
| [隐私说明](docs/privacy.md) | 权限、数据清单、本地存储与网络行为 |
| [免责声明](docs/disclaimer.md) | 非官方声明、使用风险与责任边界 |

## 隐私

应用自身的网络请求只发往官方服务，不包含广告或统计 SDK。登录凭证使用 Android Keystore 加密，接水记录保存在本机，`allowBackup=false`。权限与数据处理细节见[隐私说明](docs/privacy.md)。

## 贡献与反馈

欢迎提交 Issue 与 PR。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并注意不要提交凭据、token、逆向材料或官方反编译代码。

## 免责声明

本项目与胖乖生活、学校及设备运营方无关联，不绕过定位、实名或风控校验。账号、支付、设备与学校规定相关风险由使用者自行承担。完整声明见[免责声明](docs/disclaimer.md)。

## 许可证

[GPL-3.0](LICENSE)。Copyright (C) 2026 WindDrift。本项目按“现状”提供，不附带任何明示或暗示的担保。
