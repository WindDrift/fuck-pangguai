# 贡献指南

欢迎提交 Issue 与 PR。这个项目是非官方客户端，请在参与前阅读[免责声明](docs/disclaimer.md)。

## 开始之前

- 先搜索已有 Issue，避免重复。
- 较大的功能或重构先开 Issue 说明场景、方案与影响范围。
- 不要在 Issue、PR、日志或截图中提交凭据、token、手机号、订单号、设备完整标识或位置信息。
- 不要提交官方客户端反编译代码、接口清单、签名算法、抓包记录或其他逆向材料。
- 涉及支付、账号、设备控制或合规边界的改动，维护者可能拒绝。

## 环境配置

需要 JDK 21 与 Android SDK Platform 37。

```bash
git clone https://github.com/WindDrift/fuck-pangguai.git
cd fuck-pangguai
cp secrets.properties.example secrets.properties
# 填入你有权使用的 API 凭据
```

`secrets.properties` 已被忽略，不要提交。详细说明见[构建指南](docs/build.md)。

## 构建与测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

提交前至少保证测试、lint 与 Debug 构建通过。涉及相机、NFC、定位、通知或支付的改动，请在 PR 中说明真机验证情况；没有设备时明确标注未验证。

## 提交信息

使用单行中文 Conventional Commits：

```text
type(scope): 摘要
```

- 类型：`feat`、`fix`、`refactor`、`perf`、`style`、`docs`、`test`、`build`、`ci`、`chore`、`revert`
- 常用 scope：`app`、`ui`、`auth`、`scan`、`confirm`、`water`、`device`、`net`、`history`、`settings`、`build`、`ci`、`deps`、`docs`、`test`、`release`
- 新 scope 请在 PR 中说明理由。
- 一个提交只做一件事；按里程碑拆分，保证每个提交可编译。
- 示例：`fix(ui): 修复诊断页底部系统栏间距`

## 代码风格

- Kotlin 官方代码风格，4 空格缩进，中文 UI 文案。
- Compose 页面优先复用 `ui/components` 中的现有组件与主题。
- 业务状态放在控制器或仓库中，页面不直接持有 Activity。
- 网络与存储访问沿用现有 `AppGraph` 注入方式，不在页面内临时创建客户端。
- 不引入广告、统计、推送或社交 SDK。
- 不新增绕过定位、实名、风控或支付校验的逻辑。

## PR 流程

1. Fork 仓库并从 `master` 创建分支。
2. 完成改动与测试，保持 PR 描述简短。
3. 填写 PR 模板：摘要、关联 Issue、测试计划、截图（如有）。
4. 确认没有提交敏感文件或生成物。
5. 等待维护者审阅；根据反馈追加提交，不要强制推送已审阅的分支。

PR 描述尽量控制在几段以内，重点写“为什么”和“验证了什么”。

## 文档与版本

- 行为变化时同步更新 `docs/` 与 README。
- 版本号由维护者在发版时统一调整，贡献者不需要单独改。
- 新增依赖时更新 `gradle/libs.versions.toml`，并说明用途与许可证。

## 许可证

提交贡献即表示你同意以 [GPL-3.0](LICENSE) 授权你的改动，并确认你有权提交相关代码与内容。
