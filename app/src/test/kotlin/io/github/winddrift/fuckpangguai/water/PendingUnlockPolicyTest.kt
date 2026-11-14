package io.github.winddrift.fuckpangguai.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUnlockPolicyTest {

    private val createdAt = 1_000_000L

    private fun pending(probes: Int = 0) = PendingUnlock(
        orderNo = "order-1",
        msgId = "msg-1",
        goodsId = "goods-1",
        skuId = "sku-1",
        deviceName = "水机",
        supportAppUserFinish = true,
        traceId = "trace-1",
        payType = "0",
        usePoints = false,
        createdAt = createdAt,
        probes = probes,
    )

    @Test
    fun `ttl boundary keeps the record then expires it`() {
        assertFalse(PendingUnlockPolicy.isExpired(pending(), createdAt + PendingUnlockPolicy.TTL_MS))
        assertTrue(
            PendingUnlockPolicy.isExpired(pending(), createdAt + PendingUnlockPolicy.TTL_MS + 1),
        )
    }

    @Test
    fun `matches only the same device within ttl`() {
        assertTrue(PendingUnlockPolicy.matches(pending(), "goods-1", createdAt + 1))
        assertFalse(PendingUnlockPolicy.matches(pending(), "goods-2", createdAt + 1))
        assertFalse(PendingUnlockPolicy.matches(pending(), "", createdAt + 1))
        assertFalse(
            PendingUnlockPolicy.matches(pending(), "goods-1", createdAt + PendingUnlockPolicy.TTL_MS + 1),
        )
    }

    @Test
    fun `probe delay backs off and caps at three seconds`() {
        assertEquals(500L, PendingUnlockPolicy.probeDelayMs(1))
        assertEquals(1_000L, PendingUnlockPolicy.probeDelayMs(2))
        assertEquals(2_000L, PendingUnlockPolicy.probeDelayMs(3))
        assertEquals(3_000L, PendingUnlockPolicy.probeDelayMs(4))
        assertEquals(3_000L, PendingUnlockPolicy.probeDelayMs(99))
        assertEquals(500L, PendingUnlockPolicy.probeDelayMs(0))
    }

    @Test
    fun `waited time grows with probes`() {
        assertEquals(0L, PendingUnlockPolicy.waitedMs(0))
        assertEquals(500L, PendingUnlockPolicy.waitedMs(1))
        assertEquals(1_500L, PendingUnlockPolicy.waitedMs(2))
        assertTrue(PendingUnlockPolicy.waitedMs(20) >= PendingUnlockPolicy.BUDGET_MS)
    }

    @Test
    fun `started device is adopted`() {
        assertEquals(
            ReconcileDecision.Adopt,
            PendingUnlockPolicy.decide(pending(), true, createdAt + 1_000),
        )
    }

    @Test
    fun `definitely not started device gives up`() {
        assertEquals(
            ReconcileDecision.GiveUp,
            PendingUnlockPolicy.decide(pending(), false, createdAt + 1_000),
        )
    }

    @Test
    fun `unknown probe retries within the budget then gives up`() {
        assertEquals(
            ReconcileDecision.Retry,
            PendingUnlockPolicy.decide(pending(probes = 2), null, createdAt + 1_000),
        )
        assertEquals(
            ReconcileDecision.GiveUp,
            PendingUnlockPolicy.decide(pending(probes = 20), null, createdAt + 1_000),
        )
    }

    @Test
    fun `expired record is reported as expired even when the device started`() {
        assertEquals(
            ReconcileDecision.Expired,
            PendingUnlockPolicy.decide(
                pending(),
                true,
                createdAt + PendingUnlockPolicy.TTL_MS + 1,
            ),
        )
    }

    @Test
    fun `adopted session keeps the original order and trace`() {
        val session = pending().toSession(now = 2_000_000L, accountId = "account-a")

        assertEquals("order-1", session.orderNo)
        assertEquals("msg-1", session.msgId)
        assertEquals("trace-1", session.traceId)
        assertEquals("goods-1", session.goodsId)
        assertEquals("sku-1", session.skuId)
        assertEquals("水机", session.deviceName)
        assertEquals(SessionPhase.USING, session.phase)
        assertEquals(2_000_000L, session.startedAt)
        assertEquals("account-a", session.accountId)
        assertTrue(session.supportAppUserFinish)
        // 设备标识不落盘，接管时留空。
        assertEquals("", session.imei)
    }
}