package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.Sku
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
}
