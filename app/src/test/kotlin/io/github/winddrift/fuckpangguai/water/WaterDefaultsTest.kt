package io.github.winddrift.fuckpangguai.water

import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WaterDefaultsTest {

    @Test
    fun `default promotions contain the three negative markers`() {
        val array = Json.parseToJsonElement(WaterDefaults.PROMOTIONS).jsonArray
        assertEquals(3, array.size)
        assertEquals(
            listOf("-6", "-7", "-8"),
            array.map { it.jsonObject["promotionType"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `points promotion switches from minus eight to positive eight`() {
        val array = Json.parseToJsonElement(WaterDefaults.promotions(usePoints = true)).jsonArray
        assertEquals(
            listOf("-6", "-7", "8"),
            array.map { it.jsonObject["promotionType"]!!.jsonPrimitive.content },
        )
        assertEquals(
            "0",
            array.last().jsonObject["oldPromotionId"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `payType only for one card 1506 or 1507`() {
        assertEquals("1501", WaterDefaults.payTypeFor(PayChannelRoute(method = 1501, thirdMethod = 1506)))
        assertEquals("1501", WaterDefaults.payTypeFor(PayChannelRoute(method = 1501, thirdMethod = 1507)))
        assertEquals("", WaterDefaults.payTypeFor(PayChannelRoute(method = 1501, thirdMethod = 1505)))
        assertEquals("", WaterDefaults.payTypeFor(PayChannelRoute(method = 15)))
    }
}
