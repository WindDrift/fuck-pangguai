package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException

class NetworkFailureClassifyTest {

    @Test
    fun `canceled io exception is classified as canceled and logged at debug`() {
        val failure = classifyNetworkFailure(IOException("Canceled"))

        assertEquals(NetworkFailureKind.Canceled, failure)
        assertEquals(DiagLevel.Debug, networkEventLevel(null, failure, null, "machine/msg/sync"))
    }

    @Test
    fun `socket closed is treated as a client cancellation`() {
        assertEquals(
            NetworkFailureKind.Canceled,
            classifyNetworkFailure(SocketException("Socket closed")),
        )
    }

    @Test
    fun `socket timeout is classified as timeout and logged at warn`() {
        val failure = classifyNetworkFailure(SocketTimeoutException("timeout"))

        assertEquals(NetworkFailureKind.Timeout, failure)
        // 超时属于可重试的网络抖动，记 Warn 并带 timeout 标记。
        assertEquals(DiagLevel.Warn, networkEventLevel(null, failure, null, "goods/water/sync"))
    }

    @Test
    fun `interrupted io without cancel wording is a timeout`() {
        assertEquals(
            NetworkFailureKind.Timeout,
            classifyNetworkFailure(InterruptedIOException("stream interrupted")),
        )
    }

    @Test
    fun `plain io failure is logged at warn`() {
        val failure = classifyNetworkFailure(IOException("Failed to connect"))

        assertEquals(NetworkFailureKind.Io, failure)
        // 连接失败同样是可重试的网络错误，记 Warn；未知异常才保留 Error。
        assertEquals(DiagLevel.Warn, networkEventLevel(null, failure, null, "goods/water/sync"))
    }

    @Test
    fun `server error keeps error level`() {
        assertEquals(DiagLevel.Error, networkEventLevel(503, null, null, "task/list"))
    }

    @Test
    fun `client error is warn and core success is info`() {
        assertEquals(DiagLevel.Warn, networkEventLevel(405, null, null, "task/completed"))
        assertEquals(DiagLevel.Info, networkEventLevel(200, null, 0, "goods/water/unlock"))
    }

    @Test
    fun `business failure is warn and polling success is debug`() {
        assertEquals(DiagLevel.Warn, networkEventLevel(200, null, 2, "goods/water/sync"))
        assertEquals(DiagLevel.Debug, networkEventLevel(200, null, 0, "machine/msg/sync"))
    }

    @Test
    fun `other throwables are neither canceled nor network`() {
        assertEquals(
            NetworkFailureKind.Other,
            classifyNetworkFailure(IllegalStateException("boom")),
        )
        // 未知异常仍是最严重的级别，不能跟着 IO 一起降噪。
        assertEquals(
            DiagLevel.Error,
            networkEventLevel(null, NetworkFailureKind.Other, null, "goods/water/sync"),
        )
    }

    @Test
    fun `coroutine cancellation is classified as canceled`() {
        val failure = classifyNetworkFailure(CancellationException("job was cancelled"))

        assertEquals(NetworkFailureKind.Canceled, failure)
        assertEquals(DiagLevel.Debug, networkEventLevel(null, failure, null, "goods/water/sync"))
    }

    @Test
    fun `timeout cancellation is classified as canceled`() = runTest {
        // TimeoutCancellationException 的构造函数是 internal，只能靠真实超时拿到。
        val thrown = runCatching {
            withTimeout(1) { kotlinx.coroutines.delay(1_000) }
        }.exceptionOrNull()

        assertTrue(thrown is TimeoutCancellationException)
        assertEquals(NetworkFailureKind.Canceled, classifyNetworkFailure(thrown!!))
    }

    @Test
    fun `wrapped cancellation is found through the cause chain`() {
        val wrapped = RuntimeException("call failed", CancellationException("Canceled"))

        assertEquals(NetworkFailureKind.Canceled, classifyNetworkFailure(wrapped))
    }

    @Test
    fun `wrapped timeout is found through the cause chain`() {
        val wrapped = RuntimeException("call failed", SocketTimeoutException("timeout"))

        assertEquals(NetworkFailureKind.Timeout, classifyNetworkFailure(wrapped))
        assertEquals(
            DiagLevel.Warn,
            networkEventLevel(null, NetworkFailureKind.Timeout, null, "goods/water/sync"),
        )
    }
}