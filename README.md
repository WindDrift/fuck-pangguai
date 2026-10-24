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

## 签名与发布

Release 签名优先读取根目录 `keystore.properties`，文件不存在时回退到环境变量，便于 CI 注入。四项缺一不可；未配置或配置不全时仍可构建，但产物为未签名包。

### 本地

本仓库已生成 `keystore/release.jks` 与 `keystore.properties`，两者均被 `.gitignore` 排除。**请立即备份这两个文件及密码**：密钥丢失后无法再覆盖安装或升级已发布版本。

```bash
export JAVA_HOME=/c/Users/*****/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2
./gradlew :app:assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

`storeFile` 可写相对仓库根目录的路径或绝对路径。换机器时复制 `keystore/` 与 `keystore.properties`，或改用下面四个环境变量。

### GitHub Actions

在仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 keystore/release.jks` 的完整输出 |
| `RELEASE_STORE_PASSWORD` | `keystore.properties` 中的 `storePassword` |
| `RELEASE_KEY_ALIAS` | `keystore.properties` 中的 `keyAlias`（当前为 `release`） |
| `RELEASE_KEY_PASSWORD` | `keystore.properties` 中的 `keyPassword` |

推送 `v*` 标签会触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：解码密钥、构建签名 APK、用 `apksigner` 校验、上传 artifact，并创建附带 APK 与 `mapping.txt` 的 GitHub Release。也可在 Actions 页手动触发，此时只上传 artifact，不创建 Release。

```bash
git tag v0.1.0
git push origin v0.1.0
```

> 当前仓库尚未配置远程地址；添加 GitHub 远程并推送后才能使用 Actions。

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
