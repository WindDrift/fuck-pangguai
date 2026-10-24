# 构建指南

本文说明如何从源码构建 Debug 与 Release 包，以及如何配置本地 API 凭据。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | 21 |
| Android SDK | Platform 37、Build-Tools 37.0.0 |
| Gradle | 使用仓库自带 Wrapper |
| minSdk | 31（Android 12） |
| compileSdk / targetSdk | 37 |
| Kotlin / AGP | 2.4.20 / 9.2.1 |

设置 `JAVA_HOME` 指向 JDK 21，并确保 Android SDK 路径可用。可以在根目录 `local.properties` 写入：

```properties
sdk.dir=/path/to/Android/Sdk
```

也可以通过 `ANDROID_HOME` 环境变量提供 SDK 路径。`local.properties` 已被 `.gitignore` 排除，不要提交。

## 获取源码

```bash
git clone https://github.com/WindDrift/fuck-pangguai.git
cd fuck-pangguai
```

## 配置 API 凭据

应用调用官方接口时需要构建者提供凭据。仓库不包含、也不提供获取方式。

1. 复制模板：

   ```bash
   cp secrets.properties.example secrets.properties
   ```

2. 填入有权使用的值：

   ```properties
   appSecret=your-app-secret
   locationSecret=your-location-secret
   ```

3. `locationSecret` 留空时会回退到 `appSecret`。`secrets.properties` 已被忽略，切勿提交。

CI 或其他无文件环境使用环境变量：

```bash
export QIEKJ_APP_SECRET=your-app-secret
export QIEKJ_LOCATION_SECRET=your-location-secret
```

构建脚本会把这些值注入 `BuildConfig`。**未配置时仍可编译和运行，但登录、扫码和接水等联网功能会失败**，这不是应用 Bug。

发布签名使用另一组配置（`keystore.properties`），与 API 凭据互不影响，详见[发布指南](release.md)。

## 构建命令

Linux / macOS：

```bash
./gradlew :app:assembleDebug
```

Windows：

```bat
gradlew.bat :app:assembleDebug
```

产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`
- 未配置签名时：`app/build/outputs/apk/release/app-release-unsigned.apk`

Release 默认开启 R8 与资源压缩。缺少签名配置不会阻止构建，只会产出未签名包。

## 测试与静态检查

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

全部单元测试均为 JVM 测试，不依赖真机。涉及相机、NFC、定位、前台服务和支付的路径需要真机验证，见[测试与验收](testing.md)。

常用组合：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 依赖说明

- UI 使用 Miuix `0.9.4`。
- 依赖从 Maven Central 与 Google Maven 解析，不需要 GitHub Packages 凭据。
- 网络层使用 OkHttp、Retrofit 与 kotlinx.serialization；相机使用 CameraX，二维码解码使用 ZXing。
- 版本集中在 `gradle/libs.versions.toml`，升级依赖时优先修改这里。

## 常见构建问题

### 找不到 Android SDK

确认 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 指向已安装 Platform 37 的 SDK。路径使用本机实际位置，不要照搬他人的示例。

### JDK 版本不匹配

Gradle 与 Android 插件需要 JDK 21。执行 `java -version` 确认版本；IDE 中还需检查 Gradle JDK 设置。

### 构建成功但登录失败

检查 `secrets.properties` 或 `QIEKJ_*` 环境变量是否已配置，并重新执行一次构建。修改凭据后建议先清理再构建：

```bash
./gradlew clean :app:assembleDebug
```

### 构建配置缓存了旧值

如果启用了 Gradle 配置缓存，修改 `secrets.properties` 后可使用：

```bash
./gradlew --no-configuration-cache clean :app:assembleDebug
```

### 资源或符号错误

先尝试 `./gradlew clean`，确认没有混用不同 JDK 或未同步的生成文件。仍无法解决时附完整报错和 `./gradlew --version` 输出提交 Issue。

## 相关文档

- [发布指南](release.md)
- [调试指南](debug.md)
- [测试与验收](testing.md)
