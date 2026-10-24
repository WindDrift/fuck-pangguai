package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.UserBalance
import io.github.winddrift.fuckpangguai.net.dto.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `generic sku list response parses`() {
        val raw = """
            {"code":0,"msg":"","data":[
              {"skuId":"sku-1","name":"饮用水","price":"0.2","unknown":"ignored"},
              {"skuId":"sku-2","name":"套餐","discountPrice":"1.0"}
            ],"t":123}
        """.trimIndent()
        val response = json.decodeFromString<ApiResponse<List<Sku>>>(raw)
        assertTrue(response.isSuccess)
        assertEquals(2, response.data?.size)
        assertEquals("sku-1", response.data?.first()?.skuId)
        assertEquals("饮用水", response.data?.first()?.name)
    }

    @Test
    fun `business error is not success`() {
        val response = json.decodeFromString<ApiResponse<String>>(
            """{"code":401,"msg":"登录失效","data":null}""",
        )
        assertEquals(false, response.isSuccess)
        assertEquals("登录失效", response.msg)
    }

    @Test
    fun `profile ignores sensitive fields`() {
        val raw = """
            {"code":0,"data":{
              "id":"10001","userName":"小明","phone":"13800000000",
              "headImageId":"https://img.example/avatar.png",
              "realName":"张三","idCardNo":"110101199001010000"
            }}
        """.trimIndent()

        val profile = json.decodeFromString<ApiResponse<UserProfile>>(raw).data

        assertEquals("10001", profile?.id)
        assertEquals("小明", profile?.userName)
        assertEquals("13800000000", profile?.phone)
        assertEquals("https://img.example/avatar.png", profile?.headImageId)
    }

    @Test
    fun `balance and ticket list parse`() {
        val balance = json.decodeFromString<ApiResponse<UserBalance>>(
            """{"code":0,"data":{"balance":"12.34","tokenCoin":5678,"integral":"90","shopTokenCoinSet":true}}""",
        ).data
        assertEquals("12.34", balance?.balance)
        assertEquals(5678, balance?.tokenCoin)
        assertEquals("90", balance?.integral)
        assertEquals(true, balance?.shopTokenCoinSet)

        val tickets = json.decodeFromString<ApiResponse<ItemsList<Ticket>>>(
            """{"code":0,"data":{"total":2,"items":[
                {"id":"1","shopId":"10","shopName":"一号店","totalAmount":150,"principalAmount":100,"presentAmount":50},
                {"id":"2","shopId":"20","shopName":"二号店","totalAmount":250,"principalAmount":200,"presentAmount":50}
            ]}}""",
        ).data
        assertEquals(2, tickets?.total)
        assertEquals("二号店", tickets?.items?.last()?.shopName)
        assertEquals(250, tickets?.items?.last()?.totalAmount)
    }
}
