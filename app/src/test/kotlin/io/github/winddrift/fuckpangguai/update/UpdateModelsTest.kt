package io.github.winddrift.fuckpangguai.update

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateModelsTest {

    @Test
    fun decodesGithubReleaseJson() {
        val json = """
            {
              "tag_name": "v1.11.0",
              "body": "## 更新内容",
              "html_url": "https://github.com/WindDrift/fuck-pangguai/releases/tag/v1.11.0",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()

        val release = Json { ignoreUnknownKeys = true }
            .decodeFromString(GithubRelease.serializer(), json)

        assertEquals("v1.11.0", release.tagName)
        assertEquals("## 更新内容", release.body)
        assertEquals(
            "https://github.com/WindDrift/fuck-pangguai/releases/tag/v1.11.0",
            release.htmlUrl,
        )
    }

    @Test
    fun mapsNetworkExceptionsToFriendlyErrors() {
        assertEquals(UpdateError.NoNetwork, UnknownHostException().toUpdateError())
        assertEquals(UpdateError.NoNetwork, ConnectException().toUpdateError())
        assertEquals(UpdateError.Timeout, SocketTimeoutException().toUpdateError())
        assertEquals(UpdateError.RateLimited, GithubHttpException(403, true).toUpdateError())
        assertEquals(UpdateError.Http(500), GithubHttpException(500, false).toUpdateError())
        assertEquals(UpdateError.Unknown, IllegalStateException().toUpdateError())
    }

    @Test
    fun rendersFriendlyMessages() {
        assertEquals("网络不可用", UpdateError.NoNetwork.userMessage())
        assertEquals("请求超时", UpdateError.Timeout.userMessage())
        assertEquals("GitHub 请求过于频繁", UpdateError.RateLimited.userMessage())
        assertEquals("GitHub 返回 HTTP 503", UpdateError.Http(503).userMessage())
        assertEquals("检查失败", UpdateError.Unknown.userMessage())
    }
}
