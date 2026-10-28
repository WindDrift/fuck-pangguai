package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.diagnostics.FakeDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.nfc.NfcTagBinding
import io.github.winddrift.fuckpangguai.nfc.NfcTagSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanControllerTraceTest {

    private var now = 10_000L

    @Test
    fun `gallery input records decode time and resolved device trace id`() = runTest {
        val sink = FakeDiagSink()
        val source = FakeScanDeviceSource()
        val controller = controller(this, source, sink)

        controller.onQr(
            ScanInput(
                raw = deviceQr("860"),
                source = ScanSource.Gallery,
                decodeMs = 120,
            ),
        )
        advanceUntilIdle()

        val trace = sink.traces.values.single()
        assertEquals("相册", trace.source)
        assertEquals(10_000L - 120, trace.startedAt)
        val decode = trace.stage(TraceStage.ScanDecode)!!
        assertEquals(StageOutcome.Success, decode.outcome)
        assertEquals(120L, decode.durationMs)
        assertEquals(1, decode.attempts)
        val resolve = trace.stage(TraceStage.DeviceResolve)!!
        assertEquals(StageOutcome.Success, resolve.outcome)
        assertEquals(2, resolve.attempts)
        assertEquals("goods-860", resolve.fields["goodsId"])

        val ready = controller.state.value as ScanState.DeviceReady
        assertEquals(trace.traceId, ready.device.traceId)
        assertEquals(TraceOutcome.Running, trace.outcome)
    }

    @Test
    fun `manual input records zero decode duration`() = runTest {
        val sink = FakeDiagSink()
        val controller = controller(this, FakeScanDeviceSource(), sink)

        controller.onQr(ScanInput(deviceQr("861"), ScanSource.Manual))
        advanceUntilIdle()

        val decode = sink.traces.values.single().stage(TraceStage.ScanDecode)!!
        assertEquals(0L, decode.durationMs)
        assertEquals(StageOutcome.Success, decode.outcome)
    }

    @Test
    fun `reset while resolving abandons the trace and ignores the late result`() = runTest {
        val sink = FakeDiagSink()
        val source = FakeScanDeviceSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val controller = controller(this, source, sink)

        controller.onQr(ScanInput(deviceQr("862"), ScanSource.Qr))
        advanceUntilIdle()
        assertTrue(controller.state.value is ScanState.Resolving)

        controller.reset()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val trace = sink.traces.values.single()
        assertEquals(TraceOutcome.Abandoned, trace.outcome)
        assertEquals(StageOutcome.Abandoned, trace.stage(TraceStage.DeviceResolve)!!.outcome)
        assertTrue(controller.state.value is ScanState.Idle)
    }

    @Test
    fun `new input abandons the previous trace and old result cannot overwrite`() = runTest {
        val sink = FakeDiagSink()
        val source = FakeScanDeviceSource()
        val firstGate = CompletableDeferred<Unit>()
        source.gate = firstGate
        val controller = controller(this, source, sink)

        controller.onQr(ScanInput(deviceQr("863"), ScanSource.Qr))
        advanceUntilIdle()
        val firstTraceId = controller.state.value.let {
            sink.currentTrace()!!.traceId
        }

        source.gate = null
        controller.onQr(ScanInput(deviceQr("864"), ScanSource.Qr))
        advanceUntilIdle()

        val ready = controller.state.value as ScanState.DeviceReady
        assertEquals("goods-864", ready.device.scan.id)
        assertEquals(TraceOutcome.Abandoned, sink.trace(firstTraceId).outcome)
        assertEquals(2, sink.traces.size)

        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("goods-864", (controller.state.value as ScanState.DeviceReady).device.scan.id)
    }

    @Test
    fun `resolve failure marks the trace failed`() = runTest {
        val sink = FakeDiagSink()
        val source = FakeScanDeviceSource().apply { fail = true }
        val controller = controller(this, source, sink)

        controller.onQr(ScanInput(deviceQr("865"), ScanSource.Qr))
        advanceUntilIdle()

        val trace = sink.traces.values.single()
        assertEquals(TraceOutcome.Failed, trace.outcome)
        assertEquals(StageOutcome.Failed, trace.stage(TraceStage.DeviceResolve)!!.outcome)
        assertTrue(controller.state.value is ScanState.Message)
    }

    @Test
    fun `handing the trace to the confirm page does not abandon it`() = runTest {
        val sink = FakeDiagSink()
        val controller = controller(this, FakeScanDeviceSource(), sink)

        controller.onQr(ScanInput(deviceQr("866"), ScanSource.Qr))
        advanceUntilIdle()

        controller.reset(keepTrace = true)

        val trace = sink.traces.values.single()
        assertEquals(TraceOutcome.Running, trace.outcome)
        assertEquals(StageOutcome.Success, trace.stage(TraceStage.DeviceResolve)!!.outcome)
        assertTrue(controller.state.value is ScanState.Idle)
    }

    @Test
    fun `nfc tag binding resolves the mapped sn and keeps a trace`() = runTest {
        val sink = FakeDiagSink()
        val source = FakeScanDeviceSource()
        val tags = FakeNfcTagSource(
            bindings = listOf(
                NfcTagBinding(
                    tagId = "tag-1",
                    sn = "867",
                    deviceName = "水机",
                    goodsId = "goods-867",
                    updatedAt = 0L,
                ),
            ),
        )
        val controller = ScanController(this, source, tags, sink) { now }

        controller.onNfc(
            io.github.winddrift.fuckpangguai.nfc.NfcPayload(
                rawUrl = "alipays://platformapi/startapp?appId=test&t=tag-1",
                uid = null,
                tagId = "tag-1",
                alipayServiceId = "test",
                sn = null,
                nqt = null,
                imei = null,
            ),
        )
        advanceUntilIdle()

        val trace = sink.traces.values.single()
        assertEquals("NFC", trace.source)
        assertEquals(0L, trace.stage(TraceStage.ScanDecode)!!.durationMs)
        val ready = controller.state.value as ScanState.DeviceReady
        assertNotNull(ready.device.traceId)
    }

    private fun controller(
        scope: kotlinx.coroutines.CoroutineScope,
        source: FakeScanDeviceSource,
        sink: FakeDiagSink,
    ) = ScanController(
        scope = scope,
        deviceRepository = source,
        nfcTagStore = FakeNfcTagSource(),
        diagnostics = sink,
        clock = { now },
    )

    private fun deviceQr(sn: String) = "https://example.test/q?SN=$sn"

    private class FakeScanDeviceSource : ScanDeviceSource {
        var gate: CompletableDeferred<Unit>? = null
        var fail = false

        override suspend fun resolve(key: String, value: String): ResolvedDevice {
            gate?.await()
            if (fail) error("resolve failed")
            return ResolvedDevice(
                scan = ScanV2Bean(
                    categoryCode = "04",
                    categoryName = "饮水机",
                    id = "goods-$value",
                ),
                detail = GoodsDetail(
                    goodsId = "goods-$value",
                    imei = "imei-$value",
                    categoryCode = "04",
                    orgId = "org-1",
                    shopId = "shop-1",
                    name = "水机",
                    supportAppUserFinish = true,
                ),
                sourceKey = key,
                sourceValue = value,
            )
        }

        override suspend fun prefetchPlans(device: ResolvedDevice) = Unit
    }

    private class FakeNfcTagSource(
        private val bindings: List<NfcTagBinding> = emptyList(),
    ) : NfcTagSource {
        override suspend fun find(tagId: String): NfcTagBinding? =
            bindings.firstOrNull { it.tagId == tagId }

        override suspend fun bind(binding: NfcTagBinding) = Unit
    }
}
