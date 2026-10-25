package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryFilterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = timeAt(0, 12)

    @Test
    fun `empty filter keeps original list identity`() {
        val records = listOf(record(id = 1), record(id = 2))
        val filter = HistoryFilter()

        assertSame(records, records.applyHistoryFilter(filter, now, zone))
        assertFalse(filter.hasActiveConstraints)
        assertEquals(0, filter.activeCategoryCount)
    }

    @Test
    fun `search covers visible fields and hides internal identifiers`() {
        val record = record(
            id = 7,
            accountId = "internal-account",
            orderNo = "local-123",
            payOrderNo = "PAY-abc",
            bill = bill(
                shopName = "东区店",
                machineName = "三号机",
                payTypeName = "VIP 校园卡",
                promotions = listOf(BillDiscount(3, "优惠券", "0.20")),
                paidAmount = "1.50",
                ticket = ticket(remark = "接水抵扣", points = 30),
            ),
            error = "设备异常",
        )

        fun matches(query: String) = listOf(record)
            .applyHistoryFilter(HistoryFilter(query = query), now, zone)
            .isNotEmpty()

        assertTrue(matches("东区"))
        assertTrue(matches("pay-abc"))
        assertTrue(matches("三号机"))
        assertTrue(matches("优惠券"))
        assertTrue(matches("校园卡"))
        assertTrue(matches("接水抵扣"))
        assertTrue(matches("设备异常"))
        assertTrue(matches("1.50"))
        assertTrue(matches("¥1.50"))
        assertTrue(matches("30 点"))
        assertTrue(matches("vip"))
        assertFalse(matches("internal-account"))
        assertFalse(matches("local-123"))
    }

    @Test
    fun `same category is or and different categories are and`() {
        val a = record(id = 1, deviceName = "A", phase = SessionPhase.PAID)
        val b = record(id = 2, deviceName = "B", phase = SessionPhase.FAULT)
        val c = record(id = 3, deviceName = "A", phase = SessionPhase.FAULT)
        val records = listOf(a, b, c)

        val byDevice = records.applyHistoryFilter(
            HistoryFilter(devices = setOf("A", "B")),
            now,
            zone,
        )
        assertEquals(listOf(1L, 2L, 3L), byDevice.map { it.id })

        val paidOnly = records.applyHistoryFilter(
            HistoryFilter(
                devices = setOf("A", "B"),
                sessionResults = setOf(SessionResult.PAID),
            ),
            now,
            zone,
        )
        assertEquals(listOf(1L), paidOnly.map { it.id })

        val aAndEither = records.applyHistoryFilter(
            HistoryFilter(
                devices = setOf("A"),
                sessionResults = setOf(SessionResult.PAID, SessionResult.FAULT),
            ),
            now,
            zone,
        )
        assertEquals(listOf(1L, 3L), aAndEither.map { it.id })

        val searchAndDevice = records.applyHistoryFilter(
            HistoryFilter(query = "A", devices = setOf("A")),
            now,
            zone,
        )
        assertEquals(listOf(1L, 3L), searchAndDevice.map { it.id })
    }

    @Test
    fun `time presets use local day boundaries`() {
        val todayMidnight = record(id = 1, updatedAt = timeAt(0, 0, 0))
        val yesterdayLate = record(id = 2, updatedAt = timeAt(1, 23, 59))
        val sevenDaysAgo = record(id = 3, updatedAt = timeAt(6, 0, 0))
        val older = record(id = 4, updatedAt = timeAt(7, 0, 0))
        val records = listOf(todayMidnight, yesterdayLate, sevenDaysAgo, older)

        fun ids(preset: HistoryTimePreset) = records.applyHistoryFilter(
            HistoryFilter(timePreset = preset),
            now,
            zone,
        ).map { it.id }

        assertEquals(listOf(1L), ids(HistoryTimePreset.TODAY))
        assertEquals(listOf(1L, 2L, 3L), ids(HistoryTimePreset.LAST_7_DAYS))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(HistoryTimePreset.LAST_30_DAYS))
    }

    @Test
    fun `custom time range includes both dates and supports open ends`() {
        val before = record(id = 1, updatedAt = timeAt(1, 23, 59))
        val start = record(id = 2, updatedAt = timeAt(0, 0, 0))
        val records = listOf(before, start)
        val today = LocalDate.of(2026, 10, 25).toEpochDay()

        fun ids(filter: HistoryFilter) = records.applyHistoryFilter(filter, now, zone).map { it.id }

        assertEquals(
            listOf(2L),
            ids(
                HistoryFilter(
                    timePreset = HistoryTimePreset.CUSTOM,
                    customStartEpochDay = today,
                    customEndEpochDay = today,
                ),
            ),
        )
        assertEquals(
            listOf(2L),
            ids(
                HistoryFilter(
                    timePreset = HistoryTimePreset.CUSTOM,
                    customStartEpochDay = today,
                ),
            ),
        )
        assertEquals(
            listOf(1L),
            ids(
                HistoryFilter(
                    timePreset = HistoryTimePreset.CUSTOM,
                    customEndEpochDay = (today - 1),
                ),
            ),
        )
        assertTrue(
            ids(
                HistoryFilter(
                    timePreset = HistoryTimePreset.CUSTOM,
                    customStartEpochDay = (today + 1),
                    customEndEpochDay = (today - 1),
                ),
            ).isEmpty(),
        )
        assertEquals(
            listOf(1L, 2L),
            ids(
                HistoryFilter(
                    timePreset = HistoryTimePreset.ALL,
                    customStartEpochDay = (today + 1),
                ),
            ),
        )
    }

    @Test
    fun `session and bill states filter with or semantics`() {
        val paid = record(id = 1, phase = SessionPhase.PAID)
        val fault = record(id = 2, phase = SessionPhase.FAULT)
        val unknownPhase = record(
            id = 3,
            phase = "unknown",
            billState = BillState.NONE,
            bill = null,
        )
        val unmatched = record(
            id = 4,
            phase = SessionPhase.FINISHED_NO_CHARGE,
            billState = BillState.UNMATCHED,
            bill = null,
        )
        val noState = record(
            id = 5,
            phase = SessionPhase.FINISHED_NO_CHARGE,
            billState = BillState.NONE,
            bill = null,
        )
        val records = listOf(paid, fault, unknownPhase, unmatched, noState)

        assertEquals(
            listOf(1L, 2L),
            records.applyHistoryFilter(
                HistoryFilter(
                    sessionResults = setOf(SessionResult.PAID, SessionResult.FAULT),
                ),
                now,
                zone,
            ).map { it.id },
        )
        assertEquals(
            listOf(1L),
            records.applyHistoryFilter(
                HistoryFilter(sessionResults = setOf(SessionResult.PAID)),
                now,
                zone,
            ).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L),
            records.applyHistoryFilter(
                HistoryFilter(billStates = setOf(BillState.MATCHED)),
                now,
                zone,
            ).map { it.id },
        )
        assertEquals(
            listOf(3L, 4L, 5L),
            records.applyHistoryFilter(
                HistoryFilter(billStates = setOf(BillState.UNMATCHED, BillState.NONE)),
                now,
                zone,
            ).map { it.id },
        )
    }

    @Test
    fun `payment groups map order statuses and exclude missing bills`() {
        val records = listOf(
            record(id = 1, bill = bill(orderStatus = 0)),
            record(id = 2, bill = bill(orderStatus = 1)),
            record(id = 3, bill = bill(orderStatus = 2)),
            record(id = 4, bill = bill(orderStatus = 3)),
            record(id = 5, bill = bill(orderStatus = 4)),
            record(id = 6, bill = bill(orderStatus = 5)),
            record(id = 7, bill = null, billState = BillState.NONE),
            record(id = 8, bill = bill(orderStatus = 99)),
        )

        fun ids(group: BillPaymentGroup) = records.applyHistoryFilter(
            HistoryFilter(paymentGroups = setOf(group)),
            now,
            zone,
        ).map { it.id }

        assertEquals(listOf(1L, 2L), ids(BillPaymentGroup.NEED_PAY))
        assertEquals(listOf(3L, 4L), ids(BillPaymentGroup.PAID_OR_COMPLETED))
        assertEquals(listOf(5L), ids(BillPaymentGroup.REFUNDING))
        assertEquals(listOf(6L), ids(BillPaymentGroup.REFUNDED))
    }

    @Test
    fun `exception filter covers session bill and ticket log errors`() {
        val clean = record(id = 1)
        val sessionError = record(id = 2, error = "设备异常")
        val billError = record(id = 3, billState = BillState.ERROR, bill = null)
        val ticketLogError = record(
            id = 4,
            bill = bill(ticket = ticket(), ticketLogError = "网络异常"),
        )
        val refreshOnly = record(
            id = 5,
            billError = "刷新失败",
            bill = bill(),
        )
        val records = listOf(clean, sessionError, billError, ticketLogError, refreshOnly)

        assertEquals(
            listOf(2L, 3L, 4L),
            records.applyHistoryFilter(HistoryFilter(exceptionsOnly = true), now, zone)
                .map { it.id },
        )
    }

    @Test
    fun `ticket usage unused requires a matched bill without ticket`() {
        val used = record(id = 1, bill = bill(ticket = ticket()))
        val unused = record(id = 2, bill = bill(ticket = null))
        val noBill = record(id = 3, bill = null, billState = BillState.NONE)
        val unmatched = record(id = 4, bill = null, billState = BillState.UNMATCHED)
        val errorWithCachedBill = record(
            id = 5,
            billState = BillState.ERROR,
            bill = bill(ticket = null),
        )
        val records = listOf(used, unused, noBill, unmatched, errorWithCachedBill)

        assertEquals(
            listOf(1L),
            records.applyHistoryFilter(
                HistoryFilter(ticketUsage = TicketUsage.USED),
                now,
                zone,
            ).map { it.id },
        )
        assertEquals(
            listOf(2L),
            records.applyHistoryFilter(
                HistoryFilter(ticketUsage = TicketUsage.UNUSED),
                now,
                zone,
            ).map { it.id },
        )
    }

    @Test
    fun `ticket flow shop and composition filter ticket-bearing records`() {
        val both = record(
            id = 1,
            bill = bill(
                ticket = ticket(
                    matchedLog = true,
                    principalPoints = 20,
                    presentPoints = 10,
                    shopName = "一号店",
                ),
            ),
        )
        val principalOnly = record(
            id = 2,
            bill = bill(
                ticket = ticket(
                    matchedLog = true,
                    principalPoints = 20,
                    presentPoints = 0,
                    shopName = "二号店",
                ),
            ),
        )
        val presentOnly = record(
            id = 3,
            bill = bill(
                ticket = ticket(
                    matchedLog = true,
                    principalPoints = 0,
                    presentPoints = 10,
                    shopName = "一号店",
                ),
            ),
        )
        val withoutSplit = record(
            id = 4,
            bill = bill(
                ticket = ticket(
                    matchedLog = false,
                    principalPoints = 0,
                    presentPoints = 0,
                    shopName = "",
                ),
            ),
        )
        val noTicket = record(id = 5, bill = bill(ticket = null))
        val records = listOf(both, principalOnly, presentOnly, withoutSplit, noTicket)

        fun ids(filter: HistoryFilter) = records.applyHistoryFilter(filter, now, zone).map { it.id }

        assertEquals(
            listOf(1L, 2L, 3L),
            ids(HistoryFilter(ticketLogMatches = setOf(TicketLogMatch.MATCHED))),
        )
        assertEquals(
            listOf(4L),
            ids(
                HistoryFilter(
                    ticketLogMatches = setOf(TicketLogMatch.UNMATCHED_OR_FAILED),
                ),
            ),
        )
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            ids(
                HistoryFilter(
                    ticketLogMatches = TicketLogMatch.entries.toSet(),
                ),
            ),
        )
        assertEquals(
            listOf(1L, 3L),
            ids(HistoryFilter(ticketShops = setOf("一号店"))),
        )
        assertEquals(
            listOf(1L),
            ids(HistoryFilter(ticketComposition = TicketComposition.BOTH)),
        )
        assertEquals(
            listOf(2L),
            ids(HistoryFilter(ticketComposition = TicketComposition.PRINCIPAL_ONLY)),
        )
        assertEquals(
            listOf(3L),
            ids(HistoryFilter(ticketComposition = TicketComposition.PRESENT_ONLY)),
        )
        assertEquals(
            listOf(4L),
            ids(
                HistoryFilter(
                    ticketComposition = TicketComposition.DEDUCTION_WITHOUT_SPLIT,
                ),
            ),
        )
    }

    @Test
    fun `amount ranges are inclusive and handle invalid or inverted input`() {
        val record = record(
            id = 1,
            bill = bill(
                paidAmount = "1.50",
                ticket = ticket(deductionYuan = "0.30"),
            ),
        )
        val records = listOf(record)

        fun matches(paid: AmountRange = AmountRange(), deduction: AmountRange = AmountRange()) =
            records.applyHistoryFilter(
                HistoryFilter(paidAmount = paid, ticketDeduction = deduction),
                now,
                zone,
            ).isNotEmpty()

        assertTrue(matches(paid = AmountRange("1.50", "1.50")))
        assertTrue(matches(paid = AmountRange("", "1.50")))
        assertTrue(matches(paid = AmountRange("1.50", "")))
        assertFalse(matches(paid = AmountRange("1.51", "")))
        assertFalse(matches(paid = AmountRange("", "1.49")))
        assertFalse(matches(paid = AmountRange("2.00", "1.00")))
        assertTrue(matches(deduction = AmountRange("0.30", "0.30")))
        assertFalse(matches(deduction = AmountRange("0.31", "")))
        assertTrue(AmountRange("x", "").hasInvalidInput)
        assertFalse(AmountRange("x", "").isActive)
        assertTrue(AmountRange("2", "1").isInverted)
    }

    @Test
    fun `chips expose active categories and can be removed`() {
        val filter = HistoryFilter(
            query = "search",
            timePreset = HistoryTimePreset.TODAY,
            devices = setOf("A"),
            sessionResults = setOf(SessionResult.PAID),
            billStates = setOf(BillState.MATCHED),
            paymentGroups = setOf(BillPaymentGroup.NEED_PAY),
            exceptionsOnly = true,
            ticketUsage = TicketUsage.USED,
            ticketLogMatches = setOf(TicketLogMatch.MATCHED),
            ticketShops = setOf("S"),
            ticketComposition = TicketComposition.BOTH,
            paidAmount = AmountRange("1", "2"),
            ticketDeduction = AmountRange("0.3", "0.5"),
        )

        assertEquals(12, filter.activeCategoryCount)
        assertEquals(12, filter.chips().size)
        assertTrue(filter.hasActiveConstraints)

        assertTrue(filter.removeChip(ActiveFilterChip.Device("A")).devices.isEmpty())
        assertEquals(
            HistoryTimePreset.ALL,
            filter.removeChip(filter.chips().first { it is ActiveFilterChip.Time }).timePreset,
        )
        assertFalse(
            filter.removeChip(ActiveFilterChip.PaidRange).paidAmount.isActive,
        )
        assertEquals(
            TicketUsage.ALL,
            filter.removeChip(ActiveFilterChip.TicketUsageChip(TicketUsage.USED)).ticketUsage,
        )
    }

    @Test
    fun `synthetic order number is hidden from display`() {
        assertEquals("", record(orderNo = "local-1").displayOrderNo())
        assertEquals("real", record(orderNo = "real").displayOrderNo())
    }

    private fun timeAt(dayOffset: Long, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.of(2026, 10, 25)
            .minusDays(dayOffset)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun record(
        id: Long = 1,
        accountId: String = "account-1",
        deviceName: String = "一号机",
        phase: String = SessionPhase.PAID,
        billState: BillState = BillState.MATCHED,
        bill: BillInfo? = bill(),
        error: String = "",
        billError: String = "",
        orderNo: String = "unlock-1",
        payOrderNo: String = "pay-1",
        updatedAt: Long = now,
    ): HistoryRecord = HistoryRecord(
        id = id,
        accountId = accountId,
        goodsId = "goods-1",
        deviceName = deviceName,
        skuId = "sku-1",
        amount = "1.00",
        orderNo = orderNo,
        payOrderNo = payOrderNo,
        payOrderId = "",
        identify = "",
        phase = phase,
        error = error,
        startedAt = updatedAt - 60_000,
        updatedAt = updatedAt,
        billState = billState,
        bill = bill,
        billError = billError,
        billFetchedAt = updatedAt,
    )

    private fun bill(
        orderStatus: Int = 2,
        shopName: String = "一号店",
        machineName: String = "一号机",
        payTypeName: String = "校园卡",
        paidAmount: String = "1.50",
        promotions: List<BillDiscount> = emptyList(),
        ticket: BillTicket? = null,
        ticketLogError: String = "",
    ): BillInfo = BillInfo(
        orderId = "bill-id-1",
        orderNo = "bill-1",
        orderStatus = orderStatus,
        originalAmount = "2.00",
        payAmount = "1.50",
        paidAmount = paidAmount,
        promotions = promotions,
        ticket = ticket,
        ticketLogError = ticketLogError,
        shopName = shopName,
        machineName = machineName,
        payTypeName = payTypeName,
    )

    private fun ticket(
        matchedLog: Boolean = true,
        principalPoints: Int = 20,
        presentPoints: Int = 10,
        points: Int = 30,
        deductionYuan: String = "0.30",
        shopName: String = "一号店",
        remark: String = "接水抵扣",
    ): BillTicket = BillTicket(
        billPoints = points,
        points = points,
        principalPoints = principalPoints,
        presentPoints = presentPoints,
        deductionYuan = deductionYuan,
        shopName = shopName,
        remark = remark,
        matchedLog = matchedLog,
        logPoints = if (matchedLog) points else 0,
    )
}
