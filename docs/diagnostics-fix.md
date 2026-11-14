# 诊断与接水链路修复契约

本文把一次真实诊断导出（2026-11-11，诊断包 `diagnostics.txt`/`diagnostics.json`，54 条 trace、5817 条事件）暴露的 6 项缺陷固化为可验收的修复契约：根因位置、待改函数、行为契约、JVM 可测部分与必须真机验证的部分、提交拆分。

- 行号基于提交 `af147a9`（版本 `1.17.0-af147a9`）。实现时若行号漂移，以函数名为准。
- 本文只描述行为与代码位置，不包含接口地址、请求头、签名算法、凭据、设备名、goodsId、订单号或 msgId 等真实值。
- 契约中出现的"证据"均指该诊断包内的统计口径，实现与评审可据此复核。

## 一、证据概览

| 指标 | 诊断包实测 | 期望 |
| --- | --- | --- |
| 接水 trace | 54（成功 53、失败 1） | — |
| 结算支付阶段 | 53 条参与，平均 0ms、最大 0ms；53/53 条 `startedAt > endedAt` | 耗时为真、`startedAt <= endedAt` |
| trace 总耗时 | 含"结束接水"的 53 条 trace，其 `trace.endedAt` 全部等于该阶段的 `endedAt` | 总耗时覆盖结算与支付 |
| 积分任务完成请求 | 157 次：`code=0,data=false` 114 次、`code=0,data=true` 17 次、HTTP 405 26 次 | 只有 `data=true` 计为完成；405 触发熔断 |
| 网络事件 | 客户端取消 54 条、Socket closed 8 条、timeout 3 条，全部记为 `Error` | 取消降为 `Debug` 并带标记 |
| 响应预览 | "省略 16384 字节非 JSON 响应" 57 条（任务列表 55、订单列表 2） | 截断与"非 JSON"分开表述 |
| 失败 trace 设备信息 | 唯一失败 trace 的 `deviceName`/`goodsId` 均为空，导出显示"未知设备 / goodsId=-" | 保留确认页已知的设备信息 |

## 二、总览

| 编号 | 缺陷 | 主要待改文件 | JVM 单测可覆盖 | 必须真机验证 |
| --- | --- | --- | --- | --- |
| D1 | 解锁超时后无对账，可重复下单 | `confirm/ConfirmController.kt`、`water/`（新增待对账记录与策略）、`service/WaterSyncService.kt` | 超时保留订单号、重复下单拦截、对账决策与退避、会话接管参数 | 真实解锁超时、前台服务接管、通知、服务端订单数 |
| D2 | 结算耗时恒为 0 | `water/WaterSession.kt`、`diagnostics/WaterTraceTracker.kt`、`service/WaterSyncService.kt` | 全部（纯函数） | 真实结算后导出核对 |
| D3 | 积分任务完成判定与熔断 | `points/PointsRepository.kt`、`points/PointsController.kt` | 全部（假数据源 + `HttpException`） | 真实任务接口行为 |
| D4 | trace 设备信息被首写胜出丢弃 | `diagnostics/Diagnostics.kt`、`diagnostics/DiagSink.kt` | 合并语义、确认页续接扫码 trace | 导出物中失败 trace 的设备字段 |
| D5 | 客户端取消被记为 Error | `net/DiagnosticNetworkInterceptor.kt` | 分类与级别判定（纯函数） | 页面切走后取消请求的落盘级别 |
| D6 | 截断 JSON 被误报为"非 JSON" | `net/DiagnosticNetworkInterceptor.kt`、`net/RequestLog.kt` | 全部（纯函数） | 导出物文案与大响应路径 |

---

## 三、逐项契约

### D1 解锁超时对账（避免重复下单）

**现象**：解锁请求已返回成功（含 `msgId`、`orderNo`），15 秒轮询未就绪后整条链路直接失败。此后既没有继续同步设备状态，也没有创建接水会话，App 内看不到这次接水；用户再次点击解锁会再下一单，而第一单的设备可能已经启动。

**根因**

