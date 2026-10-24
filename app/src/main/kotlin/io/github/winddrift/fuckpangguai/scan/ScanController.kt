package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.device.DeviceRepository
import io.github.winddrift.fuckpangguai.device.QrParser
import io.github.winddrift.fuckpangguai.device.QrTarget
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.nfc.NfcPayload
import io.github.winddrift.fuckpangguai.nfc.NfcTagBinding
import io.github.winddrift.fuckpangguai.nfc.NfcTagStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState
    data object Loading : ScanState
    data class DeviceReady(val device: ResolvedDevice) : ScanState
    data class NeedsBind(
        val tagId: String,
        val payload: NfcPayload,
    ) : ScanState

    data class ConfirmBind(
        val tagId: String,
        val device: ResolvedDevice,
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

class ScanController(
    private val scope: CoroutineScope,
    private val deviceRepository: DeviceRepository,
    private val nfcTagStore: NfcTagStore,
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state = _state.asStateFlow()

    private var pendingTagId: String? = null

    fun onQr(raw: String) {
        when (val target = QrParser.parse(raw)) {
            is QrTarget.Device -> resolve(target.key, target.value, pendingTagId)
            is QrTarget.Unsupported -> {
                _state.value = ScanState.Message("无法识别该二维码，请对准设备上的二维码")
            }
        }
    }

    fun onNfc(payload: NfcPayload) {
        val sn = payload.sn
        if (!sn.isNullOrBlank()) {
            resolve("SN", sn, null)
            return
        }
        val nqt = payload.nqt
        if (!nqt.isNullOrBlank()) {
            resolve("NQT", nqt, null)
            return
        }
        val imei = payload.imei
        if (!imei.isNullOrBlank()) {
            resolve("IMEI", imei, null)
            return
        }
        val tagId = payload.tagId
        if (!tagId.isNullOrBlank()) {
            scope.launch {
                _state.value = ScanState.Loading
                try {
                    val binding = nfcTagStore.find(tagId)
                    if (binding == null) {
                        pendingTagId = tagId
                        _state.value = ScanState.NeedsBind(tagId, payload)
                    } else {
                        resolveNow("SN", binding.sn, null)
                    }
                } catch (throwable: Throwable) {
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
        if (current !is ScanState.ConfirmBind) return
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
                pendingTagId = null
                _state.value = ScanState.DeviceReady(current.device)
            } catch (throwable: Throwable) {
                _state.value = ScanState.Message(throwable.userMessage())
            }
        }
    }

    fun skipBind() {
        val current = _state.value
        if (current !is ScanState.ConfirmBind) return
        pendingTagId = null
        _state.value = ScanState.DeviceReady(current.device)
    }

    fun reset() {
        pendingTagId = null
        _state.value = ScanState.Idle
    }

    fun showMessage(text: String, rawUrl: String? = null) {
        _state.value = ScanState.Message(text, rawUrl)
    }

    private fun resolve(key: String, value: String, bindTagId: String?) {
        scope.launch {
            _state.value = ScanState.Loading
            resolveNow(key, value, bindTagId)
        }
    }

    private suspend fun resolveNow(key: String, value: String, bindTagId: String?) {
        try {
            val resolved = deviceRepository.resolve(key, value)
            _state.value = if (bindTagId != null) {
                ScanState.ConfirmBind(bindTagId, resolved)
            } else {
                ScanState.DeviceReady(resolved)
            }
        } catch (throwable: Throwable) {
            _state.value = ScanState.Message(throwable.userMessage())
        }
    }
}
