package io.github.winddrift.fuckpangguai.update

import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateControllerTest {

    @Test
    fun autoCheckFailureIsSilent() = runTest {
        val controller = UpdateController(
            scope = this,
            source = FakeSource { throw UnknownHostException("offline") },
            currentVersion = "1.10.1-7f0c885",
        )

        controller.checkOnStart()
        advanceUntilIdle()

        assertEquals(UpdateState.Idle, controller.state.value)
    }

    @Test
    fun manualCheckFailureExposesReason() = runTest {
        val controller = UpdateController(
            scope = this,
            source = FakeSource { throw UnknownHostException("offline") },
            currentVersion = "1.10.1-7f0c885",
        )

        controller.checkManually()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed(UpdateError.NoNetwork), controller.state.value)
    }

    @Test
    fun reportsAvailableWhenRemoteIsNewer() = runTest {
        val controller = UpdateController(
            scope = this,
            source = FakeSource {
                GithubRelease(
                    tagName = "v1.10.2",
                    body = "## 修复\n- 修好问题",
                    htmlUrl = "https://example.com/release",
                )
            },
            currentVersion = "1.10.1-7f0c885",
        )

        controller.checkManually()
        advanceUntilIdle()

        assertEquals(
            UpdateState.Available(
                version = "1.10.2",
                tag = "v1.10.2",
                notes = "修复\n• 修好问题",
                releaseUrl = "https://example.com/release",
            ),
            controller.state.value,
        )
    }

    @Test
    fun reportsUpToDateWhenRemoteIsNotNewer() = runTest {
        val controller = UpdateController(
            scope = this,
            source = FakeSource { GithubRelease(tagName = "v1.10.1") },
            currentVersion = "1.10.1-7f0c885",
        )

        controller.checkManually()
        advanceUntilIdle()

        assertEquals(UpdateState.UpToDate, controller.state.value)
    }

    @Test
    fun autoCheckRunsOnlyOncePerProcess() = runTest {
        val source = FakeSource { GithubRelease(tagName = "v1.10.1") }
        val controller = UpdateController(this, source, "1.10.1-7f0c885")

        controller.checkOnStart()
        advanceUntilIdle()
        controller.checkOnStart()
        advanceUntilIdle()

        assertEquals(1, source.calls)
    }

    @Test
    fun repeatedManualClicksDoNotStartParallelChecks() = runTest {
        val source = GatedSource()
        val controller = UpdateController(this, source, "1.10.1-7f0c885")

        controller.checkManually()
        runCurrent()
        assertEquals(UpdateState.Checking, controller.state.value)

        controller.checkManually()
        source.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, source.calls)
        assertEquals(UpdateState.Available::class.java, controller.state.value::class.java)
    }

    @Test
    fun invalidRemoteVersionFailsManualCheck() = runTest {
        val controller = UpdateController(
            scope = this,
            source = FakeSource { GithubRelease(tagName = "nightly") },
            currentVersion = "1.10.1-7f0c885",
        )

        controller.checkManually()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed(UpdateError.Unknown), controller.state.value)
    }

    private class FakeSource(
        private val block: suspend () -> GithubRelease,
    ) : ReleaseSource {
        var calls = 0

        override suspend fun latest(): GithubRelease {
            calls += 1
            return block()
        }
    }

    private class GatedSource : ReleaseSource {
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        override suspend fun latest(): GithubRelease {
            calls += 1
            gate.await()
            return GithubRelease(
                tagName = "v1.10.2",
                htmlUrl = "https://example.com/release",
            )
        }
    }
}