| 位置 | 问题 |
| --- | --- |
| `confirm/ConfirmController.kt:510-513` | `waitForDevice` 返回 `!ready` 时直接 `throw ApiException(-1, "设备启动超时，请检查设备状态")`，没有对账动作 |
| `confirm/ConfirmController.kt:500` | `val unlock = waterRepository.unlock(...)` 是 `try` 内局部变量，`catch`（`590-644`）拿不到 `orderNo`/`msgId`，结构上无法对账 |
| `confirm/ConfirmController.kt:968-971` | `waitForDevice` 在 `msgId` 为空时也直接抛 `ApiException`；此时 `orderNo` 已存在，同样应进入待对账 |
| `confirm/ConfirmController.kt:590-644` | 失败分支只做 stage/trace 收尾、清套餐缓存、置 `ConfirmState.Error`，没有持久化任何待核对信息 |
| `water/WaterSessionManager.kt:43-72` | 会话只在 `ConfirmState.Unlocked` 时创建；失败路径永远没有会话 |
| `service/WaterSyncService.kt:75-116` | 服务只有 `ACTION_START`/`ACTION_END`/`ACTION_STOP`，没有对账入口 |
| `confirm/ConfirmScreen.kt:126-134` | 失败态只显示"操作失败"，用户没有"正在核对上一笔"的可见状态 |
| `water/WaterRepository.kt:49-52` | `queryDeviceStarted` 用 `Boolean?` 表达"未启动/查询失败"，对账必须区分 `false` 与 `null` |

**待改函数与新增**

- `ConfirmController.unlock()`：解锁成功后立即登记待对账；`!sync.ready` 时抛携带订单标识的异常（建议 `class UnlockTimeoutException(val orderNo: String, val msgId: String, val polls: Int, val durationMs: Long)`），并在 `catch` 中先于通用分支处理。
- `ConfirmController` 构造函数：新增 `pendingStore: PendingUnlockStore = NoopPendingUnlockStore` 参数（默认实现为空），保证既有 41 例 `ConfirmControllerTest` 与 6 例 `ConfirmControllerTraceTest` 不改构造调用即可编译。
- `ConfirmController.start()`：进入确认页时读取待对账记录，命中同设备则进入"待对账"展示态而不是可解锁态。
- `ConfirmController.unlock()` 入口：待对账未结清时禁止再次调用 `waterRepository.unlock`。
- 新增 `water/PendingUnlock.kt`：`@Serializable data class PendingUnlock(orderNo, msgId, goodsId, skuId, deviceName, supportAppUserFinish, traceId, payType, usePoints, createdAt, probes)`；`interface PendingUnlockStore { suspend fun load(); save(); clear() }`；`DataStorePendingUnlockStore`（参照 `ticket/PendingRechargeStore.kt` 的写法，带 TTL 清理）。**不写入 `imei`/SN**：对账只需要订单号与 `msgId`，`WaterSession.imei` 目前没有任何读取方，接管会话时留空即可，避免把设备标识额外落盘。
- 新增纯策略 `water/PendingUnlockPolicy.kt`：`TTL_MS = 10 分钟`、`isExpired(pending, now)`、`matches(pending, goodsId, now)`、`probeDelayMs(probes)`（500ms → 1000ms → 2000ms → 3000ms 封顶）、`fun decide(pending, probe: Boolean?, now): ReconcileDecision`，`ReconcileDecision` 为 `Adopt`/`Retry`/`GiveUp`/`Expired`。
- 新增 `PendingUnlock.toSession(now: Long, accountId: String): WaterSession`（`phase = USING`，`startedAt = now`，沿用 `orderNo`/`msgId`/`traceId`/`deviceName`/`skuId`/`supportAppUserFinish`，`imei = ""`）。`supportAppUserFinish` 必须随记录保存，否则接管会话后用户无法在 App 内结束接水。
- `WaterSessionManager.start()`：接管路径改为复用上面的 `toSession`，保证对账接管与正常解锁创建出的会话字段一致。

**行为契约**

