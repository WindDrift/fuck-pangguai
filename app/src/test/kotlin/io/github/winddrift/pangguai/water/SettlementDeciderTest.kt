package io.github.winddrift.pangguai.water

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementDeciderTest {

    private fun session(
        amount: String,
        identify: String,
        attempted: Boolean = false,
    ) = WaterSession(
        goodsId = "goods",
        skuId = "sku",
        deviceName = "水机",
        imei = "imei",
        orderNo = "order",
        msgId = "msg",
        supportAppUserFinish = true,
        startedAt = 1L,
        amount = amount,
        identify = identify,
        settleAttempted = attempted,
    )

    @Test
    fun `zero amount never creates an order`() {
        assertEquals(
            SettlementDecider.Decision.NoCharge,
            SettlementDecider.decide(session("0", "identify")),
        )
    }

    @Test
    fun `settle attempted never repeats creation`() {
        assertEquals(
            SettlementDecider.Decision.AlreadyAttempted,
            SettlementDecider.decide(session("1.25", "identify", attempted = true)),
        )
    }

    @Test
    fun `positive amount creates once`() {
        assertEquals(
            SettlementDecider.Decision.CreateOrder("identify"),
            SettlementDecider.decide(session("1.25", "identify")),
        )
    }

    @Test
    fun `session survives json round trip`() {
        val original = session("1.25", "identify")
        val encoded = Json.encodeToString(WaterSession.serializer(), original)
        val decoded = Json.decodeFromString(WaterSession.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(SessionPhase.USING in SessionPhase.ACTIVE)
        assertFalse(SessionPhase.USING in SessionPhase.TERMINAL)
    }
}
