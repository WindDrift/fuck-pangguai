# 去他妈的胖乖生活（fuck-pangguai）

非官方、极简的校园饮水机客户端。目标是把“扫码/碰 NFC → 确认 → 解锁 → 接水 → 设备结算 → 支付状态”压缩成最短路径，不包含广告、推送、IM、会员和社区模块。

> 本项目与胖乖生活、学校及设备运营方均无关联。直接调用官方接口，不绕过定位、实名或风控校验；使用风险由使用者承担。仓库默认私有，公开前请自行评估用户协议与密钥暴露风险。

## 当前状态

- M0–M5 已实现：工具链、签名/登录、扫码/NFC、确认/解锁、前台服务/结算、历史/设置/诊断。
- M6 已完成 release 构建与 R8；**真实设备验收尚未完成**（当前 `adb` 无设备）。
- BLE 设备第一版按设计阻断，仅做探测提示。
- 后付费自动扣款是静态推断，需用真实账单核对。

## 环境与构建

- JDK 21
- Android SDK：compileSdk/targetSdk 37，minSdk 31
- 本机 SDK 示例路径：`/d/Android/Sdk`（`local.properties` 中配置 `sdk.dir`）

```bash
export JAVA_HOME=/c/Users/*****/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

Miuix 使用 `0.9.2`。当前构建从 Maven Central 解析；如果坚持 GitHub Packages，需在 `settings.gradle.kts` 添加 GPR 仓库并在 `~/.gradle/gradle.properties` 配置 `gpr.user` / `gpr.key`（`read:packages`）。

## 签名

1. 复制 `keystore.properties.example` 为 `keystore.properties`。
2. 填入 `storeFile/storePassword/keyAlias/keyPassword`。
3. 执行 `./gradlew :app:assembleRelease`，输出签名 APK。
4. `keystore.properties` 与 keystore 已被 `.gitignore` 排除，不要提交。

未配置签名时，release 产物为 `app-release-unsigned.apk`。

## 调试入口

- Debug 构建的登录页提供“粘贴 token”导入。
- 设置页点击版本号 **7 次**进入隐藏诊断页：设备探测、`isBluetooth/deviceErrorCode/payment/payModel/tradeMain`、设备 JSON、NFC 映射、请求日志、cURL 复制。
- Debug 构建默认开启“演练模式”：只展示请求，不真实调用 `water/unlock`。真机解锁前在设置里关闭。
- “一键解锁”默认关闭；同一设备首次确认成功后，后续才会启用。

## 真机验收清单

1. 连接设备，确认 `adb devices` 可见。
2. 安装 Debug 包；完成免责声明、短信登录、自动登录。
3. 扫码真实设备二维码；在诊断页确认 `isBluetooth`、故障码、套餐和支付通道。
4. 碰一次 NFC 标签；确认 `t` 解析、扫码绑定、重启后映射仍存在。
5. 保持演练模式，走一次确认页；核对 `water/unlock` 参数、签名头和 `promotions`。
6. 关闭演练模式，用最小套餐真实解锁；记录 `unlock.orderNo/msgId`、`waterSync.identify`、`afterPay.orderNo/payType`，核对账单。
7. 接水时切后台，确认前台服务通知、设备结算、支付状态和“使用中”页更新。
8. 连点开始、飞行模式、杀进程重开，确认没有第二次 `water/unlock` 或 `afterPay/creating`。
9. 验证 BLE=1、4204、设备故障、定位拒绝、登录过期均会阻断并提示。
10. 导出 CSV，确认 Excel 打开中文不乱码。

## 已知限制

- BLE 解锁未实现；`isBluetooth=1` 的设备第一版不可解锁。
- 自动扣款/补缴路径未动态验证；失败时显示“去原 App 核对”，不自动重试。
- 直连官方 API，`appSecret` 在 APK 中；分发范围应保持可信。
- 设备 ID 使用 Android ID 哈希替代 OAID，服务端风控行为需实测。
- NFC `t → SN` 映射需手动绑定，不同学校/标签格式可能需要调整。