1. 解锁请求成功后，无论后续是否就绪，都必须先落盘 `PendingUnlock`；只有以下三种情况清除：会话已接管、策略判定 `Expired`、用户显式放弃。策略判定 `GiveUp` 保留记录至 TTL，因为订单已经产生，设备仍可能稍后启动。
2. 超时不再丢弃订单：失败后按 `probeDelayMs` 继续用 `msgSync(msgId)` 与 `queryDeviceStarted(orderNo)` 两路信号对账，同一次探测共享 30 秒总预算，任一就绪即接管；`queryDeviceStarted` 返回 `null` 视为未知、继续重试，返回 `false` 才计入"未启动"。
3. 对账成功 → 用 `toSession` 创建会话并交给前台服务，用户提示"设备已启动，正在接水"。
4. 对账失败 → 保留 `PendingUnlock` 至 TTL，UI 提示"上一笔解锁待核对，请勿重复解锁"；同设备再次解锁被拦截（记 `unlock_reconcile_blocked`）。
5. 用户显式放弃需二次确认，由 `ConfirmController.abandonPendingUnlock()` 清除记录并记 `unlock_reconcile_abandoned`（字段 `orderNo`、`pendingAgeMs`），同时取消在途的对账循环；通用 `abandon()` 只收尾 trace，不得清除待对账记录。TTL 到期由存储层静默清除；`unlock_pending_expired` 只在**对账循环内**判定 `Expired` 时记录。
6. 用户可见文案保持"设备启动超时"语义，但不再暗示"直接重试即可"。
7. 新增诊断事件（`DiagFlow.Confirm`）：`unlock_timeout_pending`、`unlock_reconcile_adopt`、`unlock_reconcile_adopt_deferred`、`unlock_reconcile_give_up`、`unlock_reconcile_blocked`、`unlock_reconcile_abandoned`、`unlock_pending_expired`；字段只允许 `orderNo`、`polls`、`probes`、`waitedMs`、`pendingAgeMs`、`source`。
8. 页面已离开时不接管：`Adopt` 前校验 `abandoned` 与 `generation`，命中则保留记录并记 `unlock_reconcile_adopt_deferred`，等待下次进入确认页再核对。

**验收标准**

- JVM：
  - `ConfirmControllerReconcileTest`：`timeout records pending unlock with order identifiers`（`msgReady=false`，推进 16s 后 `pendingStore.saved` 的 `orderNo`/`msgId` 与假数据源一致，`unlockCalls == 1`）。
  - 同文件：`second unlock is blocked while pending exists`（再次 `unlock()` 后 `unlockCalls` 仍为 1）。
  - 同文件：`adopting pending creates a using session`（`toSession` 的 `phase`/`startedAt`/`orderNo`/`traceId` 断言）。
  - `PendingUnlockPolicyTest`：TTL 边界、同设备匹配、退避序列与总预算、`decide` 四分支。
  - 既有 `msg sync timeout produces error and does not mark device confirmed` 需同步更新 `errorKind` 期望（`ApiException--1` → `UnlockTimeout`），用户可见文案不变。
- 真机（按 `AGENTS.md` 标注）：定位、NFC、相机与前台服务均不可由 JVM 覆盖。需在真机用弱网或断电设备制造一次超时，确认：UI 与通知状态正确、`PendingUnlock` 在杀进程重启后仍在、设备随后启动能被接管并完成结束与结算、服务端只产生 1 笔订单。

---

### D2 结算耗时归零

**现象**："结算支付"阶段 53/53 条 `startedAt > endedAt`、`durationMs = 0`，统计平均 0ms；trace 总耗时止于"结束接水"，不含结算与支付（示例 trace 总耗时 21218ms，恰好等于结束接水结束时刻减 trace 开始）。

**根因**

| 位置 | 问题 |
| --- | --- |
| `diagnostics/WaterTraceTracker.kt:139-143` | `finishAt = session.endedAt`，而 `endedAt` 在结算之前就已写入 |
| `diagnostics/WaterTraceTracker.kt:118` | `startedAt = session.settleStartedAt`，该值晚于 `endedAt` |
| `diagnostics/WaterTraceTracker.kt:148` | `(finishAt - startedAt).coerceAtLeast(0L)` 把时间倒挂吞成 0，掩盖了错误 |
| `diagnostics/WaterTraceTracker.kt:156-158, 187-188` | `finalize` 用 `session.endedAt` 作 trace 结束时间 |
| `service/WaterSyncService.kt:262-269` | 先写 `endedAt`（`265`）再调用 `settle()`（`269`） |
| `service/WaterSyncService.kt:370-373` | `settleStartedAt` 在 `endedAt` 之后用 `System.currentTimeMillis()` 写入 |
| `service/WaterSyncService.kt:424-455, 489-528` | 结算终态只更新 `endedAt`/`updatedAt`，没有独立的结算结束时间 |

**待改函数与新增**

