# AGENTS.md

本文件面向参与本仓库的自动化代理与贡献者，只记录通用工程规范。维护者的本机习惯不写入仓库。

## 项目概览

- 非官方 Android 校园饮水机客户端，Kotlin + Jetpack Compose + Miuix。
- 单模块 `:app`，包名 `io.github.winddrift.fuckpangguai`。
- `minSdk 31`，`compileSdk` / `targetSdk 37`，JDK 21。
- 网络使用 OkHttp + Retrofit，存储使用 DataStore 与 SQLite，扫码使用 CameraX + ZXing。

公开文档不得包含接口地址、请求头、签名算法、官方反编译代码或真实凭据。

## 构建与测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

- 本地凭据放在 `secrets.properties`（参考 `secrets.properties.example`），CI 使用 `QIEKJ_*` 环境变量。
- 签名配置放在 `keystore.properties`（参考 `keystore.properties.example`）。
- 不得提交 `secrets.properties`、keystore、token 或任何真实凭据。
- 改动涉及相机、NFC、定位、前台服务或支付时，说明哪些部分只做了 JVM 测试、哪些需要真机验证。

## 提交规范

提交信息必须为单行中文：

```text
type(scope): 摘要
```

类型：

`feat`、`fix`、`refactor`、`perf`、`style`、`docs`、`test`、`build`、`ci`、`chore`、`revert`

固定 scope：

`app`、`ui`、`auth`、`scan`、`confirm`、`water`、`device`、`net`、`history`、`settings`、`build`、`ci`、`deps`、`docs`、`test`、`release`

如需新增 scope，在提交或 PR 中说明理由。

示例：

```text
fix(ui): 修复诊断页底部系统栏间距
docs: 重写 README 并补充公开文档
chore(release): 发布 1.1.0-abc1234
```

## 版本规则

- 每个改动批次将 `versionCode` 加 1。
- `versionName` 使用 `MAJOR.MINOR.PATCH-<短哈希>`；较大功能变更升 minor，修复与文档类变更升 patch。
- 短哈希取发布提交之前的最后一个内容提交，因为提交无法包含自身哈希。
- 版本变更放在独立发版提交中，不与功能或文档改动混在一起。

## 工作方式

- 需求模糊时先问少量高杠杆问题，不要自行扩大范围。
- 多步骤任务使用任务列表跟踪，按里程碑提交；每个提交应可编译。
- 先读现有实现与约定，优先复用已有组件，不重复造轮子。
- 以真实构建、测试或产物验收，不以“看起来正确”代替验证。
- 报告结果时说明实际执行的检查与未验证部分。
- 发现文档与行为不一致时，同步修改文档。
- 不主动 push、打标签、创建 Release 或修改远端配置，除非用户明确要求。
- 强制推送、历史重写、删除密钥或清空数据等破坏性操作，先说明影响并把命令交给用户确认。

## 公开边界

以下内容禁止提交或写入公开文档：

- `report/`、`reverse/`、`plan/` 中的逆向、反编译或提取材料。
- 官方客户端代码、接口清单、签名算法、请求头或抓包记录。
- API 凭据、token、手机号、订单号、keystore、个人路径。
- 未脱敏的日志、截图与设备完整 JSON。

提交前至少检查：

```bash
git status
git diff --cached
git grep -nE 'appSecret|keystore|/Users/|C:\\\\Users'
```

忽略示例文件与文档中的字段名说明，确保没有真实值。

## 文档与语言

- README 只保留基础信息；详细内容放入 `docs/`。
- 代码注释、提交信息、PR 描述与文档使用中文。
- 代码标识符与第三方 API 名称保持英文。
- 新增功能时同步更新 `docs/` 中的使用、构建或架构说明。
