package io.github.winddrift.fuckpangguai.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class UsagePaceTest {

    private val conversion = WaterConversion(
        usageBase = BigDecimal("100"),
        waterMl = BigDecimal("500"),
    )

    @Test
    fun `duration is truncated to one decimal and spans minutes`() {
        assertEquals("0.0 秒", formatUsageDuration(0L))
        assertEquals("12.3 秒", formatUsageDuration(12_399L))
        assertEquals("1 分 00.0 秒", formatUsageDuration(60_000L))
        assertEquals("1 分 05.2 秒", formatUsageDuration(65_299L))
    }

    @Test
    fun `pace divides usage and water by settlement seconds`() {
        val pace = computeUsagePace(
            startedAt = 1_000L,
            endedAt = 13_000L,
            amount = "1.00",
            conversion = conversion,
        )

        assertEquals(12_000L, pace?.durationMs)
        assertEquals("0.1 用量/秒 · 0.4 mL/秒", pace?.let(::formatUsagePace))
    }

    @Test
    fun `volume tagged amount still gets a milliliter rate`() {
        val pace = computeUsagePace(
            startedAt = 1_000L,
            endedAt = 13_000L,
            amount = "1.2L",
            conversion = conversion,
        )

        assertNull(pace?.usagePerSecond)
        assertEquals("100 mL/秒", pace?.let(::formatUsagePace))
    }

    @Test
    fun `without conversion only raw usage rate is available`() {
        val pace = computeUsagePace(
            startedAt = 1_000L,
            endedAt = 3_000L,
            amount = "2",
            conversion = null,
        )

        assertEquals("1 用量/秒", pace?.let(::formatUsagePace))
    }

    @Test
    fun `unparseable amount keeps duration but hides rates`() {
        val pace = computeUsagePace(
            startedAt = 1_000L,
            endedAt = 3_000L,
            amount = "30分钟",
            conversion = conversion,
        )

        assertEquals(2_000L, pace?.durationMs)
        assertNull(pace?.usagePerSecond)
        assertNull(pace?.mlPerSecond)
        assertNull(pace?.let(::formatUsagePace))
    }

    @Test
    fun `missing or too short settlement time yields no pace`() {
        assertNull(computeUsagePace(0L, 2_000L, "1", conversion))
        assertNull(computeUsagePace(2_000L, 0L, "1", conversion))
        assertNull(computeUsagePace(2_000L, 2_000L, "1", conversion))
        assertNull(computeUsagePace(2_000L, 2_050L, "1", conversion))
    }

    @Test
    fun `rates keep at most one decimal and trim zeros`() {
        assertEquals("1", formatUsageRate(BigDecimal("1.04")))
        assertEquals("1.1", formatUsageRate(BigDecimal("1.05")))
        assertEquals("0", formatUsageRate(BigDecimal.ZERO))
        assertEquals("不足 0.1", formatUsageRate(BigDecimal("0.04")))
    }
}