- `WaterSession`（`water/WaterSession.kt`）：新增 `val settleEndedAt: Long = 0L`（带默认值，旧记录反序列化兼容）。
- `WaterSyncService.settle()`：终态 `copy` 时写 `settleEndedAt = System.currentTimeMillis()`（`PAID`/`PAY_UNKNOWN`/`FINISHED_NO_CHARGE` 三条路径）。
- `WaterSyncService.resumeSettlement()`：同样写 `settleEndedAt`。
- `WaterSyncService.finish()`：兜底——`settleStartedAt > 0` 且 `settleEndedAt == 0` 时补写 `settleEndedAt = now`。
- `WaterTraceTracker.updateSettlement()`：仅当 `settleStartedAt > 0`、`payOrderNo` 非空或相位属于 `SETTLING`/`PAID`/`FINISHED_NO_CHARGE`/`PAY_UNKNOWN` 时才生成记录；`finishAt = session.settleEndedAt.takeIf { it > 0L } ?: session.updatedAt`，并把 `startedAt` 收敛为 `min(startedAt, finishAt)` 保证不倒挂。
- `WaterTraceTracker.finalize()`：trace 结束时间改为 `maxOf(session.endedAt, session.settleEndedAt)`；`waterDurationMs` 仍用 `session.endedAt - session.startedAt`（"用水时长"语义不变）。

**行为契约**

1. `StageRecord` 不变量：`endedAt == 0L || endedAt >= startedAt`，且 `durationMs == (endedAt - startedAt)`。实现不得用 `coerceAtLeast(0)` 掩盖倒挂，应在构造处保证顺序。
2. `settleEndedAt` 只在结算终态写入一次（first-write-wins），重入不覆盖。
3. 旧持久化记录（`settleEndedAt = 0`）必须回落到 `updatedAt` 得到正耗时，不得显示 0。
4. `trace.endedAt` 不得小于"结束接水"阶段的 `endedAt`；`totalDurationMs` 因此覆盖结算。
5. 未进入结算的故障不生成 `SettlePay` 记录：`settleStartedAt == 0`、`payOrderNo` 为空，且相位不属于 `SETTLING`/`PAID`/`FINISHED_NO_CHARGE`/`PAY_UNKNOWN` 时，跳过该阶段。接水阶段设备异常转 `FAULT` 属于这种情况，避免统计里出现一条 0ms 的假结算；已在结算中的 `FAULT` 仍按 `settleEndedAt` 记录真实耗时。

**验收标准**

- JVM：`WaterTraceTrackerTest` 新增 `settlement duration uses settle end time`、`legacy session without settle end falls back to updatedAt`、`trace end covers settlement`、`fault before settlement does not create a settle stage`、`fault during settlement still records a real settle duration`；`DiagStatsTest` 新增"结算阶段平均耗时 > 0"；既有 7 例全部保持通过。
- 真机（按 `AGENTS.md` 标注）：结算与支付涉及真实扣款。需在真机完成一次付费接水后导出，核对结算支付耗时 > 0、`startedAt <= endedAt`、总耗时 ≥ 结束接水结束时间；免密支付与余额支付各一次。

---

### D3 积分任务完成判定与熔断

**现象**：157 次任务完成请求中，114 次返回 `code=0, data=false`（服务端未真正记账），17 次 `data=true`，26 次 HTTP 405。日志里 114+26 次都被记为"任务完成"或普通失败，`completed` 计数虚高；405 不触发熔断，单轮产生 26 次无效请求。

**根因**

| 位置 | 问题 |
| --- | --- |
| `points/PointsRepository.kt:73-80` | `completeTask` 只 `requireSuccess(response.code, response.msg)`，完全忽略 `response.data`（类型为 `ApiResponse<JsonElement>`，实际载荷是字面量 `true`/`false`） |
| `net/QiekjApi.kt:171-173` | `taskCompleted` 声明为 `ApiResponse<JsonElement>`，业务层无法在类型上区分"成功但未记账" |
| `points/PointsController.kt:609-611` | `dataSource.completeTask(...)` 返回后无条件 `counters.completed += 1` |
| `points/PointsController.kt:617-621` | 只有 `throwable is IOException` 计入 `networkErrors`；Retrofit 对非 2xx 抛 `HttpException`，405 不计入 |
| `points/PointsController.kt:622-625, 785` | `MAX_NETWORK_ERRORS = 3` 的熔断因此永不触发 |
| `points/PointsController.kt:488-491, 552-557` | `data:false` 时 `completeTask` 不抛异常，`runOne` 返回 `true`，两个调用方的循环继续白跑 |

**待改函数与新增**

