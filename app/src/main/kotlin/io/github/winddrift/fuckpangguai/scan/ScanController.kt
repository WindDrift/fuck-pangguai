package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.device.QrParser
import io.github.winddrift.fuckpangguai.device.QrTarget
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.NoopDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.nfc.NfcPayload
import io.github.winddrift.fuckpangguai.nfc.NfcTagBinding
import io.github.winddrift.fuckpangguai.nfc.NfcTagSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanSource(
    val label: String,
) {
    Qr("相机"),
    Gallery("相册"),
    Manual("手动"),
    Nfc("NFC"),
}

data class ScanInput(
    val raw: String,
    val source: ScanSource,
    val decodeMs: Long = 0L,
    val attempts: Int = 1,
)

sealed interface ScanState {
    data object Idle : ScanState

    /** 已经识别到输入、正在向服务端解析设备；界面需要在识别瞬间给出明确反馈。 */
    data class Resolving(val source: ScanSource) : ScanState

    data class DeviceReady(val device: ResolvedDevice) : ScanState
    data class NeedsBind(
        val tagId: String,
        val payload: NfcPayload,
    ) : ScanState

    data class ConfirmBind(
        val tagId: String,
        val device: ResolvedDevice,
        val binding: Boolean = false,
    ) : ScanState

    data class Message(
        val text: String,
        val rawUrl: String? = null,
    ) : ScanState
}

/**
 * 相机识别结果是否应该交给控制器。
 *
 * NeedsBind 本身就是要求用户扫设备码来完成 NFC 标签绑定，不能在此状态停用相机；
 * 其余非 Idle 状态已经有结果或正在请求，继续扫码只会重复触发。
 */
internal fun ScanState.acceptsCameraScan(): Boolean =
    this is ScanState.Idle || this is ScanState.NeedsBind

interface ScanDeviceSource {
    suspend fun resolve(key: String, value: String): ResolvedDevice
    suspend fun prefetchPlans(device: ResolvedDevice)
}

