package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.device.ChannelSelector
import io.github.winddrift.fuckpangguai.device.DeviceRepository
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.location.LocationProvider
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.storage.ConfirmedDevice
import io.github.winddrift.fuckpangguai.storage.DeviceConfigStore
import io.github.winddrift.fuckpangguai.water.WaterDefaults
import io.github.winddrift.fuckpangguai.water.WaterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class LocationSnapshot(
    val lat: String,
    val lng: String,
)

data class ConfirmReady(
    val device: ResolvedDevice,
    val skus: List<Sku>,
    val channels: List<PayChannelRoute>,
    val selectedSkuIndex: Int,
    val selectedChannelIndex: Int,
    val location: LocationSnapshot,
    val oneTap: Boolean,
    val dryRun: Boolean,
    val confirmed: Boolean,
) {
    val sku: Sku get() = skus[selectedSkuIndex]
    val channel: PayChannelRoute get() = channels[selectedChannelIndex]
}

data class DryRunInfo(
    val deviceName: String,
    val goodsId: String,
    val skuId: String,
    val skuName: String,
    val channelName: String,
    val lat: String,
    val lng: String,
    val payType: String,
)

data class UnlockSession(
    val device: ResolvedDevice,
    val sku: Sku,
    val channel: PayChannelRoute,
    val unlock: Unlock,
    val location: LocationSnapshot,
)

sealed interface ConfirmState {
    data object Loading : ConfirmState
    data object NeedsLocationPermission : ConfirmState
    data class Ready(val value: ConfirmReady) : ConfirmState
    data class Blocked(val message: String) : ConfirmState
    data object Unlocking : ConfirmState
    data class DryRun(val info: DryRunInfo) : ConfirmState
    data class Unlocked(val session: UnlockSession) : ConfirmState
    data class Error(val message: String) : ConfirmState
}