- `PointsDataSource.completeTask(...)`：返回值改为 `Boolean`（`true` 仅当 `code == 0` 且载荷为 `true`）。
- `PointsRepository.completeTask()`：`requireSuccess(...)` 后返回布尔载荷。推荐把 `QiekjApi.taskCompleted` 声明改为 `ApiResponse<Boolean>`（服务端返回字面量布尔，kotlinx 可直接解码）；若担心个别环境返回字符串，则保留 `JsonElement` 并用 `(data as? JsonPrimitive)?.booleanOrNull == true` 解析。`data` 为 `null`、`false` 或解析失败都算未完成。
- 新增纯函数（`points/PointsErrorPolicy.kt`）：`isNetworkFailure(throwable: Throwable): Boolean`，规则为 `IOException`，或 `retrofit2.HttpException` 且 `code() in {405, 408, 425, 429} ∪ 500..599`。`retrofit` 已在依赖中，无需新增库。
- `PointsController.TaskRunner.runOne()`：仅在 `completeTask` 返回 `true` 时 `completed += 1` 并重置 `networkErrors`；返回 `false` 时 `skipped += 1`、记 Warning "任务未完成（服务端未确认）"、返回 `false`（终止该任务的本轮循环）；异常分支用 `PointsErrorPolicy.isNetworkFailure` 累加。

**行为契约**

1. 只有服务端 `data == true` 计入 `completed`；`data == false` 计入 `skipped`，且不重复尝试同一任务（`runOne` 返回 `false`）。
2. 405/408/425/429/5xx 与 `IOException` 同等计入连续网络错误；连续 3 次抛 `StopTaskPhase` 并记 `Error` "连续网络错误，停止任务阶段"。
3. `isAuthExpired`（`ApiException` 码 2/10/126）优先级高于熔断，必须继续向上抛，不被 `StopTaskPhase` 吞掉。
4. 登录失效、验证码与业务失败（如 `ApiException` 非网络码）不计入 `networkErrors`。
5. 日志与诊断字段不新增任何请求体或凭据；只允许 `taskCode`、`result`、`httpCode`。

**验收标准**

- JVM：
  - `PointsErrorPolicyTest`：`IOException`、`HttpException(405/429/503)`、`ApiException(500)`、`ApiException(2)`、普通异常的分类断言。
  - `PointsControllerTest` 新增：`server false data is not counted as completed`（`FakeData` 返回 `false`，`lastRun.completed == 0`、`skipped >= 1`、`completed` 调用次数为 1）。
  - 同文件：`three 405 responses stop the task phase`（`FakeData` 抛 `HttpException(Response.error(405, ...))`，断言停止、`completed == 0`、日志含"连续网络错误"）。
  - 既有 9 例保持通过（`FakeData.completeTask` 需同步改为返回 `Boolean`）。
- 真机（按 `AGENTS.md` 标注）：积分任务依赖真实账号与真实任务接口。需在真机确认 `data:false` 不再显示"任务完成"、405 连续出现 3 次后该轮停止、积分余额变化与服务端记账一致。

---

### D4 trace 设备信息丢失

**现象**：54 条 trace 中唯一失败的那条 `deviceName` 与 `goodsId` 都为空，导出显示"未知设备 / goodsId=-"；其余 trace 由会话 tracker 事后补齐，所以只有失败链路暴露。

**根因**

| 位置 | 问题 |
| --- | --- |
| `diagnostics/Diagnostics.kt:130-139` | `startTrace` 命中已存在的 `traceId` 直接 `return`（首写胜出），后续富化信息被丢弃 |
| `scan/ScanController.kt:367-383` | 首建 trace 只有来源与解码耗时，没有 `deviceName`/`goodsId` |
| `confirm/ConfirmController.kt:281-290` | `prepareTrace` 携带 `deviceName`/`goodsId` 调用 `startTrace`，被上一条规则丢弃 |
| `service/WaterSyncService.kt:558-563` | `replaceTrace` 只在会话轮询时调用；解锁失败没有会话，因此没有兜底 |
| `diagnostics/DiagSink.kt:21` | 接口没有写明 `startTrace` 是"确保存在"还是"仅新建"，调用方按后者理解 |

**待改函数与新增**

- `Diagnostics.startTrace()`：语义改为"确保存在 + 合并富化字段"。新增纯函数 `internal fun mergeTraceStart(existing: WaterTrace, incoming: WaterTrace): WaterTrace`：保留 `existing` 的 `traceId`、`startedAt`、`stages`、`outcome`、`endedAt`；用 `incoming` 的非空 `deviceName`/`goodsId`/`source` 与 `decodeMs > 0` 补位；`updatedAt = maxOf(existing.updatedAt, incoming.updatedAt)`；`existing.outcome != Running` 时原样返回。
- `DiagSink.startTrace` 注释：明确"幂等、不得丢弃非空富化字段"；`replaceTrace` 保持现有"拒绝终态被 Running 覆盖 + 拒绝 `updatedAt` 回退"语义（`Diagnostics.kt:224-240`）。
- `test/.../diagnostics/FakeDiagSink.kt:37-39`：`putIfAbsent` 改为同一合并语义，否则 JVM 用例无法覆盖该行为。
- 可选增强：`ScanController.resolveNow` 解析成功后可 `replaceTrace` 富化设备信息，使"解析后放弃"的 trace 也有设备名。

