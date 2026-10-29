package io.github.winddrift.fuckpangguai.confirm

/**
 * 解锁加速档位。
 *
 * 官方 1.143.1 在 unlock 后立即请求 `machine/msg/sync`，未就绪则每 1 秒重试；
 * 预付费分支还会以 500ms 轮询 `machine/sync`，并可用 `machine/device_is_start`
 * 按订单号判断设备是否已启动。四档只改变请求节奏与预绑定等待方式，不改变
 * unlock 参数、套餐选择或结算逻辑。
 */
enum class UnlockAcceleration(
    val displayName: String,
    val description: String,
) {
    Disabled(
        displayName = "不启用",
        description = "恢复 1.13.1 之前的行为：点击时绑定通道，固定每秒轮询",
    ),
    Conservative(
        displayName = "保守",
        description = "预绑定通道，前 2 秒每 250ms 轮询，之后每秒",
    ),
    Aggressive(
        displayName = "激进",
        description = "不等待预绑定完成，前 3 秒每 100ms 轮询，之后每 250ms",
    ),
    Insane(
        displayName = "丧心病狂",
        description = "并行探测设备启动状态，前 3 秒每 50ms 轮询，之后每 100ms",
    ),
}