class ConfirmController(
    private val scope: CoroutineScope,
    private val deviceRepository: DeviceRepository,
    private val waterRepository: WaterRepository,
    private val locationProvider: LocationProvider,
    private val settingsStore: AppSettingsStore,
    private val configStore: DeviceConfigStore,
) {
    private val _state = MutableStateFlow<ConfirmState>(ConfirmState.Loading)
    val state = _state.asStateFlow()

    private var currentDevice: ResolvedDevice? = null

    fun start(device: ResolvedDevice) {
        currentDevice = device
        scope.launch {
            _state.value = ConfirmState.Loading
            try {
                val detail = device.detail
                if (detail.categoryCode != AppConfig.WATER_CATEGORY) {
                    _state.value = ConfirmState.Blocked("这不是饮水机")
                    return@launch
                }
                if (detail.isBluetooth == 1) {
                    _state.value = ConfirmState.Blocked(
                        "该设备支持 BLE 解锁；当前版本只做探测，暂不支持解锁。",
                    )
                    return@launch
                }
                detail.deviceErrorCode?.takeIf { it.isNotBlank() }?.let { code ->
                    _state.value = ConfirmState.Blocked(deviceErrorMessage(code, detail.deviceErrorMsg))
                    return@launch
                }

                val skus = deviceRepository.loadSkus(device.scan.id)
                if (skus.isEmpty()) {
                    _state.value = ConfirmState.Blocked("该设备没有可用套餐")
                    return@launch
                }
                val channels = deviceRepository.loadChannels(detail)
                if (channels.isEmpty()) {
                    _state.value = ConfirmState.Blocked("该设备没有可用支付方式")
                    return@launch
                }
                if (!locationProvider.hasPermission()) {
                    _state.value = ConfirmState.NeedsLocationPermission
                    return@launch
                }
                val location = locationProvider.getCurrentLocation()
                if (location == null) {
                    _state.value = ConfirmState.Blocked("无法获取当前位置，请开启定位后重试。")
                    return@launch
                }

                val snapshot = LocationSnapshot(
                    lat = String.format(Locale.US, "%.6f", location.latitude),
                    lng = String.format(Locale.US, "%.6f", location.longitude),
                )
                val oneTap = settingsStore.isOneTapEnabled()
                val dryRun = settingsStore.isDryRun()
                val confirmed = configStore.find(device.scan.id)
                val skuIndex = confirmed?.skuId
                    ?.let { id -> skus.indexOfFirst { it.skuId == id } }
                    ?.takeIf { it >= 0 }
                    ?: 0
                val channelIndex = ChannelSelector.selectIndex(channels, confirmed?.channelMethod).coerceAtLeast(0)
                val ready = ConfirmReady(
                    device = device,
                    skus = skus,
                    channels = channels,
                    selectedSkuIndex = skuIndex,
                    selectedChannelIndex = channelIndex,
                    location = snapshot,
                    oneTap = oneTap,
                    dryRun = dryRun,
                    confirmed = confirmed != null,
                )
                _state.value = ConfirmState.Ready(ready)
                if (oneTap && confirmed != null && !dryRun) {
                    unlock()
                }
            } catch (throwable: Throwable) {
                _state.value = ConfirmState.Error(throwable.userMessage())
            }
        }
    }

    fun retryLocation() {
        currentDevice?.let(::start)
    }

    fun selectSku(index: Int) {
        val ready = (_state.value as? ConfirmState.Ready)?.value ?: return
        if (index !in ready.skus.indices) return
        _state.value = ConfirmState.Ready(ready.copy(selectedSkuIndex = index))
    }

    fun selectChannel(index: Int) {
        val ready = (_state.value as? ConfirmState.Ready)?.value ?: return
        if (index !in ready.channels.indices) return
        _state.value = ConfirmState.Ready(ready.copy(selectedChannelIndex = index))
    }

    fun unlock() {
        val ready = (_state.value as? ConfirmState.Ready)?.value ?: return
        val payType = WaterDefaults.payTypeFor(ready.channel)
        if (ready.dryRun) {
            _state.value = ConfirmState.DryRun(
                DryRunInfo(
                    deviceName = ready.device.detail.name,
                    goodsId = ready.device.scan.id,
                    skuId = ready.sku.skuId,
                    skuName = ready.sku.name,
                    channelName = ready.channel.methodName,
                    lat = ready.location.lat,
                    lng = ready.location.lng,
                    payType = payType,
                ),
            )
            return
        }

        _state.value = ConfirmState.Unlocking
        scope.launch {
            try {
                deviceRepository.bindChannel(ready.channel.method)
                val unlock = waterRepository.unlock(
                    detail = ready.device.detail,
                    skuId = ready.sku.skuId,
                    lat = ready.location.lat,
                    lng = ready.location.lng,
                    payType = payType,
                )
                waitForDevice(unlock)
                configStore.save(
                    ConfirmedDevice(
                        goodsId = ready.device.scan.id,
                        skuId = ready.sku.skuId,
                        channelMethod = ready.channel.method,
                        deviceName = ready.device.detail.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _state.value = ConfirmState.Unlocked(
                    UnlockSession(
                        device = ready.device,
                        sku = ready.sku,
                        channel = ready.channel,
                        unlock = unlock,
                        location = ready.location,
                    ),
                )
            } catch (throwable: Throwable) {
                _state.value = ConfirmState.Error(throwable.userMessage())
            }
        }
    }

    private suspend fun waitForDevice(unlock: Unlock) {
        if (unlock.msgId.isBlank()) {
            throw ApiException(-1, "解锁成功但未返回 msgId")
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val bean = waterRepository.msgSync(unlock.msgId)
            if (waterRepository.isDeviceReady(bean)) return
            delay(1_000)
        }
        throw ApiException(-1, "设备启动超时，请检查设备状态")
    }

    private fun deviceErrorMessage(code: String, serverMessage: String?): String = when (code) {
        "2" -> "设备工作中，请更换设备使用"
        "7" -> "设备被预约，请更换设备使用"
        "-100" -> "连接设备超时"
        "4000" -> "设备未绑定，请联系商家"
        "4107" -> "设备故障，请联系商家"
        "4111" -> "设备已停用，请联系商家"
        "4204" -> "设备离线，请检查设备是否通电"
        "5023" -> serverMessage ?: "设备当前不可用"
        else -> serverMessage?.takeIf { it.isNotBlank() } ?: "设备故障（$code）"
    }
}