**行为契约**

1. 同一 `traceId` 的重复 `startTrace` 不得丢弃 `deviceName`/`goodsId`/`source`/`decodeMs`。
2. 已终态（`outcome != Running`）的 trace 不被 `startTrace` 改写。
3. 阶段记录仍保持首写胜出；`stageStart`/`stageFinished`/`finishTrace` 语义不变。
4. 富化只接受名称与 goodsId，长度上限沿用 `sanitizeTrace`（128/512），不得新增任何设备标识或原始 JSON。

**验收标准**

- JVM：
  - 新增 `DiagTraceMergeTest`：空设备信息被补齐、已有设备信息不被空值覆盖、终态 trace 不被改写、`updatedAt` 取较大值、`decodeMs` 只补正值。
  - `ConfirmControllerTraceTest` 新增 `scan trace keeps device info from confirm page`：先向 `FakeDiagSink` 预置一条只有 `source` 的扫码 trace，再 `controller.start(device)`，断言 `deviceName == "水机"`、`goodsId == "goods-1"`。
  - `DiagExportTest` 可选：失败 trace 导出文本不出现"未知设备"。
- 真机（按 `AGENTS.md` 标注）：扫码相机与 NFC 属真机路径。需在真机复现一次解锁失败（弱网或断电设备）后导出，确认该 trace 显示设备名与 goodsId。

---

### D5 客户端取消降噪

**现象**：5817 条事件中 65 条网络事件是客户端主动取消或断链（`Canceled` 54、`Socket closed` 8、`timeout` 3），全部记为 `Error`。用户切页、退出确认页产生的取消淹没了真正的失败。

**根因**

| 位置 | 问题 |
| --- | --- |
| `net/DiagnosticNetworkInterceptor.kt:56-57` | `failure != null \|\| status >= 500` 一律 `Error`，不区分取消 |
| `net/DiagnosticNetworkInterceptor.kt:64-72` | 字段里只有原始 `error` 文本，没有可筛选的 `canceled`/`timeout` 标记 |
| `net/DiagnosticNetworkInterceptor.kt:32-44` | `intercept` 的 `catch` 原样上抛，取消与真实失败在 `finally` 中不可分辨 |

**待改函数与新增**

- 新增纯函数 `internal fun classifyNetworkFailure(throwable: Throwable): NetworkFailureKind`（`net/`，与 `redactResponseBody` 同级），枚举 `Canceled`/`Timeout`/`Io`/`Other`：
  - `Canceled`：message 忽略大小写等于 `canceled`/`cancelled`/`Socket closed`；或 `kotlinx.coroutines.CancellationException`；或原因链上出现上述取消语义。
  - `Timeout`：`java.net.SocketTimeoutException`，或其余 `InterruptedIOException`。判定顺序先按 message 认取消，再按子类认超时，避免把真实超时误降为 `Debug`。
  - `Io`：其余 `IOException`；`Other`：不属于以上任何一类的异常。
- 新增纯函数 `internal fun networkEventLevel(status: Int?, failure: NetworkFailureKind, businessCode: Int?, path: String): DiagLevel`：取消 → `Debug`；超时 → `Warn`；其余 IO → `Warn`；`status >= 500` → `Error`；`400..499` → `Warn`；业务码非 0 → `Warn`；轮询路径成功 → `Debug`；核心路径成功 → `Info`；其余 `Debug`。
- `DiagnosticNetworkInterceptor.report()`：改用上述两个函数；字段新增 `canceled=true` 或 `timeout=true`（布尔字符串，不含原文）。

**行为契约**

1. 客户端主动取消（页面切走、协程取消、连接被主动关闭）记 `Debug`，并带 `canceled=true`；不得计入错误统计。
2. 超时记 `Warn` 并带 `timeout=true`；连接失败等真实 IO 错误记 `Warn`；未知异常与 `status >= 500` 仍为 `Error`。
3. 取消判定不得吞掉 `CancellationException` 语义：协程取消归类为 `Canceled` 并记 `Debug`，但拦截器只记录，不改变异常上抛。
4. 新增字段不含响应体、请求体、请求头与异常堆栈；`error` 字段保持现状（仅 message 或类名）。

