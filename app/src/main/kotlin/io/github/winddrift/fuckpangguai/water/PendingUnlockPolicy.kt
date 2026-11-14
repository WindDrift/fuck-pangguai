package io.github.winddrift.fuckpangguai.water

/** 对账结论：接管会话、继续重试、放弃（设备确认未启动）、过期。 */
enum class ReconcileDecision {
    Adopt,
    Retry,
    GiveUp,
    Expired,
}

/**
 * 解锁超时对账的纯策略：TTL、同设备匹配、退避序列与决策。
 *
 * 这里不发起任何网络请求，只回答"现在该做什么"，便于 JVM 单测覆盖所有分支。
 */
object PendingUnlockPolicy {

    /** 待对账记录保留 10 分钟；超时后自动清除，用户可重新解锁。 */
    const val TTL_MS = 10 * 60 * 1000L

    /** 对账总预算 30 秒，超过后放弃本轮（记录仍在 TTL 内保留）。 */
    const val BUDGET_MS = 30_000L

    private val BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L, 3_000L)

    fun isExpired(pending: PendingUnlock, now: Long): Boolean =
        pending.createdAt <= 0L || now - pending.createdAt > TTL_MS

    /** 同设备才拦截重复解锁；goodsId 为空时保守视为不匹配。 */
    fun matches(pending: PendingUnlock, goodsId: String, now: Long): Boolean =
        goodsId.isNotBlank() && pending.goodsId == goodsId && !isExpired(pending, now)

    /** 第 n 次探测前的等待：500ms → 1s → 2s → 3s 封顶。 */
    fun probeDelayMs(probes: Int): Long =
        BACKOFF_MS[(probes - 1).coerceIn(0, BACKOFF_MS.lastIndex)]

    /** 已经等掉的毫秒数，用于判断是否超出总预算。 */
    fun waitedMs(probes: Int): Long {
        var total = 0L
        for (index in 1..probes) total += probeDelayMs(index)
        return total
    }

    /**
     * 决策：
     * - `probe == true` → 设备已启动，接管会话；
     * - `probe == false` → 设备确认未启动，放弃（订单仍保留至 TTL）；
     * - `probe == null`（查询失败/未知）→ 预算内继续重试，超预算放弃本轮。
     */
    fun decide(pending: PendingUnlock, probe: Boolean?, now: Long): ReconcileDecision = when {
        isExpired(pending, now) -> ReconcileDecision.Expired
        probe == true -> ReconcileDecision.Adopt
        probe == false -> ReconcileDecision.GiveUp
        waitedMs(pending.probes) >= BUDGET_MS -> ReconcileDecision.GiveUp
        else -> ReconcileDecision.Retry
    }
}