class ScanController(
    private val scope: CoroutineScope,
    private val deviceRepository: ScanDeviceSource,
    private val nfcTagStore: NfcTagSource,
    private val diagnostics: DiagSink = NoopDiagSink,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state = _state.asStateFlow()

    @Volatile
    private var pendingTagId: String? = null

    private var generation = 0
    private var currentTraceId: String? = null
    private var traceHandedOff = false

    fun onQr(raw: String) {
        onQr(ScanInput(raw, ScanSource.Qr))
    }

    fun onQr(input: ScanInput) {
        if (_state.value.isBinding) return
        when (val target = QrParser.parse(input.raw)) {
            is QrTarget.Device -> resolve(target.key, target.value, pendingTagId, input)
            is QrTarget.Unsupported -> {
                diagnostics.event(
                    DiagLevel.Warn,
                    DiagFlow.Scan,
                    "qr_unsupported",
                    message = "无法识别该二维码",
                    fields = mapOf("source" to input.source.label),
                )
                abandonCurrentTrace("无法识别的新输入", evenIfHandedOff = true)
                _state.value = ScanState.Message("无法识别该二维码，请对准设备上的二维码")
            }
        }
    }

    fun onNfc(payload: NfcPayload) {
        if (_state.value.isBinding) return
        val sn = payload.sn
        if (!sn.isNullOrBlank()) {
            resolve("SN", sn, null, ScanInput(payload.rawUrl, ScanSource.Nfc))
            return
        }
        val nqt = payload.nqt
        if (!nqt.isNullOrBlank()) {
            resolve("NQT", nqt, null, ScanInput(payload.rawUrl, ScanSource.Nfc))
            return
        }
        val imei = payload.imei
        if (!imei.isNullOrBlank()) {
            resolve("IMEI", imei, null, ScanInput(payload.rawUrl, ScanSource.Nfc))
            return
        }
        val tagId = payload.tagId
        if (!tagId.isNullOrBlank()) {
            _state.value = ScanState.Resolving(ScanSource.Nfc)
            scope.launch {
                try {
                    val binding = nfcTagStore.find(tagId)
                    if (binding == null) {
                        pendingTagId = tagId
                        _state.value = ScanState.NeedsBind(tagId, payload)
                        diagnostics.event(
                            DiagLevel.Debug,
                            DiagFlow.Scan,
                            "nfc_needs_bind",
                        )
                    } else {
                        val input = ScanInput(payload.rawUrl, ScanSource.Nfc)
                        val traceId = beginTrace(input)
                        resolveNow("SN", binding.sn, null, traceId, generation)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    diagnostics.event(
                        DiagLevel.Warn,
                        DiagFlow.Scan,
                        "nfc_tag_failed",
                        message = throwable.userMessage(),
                    )
                    _state.value = ScanState.Message(throwable.userMessage())
                }
            }
            return
        }
        _state.value = ScanState.Message(
            text = "这个标签里没有可识别的设备信息",
            rawUrl = payload.rawUrl.takeIf { it.isNotBlank() },
        )
    }

    fun confirmBind() {
        val current = _state.value
        if (current !is ScanState.ConfirmBind || current.binding) return
        // 先同步锁定状态，避免连点或绑定完成前再次发起写入。
        _state.value = current.copy(binding = true)
        scope.launch {
            try {
                val detail = current.device.detail
                nfcTagStore.bind(
                    NfcTagBinding(
                        tagId = current.tagId,
                        sn = if (current.device.sourceKey == "SN") {
                            current.device.sourceValue
                        } else {
                            detail.imei
                        },
                        deviceName = detail.name,
                        goodsId = current.device.scan.id,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                if (!isBindingCurrent(current.tagId)) return@launch
                pendingTagId = null
                _state.value = ScanState.DeviceReady(current.device)
                traceHandedOff = true
                diagnostics.event(
                    DiagLevel.Info,
                    DiagFlow.Scan,
                    "nfc_bind_ok",
                    traceId = current.device.traceId,
                    fields = mapOf(
                        "goodsId" to current.device.scan.id,
                        "deviceName" to detail.name,
                    ),
                )
                prefetchPlans(current.device)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isBindingCurrent(current.tagId)) {
                    diagnostics.event(
                        DiagLevel.Warn,
                        DiagFlow.Scan,
                        "nfc_bind_fail",
                        message = throwable.userMessage(),
                        traceId = current.device.traceId,
                        fields = mapOf("goodsId" to current.device.scan.id),
                    )
                    _state.value = ScanState.Message(throwable.userMessage())
                }
            }
        }
    }

    fun skipBind() {
        val current = _state.value
        if (current !is ScanState.ConfirmBind || current.binding) return
        pendingTagId = null
        _state.value = ScanState.DeviceReady(current.device)
        traceHandedOff = true
        diagnostics.event(
            DiagLevel.Debug,
            DiagFlow.Scan,
            "nfc_bind_skipped",
            traceId = current.device.traceId,
        )
        prefetchPlans(current.device)
    }

    /**
     * 重置扫码页状态。
     *
     * [keepTrace] 用于“跳过设备信息”直接进入确认页的交接路径；
     * 其余情况说明这次扫码不会继续，trace 统一标为放弃。
     */
    fun reset(keepTrace: Boolean = false) {
        if (keepTrace) {
            currentTraceId = null
            traceHandedOff = false
        } else {
            abandonCurrentTrace("扫码页重置", evenIfHandedOff = true)
        }
        generation += 1
        pendingTagId = null
        _state.value = ScanState.Idle
    }

    private val ScanState.isBinding: Boolean
        get() = this is ScanState.ConfirmBind && binding

    private fun isBindingCurrent(tagId: String): Boolean {
        val current = _state.value
        return current is ScanState.ConfirmBind &&
            current.tagId == tagId &&
            current.binding
    }

    fun showMessage(text: String, rawUrl: String? = null) {
        _state.value = ScanState.Message(text, rawUrl)
    }

    private fun resolve(
        key: String,
        value: String,
        bindTagId: String?,
        input: ScanInput,
    ) {
        // 先同步切状态，让界面在识别到的瞬间就能给出成功反馈，再开始网络请求。
        _state.value = ScanState.Resolving(input.source)
        val traceId = beginTrace(input)
        val gen = generation
        scope.launch {
            resolveNow(key, value, bindTagId, traceId, gen)
        }
    }

    private suspend fun resolveNow(
        key: String,
        value: String,
        bindTagId: String?,
        traceId: String,
        gen: Int,
    ) {
        try {
            val resolved = deviceRepository.resolve(key, value)
            if (gen != generation || currentTraceId != traceId) return
            val device = resolved.copy(traceId = traceId)
            traceHandedOff = true
            diagnostics.stageFinished(
                traceId = traceId,
                stage = TraceStage.DeviceResolve,
                outcome = StageOutcome.Success,
                endedAt = clock(),
                attempts = DEVICE_RESOLVE_ATTEMPTS,
                fields = mapOf(
                    "goodsId" to device.scan.id,
                    "deviceName" to device.detail.name,
                    "sourceKey" to device.sourceKey,
                    "category" to device.scan.categoryCode,
                ),
            )
            diagnostics.event(
                DiagLevel.Info,
                DiagFlow.Device,
                "resolve_ok",
                traceId = traceId,
                fields = mapOf(
                    "goodsId" to device.scan.id,
                    "deviceName" to device.detail.name,
                    "sourceKey" to device.sourceKey,
                ),
            )
            if (bindTagId != null) {
                _state.value = ScanState.ConfirmBind(bindTagId, device)
            } else {
                _state.value = ScanState.DeviceReady(device)
                prefetchPlans(device)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (gen != generation || currentTraceId != traceId) return
            val endedAt = clock()
            diagnostics.stageFinished(
                traceId = traceId,
                stage = TraceStage.DeviceResolve,
                outcome = StageOutcome.Failed,
                endedAt = endedAt,
                attempts = DEVICE_RESOLVE_ATTEMPTS,
                error = throwable.userMessage(),
                fields = mapOf("sourceKey" to key),
            )
            diagnostics.finishTrace(
                traceId = traceId,
                outcome = TraceOutcome.Failed,
                endedAt = endedAt,
                error = throwable.userMessage(),
            )
            diagnostics.event(
                DiagLevel.Warn,
                DiagFlow.Device,
                "resolve_fail",
                message = throwable.userMessage(),
                traceId = traceId,
                fields = mapOf("sourceKey" to key),
            )
            currentTraceId = null
            traceHandedOff = false
            _state.value = ScanState.Message(throwable.userMessage())
        }
    }

    private fun beginTrace(input: ScanInput): String {
        abandonCurrentTrace("新的扫码输入", evenIfHandedOff = true)
        val acceptedAt = clock()
        val gen = ++generation
        val traceId = "trace-$acceptedAt-$gen"
        currentTraceId = traceId
        traceHandedOff = false
        val startedAt = (acceptedAt - input.decodeMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        diagnostics.startTrace(
            WaterTrace(
                traceId = traceId,
                startedAt = startedAt,
                updatedAt = acceptedAt,
                source = input.source.label,
                decodeMs = input.decodeMs,
            ),
        )
        diagnostics.stageStart(
            traceId = traceId,
            stage = TraceStage.ScanDecode,
            startedAt = startedAt,
            attempts = input.attempts,
            fields = mapOf("source" to input.source.label),
        )
        diagnostics.stageFinished(
            traceId = traceId,
            stage = TraceStage.ScanDecode,
            outcome = StageOutcome.Success,
            endedAt = acceptedAt,
            attempts = input.attempts,
            fields = mapOf("source" to input.source.label),
        )
        diagnostics.stageStart(
            traceId = traceId,
            stage = TraceStage.DeviceResolve,
            startedAt = acceptedAt,
            attempts = DEVICE_RESOLVE_ATTEMPTS,
            fields = mapOf("sourceKey" to input.source.name),
        )
        return traceId
    }

    private fun abandonCurrentTrace(reason: String, evenIfHandedOff: Boolean = false) {
        val traceId = currentTraceId ?: return
        if (traceHandedOff && !evenIfHandedOff) return
        val endedAt = clock()
        diagnostics.stageFinished(
            traceId = traceId,
            stage = TraceStage.DeviceResolve,
            outcome = StageOutcome.Abandoned,
            endedAt = endedAt,
            attempts = DEVICE_RESOLVE_ATTEMPTS,
            error = reason,
        )
        diagnostics.finishTrace(
            traceId = traceId,
            outcome = TraceOutcome.Abandoned,
            endedAt = endedAt,
            error = reason,
        )
        diagnostics.event(
            DiagLevel.Debug,
            DiagFlow.Scan,
            "scan_abandoned",
            message = reason,
            traceId = traceId,
        )
        currentTraceId = null
        traceHandedOff = false
    }

    private fun prefetchPlans(device: ResolvedDevice) {
        scope.launch {
            deviceRepository.prefetchPlans(device)
        }
    }

    private companion object {
        const val DEVICE_RESOLVE_ATTEMPTS = 2
    }
}