**验收标准**

- JVM：新增 `NetworkFailureClassifyTest`：`IOException("Canceled")` → `Canceled`/`Debug`；`SocketException("Socket closed")` → `Canceled`；`SocketTimeoutException` → `Timeout`/`Warn`；`IOException("Failed to connect")` → `Io`/`Warn`；`status=503` → `Error`（回归）；`status=405` → `Warn`（回归）。
- 真机（按 `AGENTS.md` 标注）：需在真机在轮询中切走页面/退出确认页，导出后确认该事件为 `Debug` 且 `canceled=true`，错误计数不再包含它。

---

### D6 预览截断文案

**现象**：57 条"省略 16384 字节非 JSON 响应"（任务列表 55、订单列表 2）——这些响应其实是合法 JSON，只是超过 16 KB 预览上限被 `peekBody` 截断；另有 26 条"省略 2657 字节非 JSON 响应"（405 错误页），这类才是真非 JSON。两种完全不同的情况用了同一句话，导致"任务列表看不到"被误判为服务端返回异常。

**根因**

| 位置 | 问题 |
| --- | --- |
| `net/DiagnosticNetworkInterceptor.kt:86-95` | `peekBody(previewBytes)` 静默截断，调用方不知道是否截断 |
| `net/DiagnosticNetworkInterceptor.kt:89` | 把截断后的字符串直接交给 `redactResponseBody` |
| `net/RequestLog.kt:41-48` | 解析失败即判"非 JSON"，返回 `"<省略 N 字节非 JSON 响应>"` |
| `net/DiagnosticNetworkInterceptor.kt:90-92` | `redacted.length > previewBytes` 既无法识别截断（`peekBody` 已按字节截断，字符数恒 ≤ 字节数），又混用字符数与 UTF-8 字节数 |
| `net/DiagnosticNetworkInterceptor.kt:102-103` | `PREVIEW_BYTES = 4 KB`（Release）、`DIAGNOSTIC_PREVIEW_BYTES = 16 KB`（诊断/Debug），两者都要走新文案 |

**待改函数与新增**

- `redactResponseBody(body: String, truncated: Boolean = false)`（`net/RequestLog.kt`）：
  - `truncated` 且首字符为 `{`/`[` 且解析失败 → `"<已截断的 JSON 响应：≥N 字节，未保存预览>"`；
  - 非截断且非 JSON → 保持现有 `"<省略 N 字节非 JSON 响应>"`；
  - 合法 JSON → 保持递归脱敏输出。
- `DiagnosticNetworkInterceptor.readPreview()`：返回 `Preview(text, truncated)`，`truncated` 判定为"读取字节数 ≥ `previewBytes`"或"`response.body.contentLength() > 读取字节数`"；删除不可达分支或改为按 UTF-8 字节比较。
- `report()` 字段新增 `bytes`（预览字节数）与 `truncated=true`（仅截断时）。
- `RequestLogInterceptor`（`net/RequestLog.kt:127-131`）保持 `truncated = false` 调用（512 KB 上限，不参与本次改动）。

**行为契约**

1. 截断的 JSON 与真正的非 JSON 必须给出不同文案，且都不保存原文（隐私红线不变）。
2. 截断标记必须能从事件字段筛选：`truncated=true` 与 `bytes`。
3. 合法 JSON 的脱敏结果与现在逐字节一致（既有 `LogRedactionTest`/`RequestLogRedactionTest` 全部保持通过）。
4. Release 的 4 KB 预览同样适用新文案。
5. 文案不含响应片段、字段名或业务码。

**验收标准**

- JVM：
  - `LogRedactionTest` 新增：截断 JSON（`{"code":0,"data":[` 前缀）→ 文案含"已截断"与"JSON"，且不含原文片段；非 JSON（`<html>`）→ 仍是"非 JSON"文案；合法 JSON 仍脱敏。
  - 新增 `NetworkPreviewTest`：`truncated` 判定（等于/小于 `previewBytes`、`contentLength` 更大）、4 KB 与 16 KB 两个上限。
- 真机（按 `AGENTS.md` 标注）：需在真机打开积分页触发任务列表（大响应），导出后确认该事件带 `truncated=true` 且文案为"已截断的 JSON 响应"，不再出现"非 JSON"。

---

## 四、验证命令

