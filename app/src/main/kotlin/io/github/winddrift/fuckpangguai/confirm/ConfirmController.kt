package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.device.ChannelSelector
import io.github.winddrift.fuckpangguai.device.DeviceDataSource
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.location.GeoPoint
import io.github.winddrift.fuckpangguai.location.LocationSource
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.storage.ConfirmedDevice
import io.github.winddrift.fuckpangguai.storage.DeviceConfigSource
import io.github.winddrift.fuckpangguai.storage.SettingsSource
import io.github.winddrift.fuckpangguai.water.WaterDefaults
import io.github.winddrift.fuckpangguai.water.WaterDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class LocationSnapshot(
    val lat: String,
    val lng: String,
)

enum class LocationPhase {
    Fetching,
    Ready,
    Failed,
}

data class ConfirmReady(
    val device: ResolvedDevice,
    val skus: List<Sku>,
    val channels: List<PayChannelRoute>,
    val selectedSkuIndex: Int,
    val selectedChannelIndex: Int,
    val location: LocationSnapshot?,
    val locationPhase: LocationPhase,
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
    private val deviceRepository: DeviceDataSource,
    private val waterRepository: WaterDataSource,
    private val locationProvider: LocationSource,
    private val settingsStore: SettingsSource,
    private val configStore: DeviceConfigSource,
) {
    private val _state = MutableStateFlow<ConfirmState>(ConfirmState.Loading)
    val state = _state.asStateFlow()

    private var currentDevice: ResolvedDevice? = null
    private var loadJob: Job? = null
    private var locationJob: Job? = null
    private var generation = 0
    private var autoUnlockTriggered = false
    private var locationSnapshot: LocationSnapshot? = null
    private var locationPhase = LocationPhase.Fetching

    fun start(device: ResolvedDevice) {
        val gen = ++generation
        currentDevice = device
        autoUnlockTriggered = false
        locationSnapshot = null
        locationPhase = LocationPhase.Fetching
        loadJob?.cancel()
        locationJob?.cancel()

        loadJob = scope.launch {
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

                if (!locationProvider.hasPermission()) {
                    _state.value = ConfirmState.NeedsLocationPermission
                    return@launch
                }

                launchLocation(device, gen)

                val cached = deviceRepository.readCachedPlans(device)
                if (gen != generation) return@launch
                if (cached != null) {
                    if (!publishReady(device, cached.skus, cached.channels, gen)) return@launch
                    launchCacheRefresh(device)
                    return@launch
                }

                val plans = deviceRepository.loadPlans(device)
                if (gen != generation) return@launch
                if (plans.skus.isEmpty()) {
                    _state.value = ConfirmState.Blocked("该设备没有可用套餐")
                    return@launch
                }
                if (plans.channels.isEmpty()) {
                    _state.value = ConfirmState.Blocked("该设备没有可用支付方式")
                    return@launch
                }
                publishReady(device, plans.skus, plans.channels, gen)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                if (gen == generation) {
                    _state.value = ConfirmState.Error(throwable.userMessage())
                }
            }
        }
    }

    fun retryLocation() {
        val device = currentDevice ?: return
        when (val state = _state.value) {
            ConfirmState.NeedsLocationPermission -> {
                if (locationProvider.hasPermission()) start(device)
            }

            is ConfirmState.Ready -> {
                if (!locationProvider.hasPermission()) {
                    _state.value = ConfirmState.NeedsLocationPermission
                    return
                }
                if (state.value.locationPhase == LocationPhase.Fetching) return

                locationSnapshot = null
                locationPhase = LocationPhase.Fetching
                _state.value = ConfirmState.Ready(
                    state.value.copy(location = null, locationPhase = LocationPhase.Fetching),
                )
                launchLocation(device, generation)
            }

            else -> Unit
        }
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
                    lat = ready.location?.lat.orEmpty(),
                    lng = ready.location?.lng.orEmpty(),
                    payType = payType,
                ),
            )
            return
        }

        val location = ready.location
        if (ready.locationPhase != LocationPhase.Ready || location == null) return

        _state.value = ConfirmState.Unlocking
        scope.launch {
            try {
                deviceRepository.bindChannel(ready.channel.method)
                val unlock = waterRepository.unlock(
                    detail = ready.device.detail,
                    skuId = ready.sku.skuId,
                    lat = location.lat,
                    lng = location.lng,
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
                        location = location,
                    ),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                val message = throwable.userMessage()
                try {
                    deviceRepository.clearCachedPlans(ready.device)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Throwable) {
                }
                _state.value = ConfirmState.Error(message)
            }
        }
    }

    private fun launchLocation(device: ResolvedDevice, gen: Int) {
        locationJob?.cancel()
        locationJob = scope.launch {
            val point = try {
                locationProvider.getCurrentLocation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                null
            }
            if (gen != generation) return@launch

            locationSnapshot = point?.let(::toSnapshot)
            locationPhase = if (point == null) LocationPhase.Failed else LocationPhase.Ready

            val ready = (_state.value as? ConfirmState.Ready)?.value ?: return@launch
            if (ready.device != device) return@launch
            _state.value = ConfirmState.Ready(
                ready.copy(
                    location = locationSnapshot,
                    locationPhase = locationPhase,
                ),
            )
            maybeAutoUnlock(device, gen)
        }
    }

    private suspend fun publishReady(
        device: ResolvedDevice,
        skus: List<Sku>,
        channels: List<PayChannelRoute>,
        gen: Int,
    ): Boolean {
        if (gen != generation) return false

        val oneTap = settingsStore.isOneTapEnabled()
        val dryRun = settingsStore.isDryRun()
        val confirmed = configStore.find(device.scan.id)
        if (gen != generation) return false

        val skuIndex = confirmed?.skuId
            ?.let { id -> skus.indexOfFirst { it.skuId == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val channelIndex = ChannelSelector.selectIndex(channels, confirmed?.channelMethod)
            .coerceAtLeast(0)
        val ready = ConfirmReady(
            device = device,
            skus = skus,
            channels = channels,
            selectedSkuIndex = skuIndex,
            selectedChannelIndex = channelIndex,
            location = locationSnapshot,
            locationPhase = locationPhase,
            oneTap = oneTap,
            dryRun = dryRun,
            confirmed = confirmed != null,
        )
        _state.value = ConfirmState.Ready(ready)
        maybeAutoUnlock(device, gen)
        return true
    }

    private fun launchCacheRefresh(device: ResolvedDevice) {
        scope.launch {
            try {
                deviceRepository.refreshPlans(device)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
            }
        }
    }

    private fun maybeAutoUnlock(device: ResolvedDevice, gen: Int) {
        if (gen != generation || autoUnlockTriggered) return
        val ready = (_state.value as? ConfirmState.Ready)?.value ?: return
        if (ready.device != device) return
        if (!ready.oneTap || !ready.confirmed || ready.dryRun) return
        if (ready.locationPhase != LocationPhase.Ready) return

        autoUnlockTriggered = true
        unlock()
    }

    private fun toSnapshot(point: GeoPoint): LocationSnapshot = LocationSnapshot(
        lat = String.format(Locale.US, "%.6f", point.latitude),
        lng = String.format(Locale.US, "%.6f", point.longitude),
    )

    private suspend fun waitForDevice(unlock: Unlock) {
        if (unlock.msgId.isBlank()) {
            throw ApiException(-1, "解锁成功但未返回 msgId")
        }
        val ready = withTimeoutOrNull(15_000) {
            while (true) {
                val bean = waterRepository.msgSync(unlock.msgId)
                if (waterRepository.isDeviceReady(bean)) return@withTimeoutOrNull true
                delay(1_000)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        if (!ready) {
            throw ApiException(-1, "设备启动超时，请检查设备状态")
        }
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
