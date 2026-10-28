# 发布指南

本文面向维护者，说明签名配置、GitHub Actions 与版本发布流程。

## 版本规则

- 每次发布批次将 `versionCode` 加 1。
- `versionName` 使用 `MAJOR.MINOR.PATCH-<短哈希>`：较大功能变更升 minor，修复与文档类变更升 patch。
- 短哈希取发布提交之前的最后一个内容提交，因为提交无法包含自身的哈希。
- 发版提交使用 `chore(release): 发布 <versionName>`。

版本号位于 `app/build.gradle.kts` 的 `defaultConfig`。

## Release 正文

- 标签触发时，工作流读取上一个 `v*` 标签到当前标签之间的提交，按 `feat/fix/perf/refactor/docs/test/build/ci/style` 分组为中文小节，空组省略，末尾附 compare 链接。
- `chore(release):` 发版提交会被排除；直接提交到 master 的改动也会进入正文，因此提交信息保持单行 `type(scope): 摘要`。
- 没有任何条目时写入「常规维护与问题修复」。
- 关于页更新弹窗展示同一份正文的纯文本版本。

## 本地签名

1. 生成自己的 keystore（只在可信机器上保存）：

   ```bash
   mkdir -p keystore
   keytool -genkeypair -v \
     -keystore keystore/release.jks \
     -alias release \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000
   ```

2. 复制配置模板：

   ```bash
   cp keystore.properties.example keystore.properties
   ```

3. 填入 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。相对路径按仓库根目录解析。

4. 构建：

   ```bash
   ./gradlew :app:assembleRelease
   ```

`keystore/`、`*.jks`、`keystore.properties` 均已被 `.gitignore` 排除。四项签名配置缺一时仍可构建，但产物是未签名包。

请立即备份 keystore 与密码。密钥丢失后无法覆盖安装或升级已发布版本，只能更换包名或让用户卸载重装。

## CI 发布

仓库的 [Release 工作流](../.github/workflows/release.yml) 在推送 `v*` 标签时执行，也可以在 Actions 页面手动触发。

需要在仓库 Settings → Secrets and variables → Actions 中配置：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | keystore 文件的 Base64 编码，例如 `base64 -w0 keystore/release.jks` |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 密钥别名 |
| `RELEASE_KEY_PASSWORD` | 密钥密码 |
| `QIEKJ_APP_SECRET` | 构建所需的 API 凭据 |
| `QIEKJ_LOCATION_SECRET` | 定位风控凭据；可与 `QIEKJ_APP_SECRET` 相同 |

缺少任意一项时工作流会直接失败，避免发布一个无法登录或无法联网的安装包。

工作流会：

1. 校验并解码签名与 API 凭据。
2. 一次构建签名 Release 与 diagnostic APK。
3. 使用 `apksigner` 校验两个 APK，并比较证书 SHA-256。
4. 使用 `aapt2` 校验两者同包名、同 versionCode，且 diagnostic 的 versionName 为 `<versionName>-diagnostic`。
5. 将 APK 命名为 `fuck-pangguai-<versionName>.apk` 与 `fuck-pangguai-<versionName>-diagnostic.apk`，上传同名（去掉 `.apk`）的 artifact，内含双 APK、`mapping.txt` 与 `mapping-diagnostic.txt`。
6. 标签触发时创建 GitHub Release 并用自动生成的正文，正文末尾追加“诊断包”说明；手动触发只上传 artifact。

### 诊断包

- 与正式版同包名、同 versionCode、同正式签名，可覆盖安装正式版，也可回退正式版；应用数据保留。
- 覆盖安装会结束进程，接水或支付进行中禁止安装。
- `versionName` 带 `-diagnostic`，应用名带“（诊断）”；记录 Debug 级事件与更大的响应预览。
- 诊断包未通过签名/包名校验时，工作流会失败，不会发布未验签产物。
- 诊断包日志可能包含设备名称、订单号与脱敏响应片段，分享前需检查；Release 正文会提示这一点。

本地无 keystore 时 diagnostic 仍可编译，但不能发布，也不应安装覆盖正式版。

## 创建发布

```bash
git tag v1.1.0
git push origin v1.1.0
```

推送前确认：

- 版本号、标签与 Release 标题一致。
- 单元测试、lint 与 Debug 构建通过。
- 没有提交 `secrets.properties`、`keystore.properties`、keystore 或任何凭据。
- 文档中的版本号与已知限制已同步。

## 签名校验

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

也可以对 CI 下载的 APK 执行同样命令。请勿安装来源不明的重打包版本。

## 凭据与密钥安全

- 仓库不提供官方客户端凭据，CI 仅从 Secret 注入。
- 凭据会随 APK 分发并可被提取，不能视为绝对秘密；泄露后及时轮换。
- keystore 与密码只保存在维护者的可信设备和 GitHub Secrets 中。
- 不要在 Issue、PR、日志或截图中粘贴凭据、token、手机号或订单号。
- Release 与 diagnostic 产物默认开启 R8；上传的 `mapping.txt`/`mapping-diagnostic.txt` 只用于崩溃分析，不要附加敏感信息。