每个提交都必须独立通过：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleDiagnostic
```

- 单测使用虚拟密钥，不需要 `secrets.properties`；`assembleDiagnostic` 需要 `keystore.properties` 才能产出正式签名包。
- lint 通过不代表真机可用；D1/D3/D5/D6 的真机项必须在 `docs/testing.md` 的对应清单中逐条勾选。

## 五、提交拆分

6 个代码提交 + 1 个文档提交，全部为单行中文 `type(scope): 摘要`，每个提交都可独立编译（`testDebugUnitTest` + `assembleDebug` 通过）。

| # | 提交信息 | 主要文件 | 依赖 | 独立可编译理由 |
| --- | --- | --- | --- | --- |
| 1 | `feat(water): 新增待对账解锁记录与超时策略` | `water/PendingUnlock.kt`、`water/PendingUnlockPolicy.kt`、新增单测 | 无 | 只新增纯 JVM 类型与策略，无调用方也能编译 |
| 2 | `fix(confirm): 解锁超时改为对账并拦截重复下单` | `confirm/ConfirmController.kt`、`confirm/ConfirmScreen.kt`、`ui/PangguaiRoot.kt`、`AppGraph.kt`、`water/WaterSessionManager.kt`、`service/WaterSyncService.kt`、单测 | #1 | 调用 #1 的策略；异常、状态、UI 与依赖注入同批改完 |
| 3 | `fix(water): 结算阶段记录真实结束时间` | `water/WaterSession.kt`、`diagnostics/WaterTraceTracker.kt`、`diagnostics/model/DiagStats.kt`、`diagnostics/DiagExport.kt`、`debug/DebugScreen.kt`、`service/WaterSyncService.kt`、单测 | 无 | 字段带默认值，读写、统计口径与展示同批改完 |
| 4 | `fix(app): 起 trace 时合并设备信息` | `diagnostics/Diagnostics.kt`、`diagnostics/DiagSink.kt`、`test/.../FakeDiagSink.kt`、单测 | 无 | 纯合并语义，不改调用方签名 |
| 5 | `fix(app): 任务完成以服务端结果为准并熔断 405` | `points/PointsRepository.kt`、`points/PointsController.kt`、`points/PointsErrorPolicy.kt`、单测 | 无 | 接口返回值与调用方、假数据源同批改完 |
| 6 | `fix(net): 取消降噪并区分截断预览` | `net/DiagnosticNetworkInterceptor.kt`、`net/RequestLog.kt`、单测 | 无 | 纯函数 + 拦截器同批改完 |
| 7 | `docs: 补充诊断与接水修复契约` | `docs/diagnostics-fix.md`、`docs/README.md`、`docs/privacy.md`、`docs/architecture.md`、`docs/debug.md`、`docs/testing.md` | #1–#6 | 纯文档 |

评审修复不单独开提交，按所属缺陷并入上表：t6 的 F1–F5 分别并入 #2（对账放弃入口与按钮）、#6（取消与级别映射）、#3（未结算故障的结算语义）；t8 的 F1/F2/F7/F10 并入 #2（对账生命周期与接管会话账号）、F3/F4/F5 并入 #7（隐私、存储与验收文档）、F6 并入 #6（删除不可达分支）、F8 并入 #3（统计口径）、F9 并入 #7（本表本身）。

约定：

- scope 只使用 `AGENTS.md` 的固定清单：`points` 与 `diagnostics` 不在清单内，因此提交 4、5 统一用 `app`；若维护者同意新增 scope，可改为 `fix(diagnostics)`/`fix(points)` 并在提交正文说明理由。
- 提交 1 与 2 必须相邻；中间不得插入会改变 `ConfirmController` 行为的提交。
- 提交 3 与提交 2 都改 `WaterSyncService.kt`，建议按上表顺序落地以减少冲突。
- 版本号提升（`versionCode + 1`、`versionName` 加短哈希）放在独立的发版提交，不与上述修复混在一起。
- 每完成一个可编译里程碑，先同步 `docs/testing.md` 的对应验收项与 `docs/debug.md` 的事件说明，再单独提交。

## 六、范围与边界

- 本契约只覆盖 D1–D6；不改接口地址、签名、请求头、套餐与支付渠道逻辑。
- 相机、NFC、定位、前台服务、支付相关的改动必须按 `AGENTS.md` 在提交与 PR 中写明"哪些只做了 JVM 测试、哪些需要真机验证"。
- 新增诊断事件或字段必须同步 `docs/privacy.md` 与 `docs/debug.md`；本契约新增字段均为布尔标记、计数与毫秒数，不含原文。
- 不在本契约内：积分任务的业务策略调整（哪些任务可自动做）、诊断日志的保留/上限策略、导出格式变更。
