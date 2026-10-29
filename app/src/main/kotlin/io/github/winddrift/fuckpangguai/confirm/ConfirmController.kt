package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.device.ChannelSelector
import io.github.winddrift.fuckpangguai.device.DeviceDataSource
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.NoopDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
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
    val traceId: String = "",
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
    private val timingSink: UnlockTimingSink = NoopUnlockTimingSink,
    private val diagnostics: DiagSink = NoopDiagSink,
    private val clock: () -> Long = System::currentTimeMillis,
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
    private val bindMutex = Mutex()
    private var boundChannel: Int? = null
    private var boundByPrebind = false
    private var prebindAttempted = false
    private var prebindInFlightMethod: Int? = null
    private var prebindSkippedByClick = false
    private var acceleration = UnlockAcceleration.Conservative
    private var traceId = ""
    private var unlockSucceeded = false
    private var abandonPending = false
    private var abandoned = false

    fun start(device: ResolvedDevice) {
        val gen = ++generation
        currentDevice = device
        autoUnlockTriggered = false
        boundChannel = null
        boundByPrebind = false
        prebindAttempted = false
        prebindInFlightMethod = null
        prebindSkippedByClick = false
        acceleration = UnlockAcceleration.Conservative
        locationSnapshot = null
        locationPhase = LocationPhase.Fetching
        loadJob?.cancel()
        locationJob?.cancel()
        prepareTrace(device)

        loadJob = scope.launch {
            _state.value = ConfirmState.Loading
            try {
                val detail = device.detail
                if (detail.categoryCode != AppConfig.WATER_CATEGORY) {
                    blockConfirm("这不是饮水机")
                    return@launch
                }
                if (detail.isBluetooth == 1) {
                    blockConfirm("该设备支持 BLE 解锁；当前版本只做探测，暂不支持解锁。")
                    return@launch
                }
                detail.deviceErrorCode?.takeIf { it.isNotBlank() }?.let { code ->
                    blockConfirm(deviceErrorMessage(code, detail.deviceErrorMsg))
                    return@launch
                }

                if (!locationProvider.hasPermission()) {
                    diagnostics.event(
                        DiagLevel.Info,
                        DiagFlow.Confirm,
                        "location_permission_required",
                        traceId = traceId,
                        fields = mapOf("goodsId" to device.scan.id),
                    )
                    _state.value = ConfirmState.NeedsLocationPermission
                    return@launch
                }

                launchLocation(device, gen)

                val cached = deviceRepository.readCachedPlans(device)
                if (gen != generation) return@launch
                if (cached != null) {
                    if (!publishReady(device, cached.skus, cached.channels, gen, fromCache = true)) {
                        return@launch
                    }
                    launchCacheRefresh(device)
                    return@launch
                }

                val plans = deviceRepository.loadPlans(device)
                if (gen != generation) return@launch
                if (plans.skus.isEmpty()) {
                    blockConfirm("该设备没有可用套餐")
                    return@launch
                }
                if (plans.channels.isEmpty()) {
                    blockConfirm("该设备没有可用支付方式")
                    return@launch
                }
                publishReady(device, plans.skus, plans.channels, gen, fromCache = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                if (gen == generation) {
                    diagnostics.stageFinished(
                        traceId = traceId,
                        stage = TraceStage.ConfirmReady,
                        outcome = StageOutcome.Failed,
                        endedAt = clock(),
                        attempts = 0,
                        error = throwable.userMessage(),
                    )
                    diagnostics.finishTrace(
                        traceId = traceId,
                        outcome = TraceOutcome.Failed,
                        endedAt = clock(),
                        error = throwable.userMessage(),
                    )
                    _state.value = ConfirmState.Error(throwable.userMessage())
                }
            }
        }
    }

    private fun blockConfirm(message: String) {
        val endedAt = clock()
        diagnostics.stageFinished(
            traceId = traceId,
            stage = TraceStage.ConfirmReady,
            outcome = StageOutcome.Failed,
            endedAt = endedAt,
            attempts = 0,
            error = message,
        )
        diagnostics.finishTrace(
            traceId = traceId,
            outcome = TraceOutcome.Failed,
            endedAt = endedAt,
            error = message,
        )
        diagnostics.event(
            DiagLevel.Warn,
            DiagFlow.Confirm,
            "confirm_blocked",
            message = message,
            traceId = traceId,
        )
        _state.value = ConfirmState.Blocked(message)
    }

    private fun prepareTrace(device: ResolvedDevice) {
        val nextTraceId = device.traceId.ifBlank {
            traceId.ifBlank { "trace-${clock()}-confirm" }
        }
        if (traceId.isNotBlank() && traceId != nextTraceId && !unlockSucceeded) {
            finishAbandoned("切换设备")
        }
        traceId = nextTraceId
        unlockSucceeded = false
        abandonPending = false
        abandoned = false
        val startedAt = clock()
        diagnostics.startTrace(
            WaterTrace(
                traceId = traceId,
                startedAt = startedAt,
                updatedAt = startedAt,
                source = "确认页",
                deviceName = device.detail.name,
                goodsId = device.scan.id,
            ),
        )
        diagnostics.stageStart(
            traceId = traceId,
            stage = TraceStage.ConfirmReady,
            startedAt = startedAt,
            attempts = 0,
            fields = mapOf("goodsId" to device.scan.id),
        )
    }

    private fun finishAbandoned(reason: String) {
        if (traceId.isBlank() || unlockSucceeded) return
        val endedAt = clock()
        diagnostics.stageFinished(
            traceId = traceId,
            stage = TraceStage.ConfirmReady,
            outcome = StageOutcome.Abandoned,
            endedAt = endedAt,
            attempts = 0,
            error = reason,
        )
        diagnostics.finishTrace(
            traceId = traceId,
            outcome = TraceOutcome.Abandoned,
            endedAt = endedAt,
            error = reason,
        )
        abandoned = true
        diagnostics.event(
            DiagLevel.Debug,
            DiagFlow.Confirm,
            "confirm_abandoned",
            message = reason,
            traceId = traceId,
        )
    }

    fun abandon(reason: String) {
        if (traceId.isBlank() || unlockSucceeded) return
        if (_state.value is ConfirmState.Unlocking) {
            abandonPending = true
            diagnostics.event(
                DiagLevel.Debug,
                DiagFlow.Confirm,
                "abandon_pending",
                message = reason,
                traceId = traceId,
            )
            return
        }
        finishAbandoned(reason)
    }

    fun retryLocation() {
        val device = currentDevice ?: return
        when (val state = _state.value) {
            ConfirmState.NeedsLocationPermission -> {
                diagnostics.event(
                    DiagLevel.Debug,
                    DiagFlow.Confirm,
                    "location_permission_retry",
                    traceId = traceId,
                    fields = mapOf("granted" to locationProvider.hasPermission().toString()),
                )
                if (locationProvider.hasPermission()) start(device)
            }

            is ConfirmState.Ready -> {
                if (!locationProvider.hasPermission()) {
                    diagnostics.event(
                        DiagLevel.Warn,
                        DiagFlow.Confirm,
                        "location_permission_missing",
                        traceId = traceId,
                    )
                    _state.value = ConfirmState.NeedsLocationPermission
                    return
                }
                if (state.value.locationPhase == LocationPhase.Fetching) return

                diagnostics.event(
                    DiagLevel.Debug,
                    DiagFlow.Confirm,
                    "location_retry",
                    traceId = traceId,
                )
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
        if (abandoned) return
        val ready = (_state.value as? ConfirmState.Ready)?.value ?: return
        val payType = WaterDefaults.payTypeFor(ready.channel)
        if (ready.dryRun) {
            val at = clock()
            diagnostics.stageStart(
                traceId = traceId,
                stage = TraceStage.Unlock,
                startedAt = at,
                attempts = 0,
                fields = mapOf("dryRun" to "true"),
            )
            diagnostics.stageFinished(
                traceId = traceId,
                stage = TraceStage.Unlock,
                outcome = StageOutcome.Abandoned,
                endedAt = at,
                attempts = 0,
                error = "dry-run",
                fields = mapOf(
                    "dryRun" to "true",
                    "channel" to ready.channel.method.toString(),
                ),
            )
            diagnostics.finishTrace(traceId, TraceOutcome.Abandoned, at, "dry-run")
            diagnostics.event(
                DiagLevel.Info,
                DiagFlow.Confirm,
                "unlock_dry_run",
                traceId = traceId,
                fields = mapOf(
                    "goodsId" to ready.device.scan.id,
                    "channel" to ready.channel.methodName,
                ),
            )
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
        if (ready.locationPhase != LocationPhase.Ready || location == null) {
            diagnostics.event(
                DiagLevel.Debug,
                DiagFlow.Confirm,
                "unlock_blocked_no_location",
                traceId = traceId,
                fields = mapOf("phase" to ready.locationPhase.name),
            )
            return
        }

        val clickedAt = clock()
        diagnostics.stageStart(
            traceId = traceId,
            stage = TraceStage.Unlock,
            startedAt = clickedAt,
            attempts = 1,
            fields = mapOf(
                "channel" to ready.channel.methodName,
                "channelMethod" to ready.channel.method.toString(),
            ),
        )
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Confirm,
            "unlock_click",
            traceId = traceId,
            fields = mapOf(
                "goodsId" to ready.device.scan.id,
                "skuId" to ready.sku.skuId,
                "channel" to ready.channel.methodName,
                "oneTap" to ready.oneTap.toString(),
                "acceleration" to acceleration.name,
            ),
        )
        _state.value = ConfirmState.Unlocking
        scope.launch {
            var bindStats = BindStats()
            var unlockCallMs = 0L
            var sync = SyncOutcome(ready = false, polls = 0, durationMs = 0L)
            try {
                bindStats = ensureBound(ready.channel.method)

                val unlockStartedAt = clock()
                val unlock = waterRepository.unlock(
                    detail = ready.device.detail,
                    skuId = ready.sku.skuId,
                    lat = location.lat,
                    lng = location.lng,
                    payType = payType,
                )
                unlockCallMs = clock() - unlockStartedAt

                sync = waitForDevice(unlock)
                if (!sync.ready) {
                    throw ApiException(-1, "设备启动超时，请检查设备状态")
                }

                val totalMs = clock() - clickedAt
                val attempts = 1 + sync.polls + if (bindStats.skipped) 0 else 1
                val unlockFields = buildMap {
                    put("channel", ready.channel.methodName)
                    put("channelMethod", ready.channel.method.toString())
                    put("acceleration", acceleration.name)
                    put("bindSkipped", bindStats.skipped.toString())
                    put("prebound", bindStats.prebound.toString())
                    put("bindWaitMs", bindStats.waitMs.toString())
                    put("bindCallMs", bindStats.callMs.toString())
                    put("unlockCallMs", unlockCallMs.toString())
                    put("syncPolls", sync.polls.toString())
                    put("syncSource", sync.source)
                    put("deviceStartProbes", sync.deviceStartProbes.toString())
                    put("syncDurationMs", sync.durationMs.toString())
                    put("totalMs", totalMs.toString())
                }
                val abandoned = abandonPending
                val endedAt = clock()
                diagnostics.stageFinished(
                    traceId = traceId,
                    stage = TraceStage.Unlock,
                    outcome = if (abandoned) StageOutcome.Abandoned else StageOutcome.Success,
                    endedAt = endedAt,
                    attempts = attempts,
                    error = if (abandoned) "离开确认页" else "",
                    fields = unlockFields,
                )
                if (abandoned) {
                    diagnostics.finishTrace(
                        traceId = traceId,
                        outcome = TraceOutcome.Abandoned,
                        endedAt = endedAt,
                        error = "离开确认页",
                    )
                } else {
                    unlockSucceeded = true
                }
                _state.value = ConfirmState.Unlocked(
                    UnlockSession(
                        device = ready.device,
                        sku = ready.sku,
                        channel = ready.channel,
                        unlock = unlock,
                        location = location,
                        traceId = traceId,
                    ),
                )
                diagnostics.event(
                    if (abandoned) DiagLevel.Warn else DiagLevel.Info,
                    DiagFlow.Confirm,
                    if (abandoned) "unlock_ok_abandoned" else "unlock_ok",
                    traceId = traceId,
                    fields = unlockFields,
                )
                reportTiming(
                    buildTiming(
                        outcome = UnlockOutcome.Unlocked,
                        ready = ready,
                        bindStats = bindStats,
                        unlockCallMs = unlockCallMs,
                        sync = sync,
                        totalMs = totalMs,
                        errorKind = null,
                    ),
                )
                persistConfirmedAsync(ready)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                val endedAt = clock()
                diagnostics.stageFinished(
                    traceId = traceId,
                    stage = TraceStage.Unlock,
                    outcome = StageOutcome.Failed,
                    endedAt = endedAt,
                    attempts = 1 + sync.polls + if (bindStats.skipped) 0 else 1,
                    error = throwable.userMessage(),
                    fields = mapOf(
                        "channel" to ready.channel.methodName,
                        "acceleration" to acceleration.name,
                        "unlockCallMs" to unlockCallMs.toString(),
                        "syncPolls" to sync.polls.toString(),
                        "syncSource" to sync.source,
                        "errorKind" to errorKind(throwable),
                    ),
                )
                diagnostics.finishTrace(
                    traceId = traceId,
                    outcome = TraceOutcome.Failed,
                    endedAt = endedAt,
                    error = throwable.userMessage(),
                )
                diagnostics.event(
                    DiagLevel.Warn,
                    DiagFlow.Confirm,
                    "unlock_fail",
                    message = throwable.userMessage(),
                    traceId = traceId,
                    fields = mapOf(
                        "channel" to ready.channel.methodName,
                        "errorKind" to errorKind(throwable),
                    ),
                )
                reportTiming(
                    buildTiming(
                        outcome = UnlockOutcome.Failed,
                        ready = ready,
                        bindStats = bindStats,
                        unlockCallMs = unlockCallMs,
                        sync = sync,
                        totalMs = clock() - clickedAt,
                        errorKind = errorKind(throwable),
                    ),
                )
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

    private suspend fun ensureBound(method: Int): BindStats {
        val waitStartedAt = clock()
        if (acceleration != UnlockAcceleration.Disabled && boundChannel == method) {
            return BindStats(
                skipped = true,
                prebound = boundByPrebind,
                waitMs = (clock() - waitStartedAt).coerceAtLeast(0L),
            )
        }
        if (acceleration != UnlockAcceleration.Disabled &&
            acceleration != UnlockAcceleration.Conservative &&
            prebindInFlightMethod == method
        ) {
            prebindSkippedByClick = true
            return BindStats(
                skipped = true,
                prebound = true,
                waitMs = (clock() - waitStartedAt).coerceAtLeast(0L),
            )
        }
        return bindMutex.withLock {
            if (acceleration != UnlockAcceleration.Disabled && boundChannel == method) {
                return@withLock BindStats(
                    skipped = true,
                    prebound = boundByPrebind,
                    waitMs = (clock() - waitStartedAt).coerceAtLeast(0L),
                )
            }
            val callStartedAt = clock()
            try {
                deviceRepository.bindChannel(method)
            } catch (throwable: Throwable) {
                boundChannel = null
                boundByPrebind = false
                throw throwable
            }
            boundChannel = method
            boundByPrebind = false
            BindStats(
                skipped = false,
                prebound = false,
                waitMs = (callStartedAt - waitStartedAt).coerceAtLeast(0L),
                callMs = (clock() - callStartedAt).coerceAtLeast(0L),
            )
        }
    }

    private fun persistConfirmedAsync(ready: ConfirmReady) {
        scope.launch {
            try {
                configStore.save(
                    ConfirmedDevice(
                        goodsId = ready.device.scan.id,
                        skuId = ready.sku.skuId,
                        channelMethod = ready.channel.method,
                        deviceName = ready.device.detail.name,
                        updatedAt = clock(),
                    ),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
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
            diagnostics.event(
                if (point == null) DiagLevel.Warn else DiagLevel.Debug,
                DiagFlow.Confirm,
                if (point == null) "location_fail" else "location_ok",
                traceId = traceId,
            )

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
        fromCache: Boolean,
    ): Boolean {
        if (gen != generation) return false

        val oneTap = settingsStore.isOneTapEnabled()
        val dryRun = settingsStore.isDryRun()
        val accelerationTier = settingsStore.getUnlockAcceleration()
        val confirmed = configStore.find(device.scan.id)
        if (gen != generation) return false

        acceleration = accelerationTier

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
        if (abandoned) return true
        val readyAt = clock()
        diagnostics.stageFinished(
            traceId = traceId,
            stage = TraceStage.ConfirmReady,
            outcome = StageOutcome.Success,
            endedAt = readyAt,
            attempts = if (fromCache) 0 else PLAN_REQUEST_ATTEMPTS,
            fields = mapOf(
                "fromCache" to fromCache.toString(),
                "skus" to skus.size.toString(),
                "channels" to channels.size.toString(),
                "locationPhase" to locationPhase.name,
                "oneTap" to oneTap.toString(),
                "dryRun" to dryRun.toString(),
                "acceleration" to accelerationTier.name,
            ),
        )
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Confirm,
            "confirm_ready",
            traceId = traceId,
            fields = mapOf(
                "goodsId" to device.scan.id,
                "fromCache" to fromCache.toString(),
                "skuId" to ready.sku.skuId,
                "channel" to ready.channel.methodName,
                "locationPhase" to locationPhase.name,
                "acceleration" to accelerationTier.name,
            ),
        )
        launchPrebind(ready, gen)
        maybeAutoUnlock(device, gen)
        return true
    }

    private fun launchPrebind(ready: ConfirmReady, gen: Int) {
        if (acceleration == UnlockAcceleration.Disabled || ready.dryRun || prebindAttempted) return
        prebindAttempted = true
        val method = ready.channel.method
        scope.launch {
            var failed = false
            try {
                prebindInFlightMethod = method
                bindMutex.withLock {
                    if (gen != generation || boundChannel == method) return@withLock
                    deviceRepository.bindChannel(method)
                    if (gen == generation) {
                        boundChannel = method
                        boundByPrebind = true
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                failed = true
                // 预绑定失败静默，点击解锁时会重试；点击时若已跳过等待，这里补一次。
            } finally {
                if (prebindInFlightMethod == method) {
                    prebindInFlightMethod = null
                }
            }
            if (failed && prebindSkippedByClick && gen == generation) {
                retryPrebind(method, gen)
            }
        }
    }

    private fun retryPrebind(method: Int, gen: Int) {
        scope.launch {
            try {
                bindMutex.withLock {
                    if (gen != generation || boundChannel == method) return@withLock
                    deviceRepository.bindChannel(method)
                    boundChannel = method
                    boundByPrebind = true
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
            }
        }
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
        if (abandoned) return
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

    private suspend fun waitForDevice(unlock: Unlock): SyncOutcome {
        if (unlock.msgId.isBlank()) {
            throw ApiException(-1, "解锁成功但未返回 msgId")
        }
        val startedAt = clock()
        var polls = 0
        var deviceStartProbes = 0
        val signal = withTimeoutOrNull(POLL_TIMEOUT_MS) {
            while (true) {
                polls += 1
                val bean = waterRepository.msgSync(unlock.msgId)
                if (waterRepository.isDeviceReady(bean)) {
                    return@withTimeoutOrNull SyncSignal.MsgSync
                }
                if (acceleration == UnlockAcceleration.Insane && unlock.orderNo.isNotBlank()) {
                    deviceStartProbes += 1
                    val started = try {
                        waterRepository.queryDeviceStarted(unlock.orderNo)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Throwable) {
                        null
                    }
                    if (started == true) {
                        return@withTimeoutOrNull SyncSignal.DeviceStart
                    }
                }
                delay(pollIntervalMs(polls))
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        return SyncOutcome(
            ready = signal != null,
            polls = polls,
            durationMs = (clock() - startedAt).coerceAtLeast(0L),
            source = signal?.label.orEmpty(),
            deviceStartProbes = deviceStartProbes,
        )
    }

    private fun pollIntervalMs(polls: Int): Long = when (acceleration) {
        UnlockAcceleration.Disabled -> SLOW_POLL_INTERVAL_MS
        UnlockAcceleration.Conservative ->
            if (polls < CONSERVATIVE_FAST_POLL_COUNT) {
                CONSERVATIVE_FAST_POLL_INTERVAL_MS
            } else {
                SLOW_POLL_INTERVAL_MS
            }

        UnlockAcceleration.Aggressive ->
            if (polls < AGGRESSIVE_FAST_POLL_COUNT) {
                AGGRESSIVE_FAST_POLL_INTERVAL_MS
            } else {
                AGGRESSIVE_SLOW_POLL_INTERVAL_MS
            }

        UnlockAcceleration.Insane ->
            if (polls < INSANE_FAST_POLL_COUNT) {
                INSANE_FAST_POLL_INTERVAL_MS
            } else {
                INSANE_SLOW_POLL_INTERVAL_MS
            }
    }

    private fun buildTiming(
        outcome: UnlockOutcome,
        ready: ConfirmReady,
        bindStats: BindStats,
        unlockCallMs: Long,
        sync: SyncOutcome,
        totalMs: Long,
        errorKind: String?,
    ): UnlockTiming = UnlockTiming(
        atMillis = clock(),
        outcome = outcome,
        acceleration = acceleration,
        channelMethod = ready.channel.method,
        channelName = ready.channel.methodName,
        prebindAttempted = prebindAttempted,
        prebound = bindStats.prebound,
        bindSkipped = bindStats.skipped,
        bindWaitMs = bindStats.waitMs,
        bindCallMs = bindStats.callMs,
        unlockCallMs = unlockCallMs.coerceAtLeast(0L),
        syncPollCount = sync.polls,
        syncDurationMs = sync.durationMs,
        syncSource = sync.source,
        deviceStartProbes = sync.deviceStartProbes,
        totalMs = totalMs.coerceAtLeast(0L),
        errorKind = errorKind,
    )

    private fun reportTiming(entry: UnlockTiming) {
        try {
            timingSink.record(entry)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
        }
    }

    private fun errorKind(throwable: Throwable): String = when (throwable) {
        is ApiException -> "ApiException-${throwable.code}"
        is IOException -> "IOException"
        else -> throwable.javaClass.simpleName
    }

    private data class BindStats(
        val skipped: Boolean = false,
        val prebound: Boolean = false,
        val waitMs: Long = 0L,
        val callMs: Long = 0L,
    )

    private data class SyncOutcome(
        val ready: Boolean,
        val polls: Int,
        val durationMs: Long,
        val source: String = "",
        val deviceStartProbes: Int = 0,
    )

    private enum class SyncSignal(val label: String) {
        MsgSync("msgSync"),
        DeviceStart("deviceStart"),
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

    private companion object {
        const val PLAN_REQUEST_ATTEMPTS = 2
        const val POLL_TIMEOUT_MS = 15_000L

        /** 保守：前 9 次请求覆盖约 2 秒（首轮立即，随后 8 个 250ms 间隔）。 */
        const val CONSERVATIVE_FAST_POLL_COUNT = 9
        const val CONSERVATIVE_FAST_POLL_INTERVAL_MS = 250L

        /** 激进：前 31 次请求覆盖约 3 秒，之后回落到 250ms。 */
        const val AGGRESSIVE_FAST_POLL_COUNT = 31
        const val AGGRESSIVE_FAST_POLL_INTERVAL_MS = 100L
        const val AGGRESSIVE_SLOW_POLL_INTERVAL_MS = 250L

        /** 丧心病狂：前 61 次请求覆盖约 3 秒，之后回落到 100ms。 */
        const val INSANE_FAST_POLL_COUNT = 61
        const val INSANE_FAST_POLL_INTERVAL_MS = 50L
        const val INSANE_SLOW_POLL_INTERVAL_MS = 100L

        /** 官方 1.143.1 的固定重试间隔；不启用档位恢复该行为。 */
        const val SLOW_POLL_INTERVAL_MS = 1_000L
    }
}
