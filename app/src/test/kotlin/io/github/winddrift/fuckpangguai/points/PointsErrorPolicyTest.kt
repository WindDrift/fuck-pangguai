package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.ApiException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class PointsErrorPolicyTest {

    @Test
    fun `io errors count as network failures`() {
        assertTrue(isNetworkFailure(IOException("connection reset")))
        assertTrue(isNetworkFailure(SocketTimeoutException("timeout")))
    }

    @Test
    fun `rate limit http codes count as network failures`() {
        assertTrue(isNetworkFailure(http(405)))
        assertTrue(isNetworkFailure(http(429)))
        assertTrue(isNetworkFailure(http(503)))
    }

    @Test
    fun `business and auth failures do not count`() {
        assertFalse(isNetworkFailure(http(400)))
        assertFalse(isNetworkFailure(ApiException(2, "登录失效")))
        assertFalse(isNetworkFailure(ApiException(10, "需要验证")))
        assertFalse(isNetworkFailure(ApiException(-1, "业务失败")))
        assertFalse(isNetworkFailure(IllegalStateException("boom")))
    }

    @Test
    fun `server side api failures count as network failures`() {
        assertTrue(isNetworkFailure(ApiException(500, "服务异常")))
        assertFalse(isNetworkFailure(ApiException(404, "任务不存在")))
    }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))
}