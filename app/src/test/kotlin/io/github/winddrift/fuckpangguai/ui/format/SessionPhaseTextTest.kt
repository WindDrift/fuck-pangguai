package io.github.winddrift.fuckpangguai.ui.format

import io.github.winddrift.fuckpangguai.water.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPhaseTextTest {

    @Test
    fun `using shows in progress status`() {
        assertEquals("接水进行中", sessionStatusLabel(SessionPhase.USING))
    }

    @Test
    fun `ending and settling do not look like using`() {
        assertEquals("正在结束", sessionStatusLabel(SessionPhase.ENDING))
        assertEquals("正在结算", sessionStatusLabel(SessionPhase.SETTLING))
    }

    @Test
    fun `terminal phases use phase labels`() {
        assertEquals("已支付", sessionStatusLabel(SessionPhase.PAID))
        assertEquals("已结束（无费用）", sessionStatusLabel(SessionPhase.FINISHED_NO_CHARGE))
        assertEquals("支付状态待确认", sessionStatusLabel(SessionPhase.PAY_UNKNOWN))
        assertEquals("设备异常", sessionStatusLabel(SessionPhase.FAULT))
    }
}
