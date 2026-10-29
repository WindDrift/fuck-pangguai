package io.github.winddrift.fuckpangguai.confirm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnlockTimingTest {

    @Before
    fun setUp() {
        UnlockTimingLog.clear()
    }

    @Test
    fun `keeps only the newest twenty entries`() {
        repeat(25) { index ->
            UnlockTimingLog.record(timing(totalMs = index.toLong()))
        }

        val entries = UnlockTimingLog.entries.value
        assertEquals(20, entries.size)
        assertEquals(24L, entries.first().totalMs)
        assertEquals(5L, entries.last().totalMs)
    }

    @Test
    fun `clear removes all entries`() {
        UnlockTimingLog.record(timing())
        UnlockTimingLog.clear()

        assertTrue(UnlockTimingLog.entries.value.isEmpty())
    }

    @Test
    fun `text keeps timings but no order coordinates or token`() {
        val text = timing(
            outcome = UnlockOutcome.Failed,
            errorKind = "ApiException--1",
        ).toText()

        assertTrue(text.contains("失败"))
        assertTrue(text.contains("保守"))
        assertTrue(text.contains("总 900ms"))
        assertTrue(text.contains("ApiException--1"))
        assertFalse(text.contains("order", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("lat", ignoreCase = true))
    }

    @Test
    fun `text shows the acceleration tier and device start probe`() {
        val text = timing(
            acceleration = UnlockAcceleration.Insane,
            syncSource = "deviceStart",
            deviceStartProbes = 2,
        ).toText()

        assertTrue(text.contains("丧心病狂"))
        assertTrue(text.contains("deviceStart 探测 2 次"))
    }

    private fun timing(
        outcome: UnlockOutcome = UnlockOutcome.Unlocked,
        totalMs: Long = 900L,
        errorKind: String? = null,
        acceleration: UnlockAcceleration = UnlockAcceleration.Conservative,
        syncSource: String = "msgSync",
        deviceStartProbes: Int = 0,
    ) = UnlockTiming(
        atMillis = 1_700_000_000_000L,
        outcome = outcome,
        acceleration = acceleration,
        channelMethod = 15,
        channelName = "支付宝免密",
        prebindAttempted = true,
        prebound = true,
        bindSkipped = true,
        bindWaitMs = 0L,
        bindCallMs = 0L,
        unlockCallMs = 180L,
        syncPollCount = 3,
        syncDurationMs = 620L,
        syncSource = syncSource,
        deviceStartProbes = deviceStartProbes,
        totalMs = totalMs,
        errorKind = errorKind,
    )
}
