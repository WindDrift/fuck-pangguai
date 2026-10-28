package io.github.winddrift.fuckpangguai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.winddrift.fuckpangguai.PangguaiApp
import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.WaterTraceTracker
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.SettlementDecider
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.WaterRepository
import io.github.winddrift.fuckpangguai.water.WaterSession
import io.github.winddrift.fuckpangguai.water.WaterSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WaterSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val tracker = WaterTraceTracker()
    private lateinit var store: WaterSessionStore
    private lateinit var repository: WaterRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var diagnostics: DiagSink
    private var job: Job? = null

    @Volatile
    private var current: WaterSession? = null

    @Volatile
    private var trace: WaterTrace? = null

    @Volatile
    private var waterConversion: WaterConversion? = null

    @Volatile
    private var endRequested = false

    private var endSent = false

    override fun onCreate() {
        super.onCreate()
        val graph = (application as PangguaiApp).graph
        store = graph.waterSessionStore
        repository = graph.waterRepository
        historyRepository = graph.historyRepository
        diagnostics = graph.diagnostics
        NotificationChannels.ensure(this)
        scope.launch {
            graph.settingsStore.waterConversion.collect { waterConversion = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }

        val initial = intent?.getStringExtra(EXTRA_SESSION)
            ?.let { raw -> runCatching { json.decodeFromString<WaterSession>(raw) }.getOrNull() }
            ?: current
            ?: store.session.value

        startForegroundCompat(initial)

        if (action == ACTION_END) {
            scope.launch {
                val session = initial ?: store.load()
                if (session == null) {
                    stopSession()
                } else {
                    endRequested = true
                    startPolling(session)
                }
            }
            return START_STICKY
        }

        if (action == ACTION_START && initial != null) {
            startPolling(initial)
            return START_STICKY
        }

        scope.launch {
            val persisted = store.load()
            if (persisted == null) {
                stopSession()
            } else {
                startPolling(persisted)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        val session = current ?: store.session.value
        if (session != null) {
            val now = System.currentTimeMillis()
            val updated = session.copy(
                phase = SessionPhase.PAY_UNKNOWN,
                error = "前台服务超时，请回到 App 或原 App 核对",
                endedAt = session.endedAt.takeIf { it > 0L } ?: now,
                updatedAt = now,
            )
            current = updated
            diagnostics.event(
                DiagLevel.Error,
                DiagFlow.Water,
                "service_timeout",
                message = updated.error,
                traceId = updated.traceId,
                fields = mapOf("phase" to updated.phase),
            )
            scope.launch {
                store.save(updated)
                persistTrace(updated)
                historyRepository.save(updated)
                NotificationChannels.notify(
                    this@WaterSyncService,
                    updated,
                    terminal = true,
                    conversion = waterConversion,
                )
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf(startId)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPolling(session: WaterSession) {
        job?.cancel()
        job = scope.launch { runSession(session) }
    }

    private suspend fun runSession(initial: WaterSession) {
        var session = prepareTrace(initial)
        current = session

        if (session.phase == SessionPhase.SETTLING) {
            resumeSettlement(session)
            return
        }
        if (session.phase in SessionPhase.TERMINAL) {
            finish(session)
            return
        }

        session = session.copy(phase = SessionPhase.USING, updatedAt = System.currentTimeMillis())
        save(session)
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Water,
            "session_start",
            traceId = session.traceId,
            fields = mapOf(
                "goodsId" to session.goodsId,
                "phase" to session.phase,
            ),
        )

        while (currentCoroutineContext().isActive) {
            if (endRequested && !endSent) {
                endSent = true
                val now = System.currentTimeMillis()
                session = session.copy(
                    endRequestedAt = session.endRequestedAt.takeIf { it > 0L } ?: now,
                    updatedAt = now,
                )
                save(session)
                val result = runCatching { repository.endWater(session.goodsId) }
                val error = result.exceptionOrNull()
                session = session.copy(
                    phase = SessionPhase.ENDING,
                    error = error?.userMessage() ?: session.error,
                    updatedAt = System.currentTimeMillis(),
                )
                save(session)
                diagnostics.event(
                    if (error == null) DiagLevel.Info else DiagLevel.Warn,
                    DiagFlow.Water,
                    "end_water",
                    message = error?.userMessage().orEmpty(),
                    traceId = session.traceId,
                    fields = mapOf("goodsId" to session.goodsId),
                )
            }

            try {
                val sync = repository.waterSync(session.skuId)
                val now = System.currentTimeMillis()
                val amount = sync.amount.ifBlank { session.amount }
                val confirmed = session.waterConfirmedAt.takeIf { it > 0L } ?: run {
                    val positiveAmount = (amount.toDoubleOrNull() ?: 0.0) > 0.0
                    if (sync.workStatus == 2 || positiveAmount) now else 0L
                }
                session = session.copy(
                    amount = amount,
                    identify = sync.identify.ifBlank { session.identify },
                    syncCount = session.syncCount + 1,
                    waterConfirmedAt = confirmed,
                    updatedAt = now,
                )
                save(session)
                diagnostics.event(
                    DiagLevel.Debug,
                    DiagFlow.Water,
                    "water_sync",
                    traceId = session.traceId,
                    fields = mapOf(
                        "status" to sync.status.toString(),
                        "workStatus" to sync.workStatus.toString(),
                        "attempt" to session.syncCount.toString(),
                        "confirmed" to (confirmed > 0L).toString(),
                    ),
                )
                if (confirmed == now && confirmed > 0L) {
                    diagnostics.event(
                        DiagLevel.Info,
                        DiagFlow.Water,
                        "water_confirmed",
                        traceId = session.traceId,
                        fields = mapOf(
                            "attempt" to session.syncCount.toString(),
                            "workStatus" to sync.workStatus.toString(),
                            "amount" to amount,
                        ),
                    )
                }

                when (sync.status) {
                    3, 5 -> {
                        session = session.copy(
                            endedAt = session.endedAt.takeIf { it > 0L } ?: now,
                            updatedAt = now,
                        )
                        diagnoseDeviceEnd(session, sync.status)
                        settle(session)
                        return
                    }
                    6 -> {
                        session = session.copy(
                            phase = SessionPhase.FAULT,
                            error = "设备异常",
                            endedAt = session.endedAt.takeIf { it > 0L } ?: now,
                            updatedAt = now,
                        )
                        diagnostics.event(
                            DiagLevel.Error,
                            DiagFlow.Water,
                            "device_fault",
                            message = "设备异常",
                            traceId = session.traceId,
                            fields = mapOf("status" to sync.status.toString()),
                        )
                        finish(session)
                        return
                    }
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                session = session.copy(
                    error = throwable.userMessage(),
                    updatedAt = System.currentTimeMillis(),
                )
                save(session)
                diagnostics.event(
                    DiagLevel.Warn,
                    DiagFlow.Water,
                    "water_sync_fail",
                    message = throwable.userMessage(),
                    traceId = session.traceId,
                )
                delay(3_000)
            }

            delay(1_500)
        }
    }

    private fun diagnoseDeviceEnd(session: WaterSession, status: Int) {
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Water,
            "device_end",
            traceId = session.traceId,
            fields = mapOf(
                "status" to status.toString(),
                "waterConfirmed" to (session.waterConfirmedAt > 0L).toString(),
            ),
        )
    }

    private suspend fun prepareTrace(initial: WaterSession): WaterSession {
        var session = initial
        if (session.traceId.isBlank()) {
            session = session.copy(traceId = "water-${session.startedAt}")
            store.save(session)
        }
        val existing = diagnostics.findTrace(session.traceId)
        if (existing != null && existing.outcome == TraceOutcome.Running) {
            trace = existing
            diagnostics.event(
                DiagLevel.Debug,
                DiagFlow.Water,
                "trace_resume",
                traceId = session.traceId,
                fields = mapOf("stages" to existing.stages.size.toString()),
            )
            return session
        }
        val traceId = if (existing != null) {
            "${session.traceId}-${System.currentTimeMillis()}"
        } else {
            session.traceId
        }
        if (traceId != session.traceId) {
            session = session.copy(traceId = traceId)
            store.save(session)
        }
        val now = System.currentTimeMillis()
        val fresh = WaterTrace(
            traceId = traceId,
            startedAt = session.startedAt,
            updatedAt = now,
            source = "接水",
            deviceName = session.deviceName,
            goodsId = session.goodsId,
        )
        trace = fresh
        diagnostics.startTrace(fresh)
        return session
    }

    private suspend fun settle(session: WaterSession) {
        var updated = session
        val decision = SettlementDecider.decide(session)
        updated = updated.copy(
            settleStartedAt = updated.settleStartedAt.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
        )
        when (decision) {
            SettlementDecider.Decision.NoCharge -> {
                diagnostics.event(
                    DiagLevel.Info,
                    DiagFlow.Water,
                    "settle_no_charge",
                    traceId = updated.traceId,
                    fields = mapOf("amount" to updated.amount, "identify" to updated.identify),
                )
                finish(
                    updated.copy(
                        phase = SessionPhase.FINISHED_NO_CHARGE,
                        endedAt = updated.endedAt.takeIf { it > 0L }
                            ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return
            }
            SettlementDecider.Decision.AlreadyAttempted -> {
                diagnostics.event(
                    DiagLevel.Warn,
                    DiagFlow.Water,
                    "settle_already_attempted",
                    traceId = updated.traceId,
                    fields = mapOf("amount" to updated.amount),
                )
                finish(
                    updated.copy(
                        phase = SessionPhase.PAY_UNKNOWN,
                        error = "结算单状态未知，请到原 App 核对",
                        endedAt = updated.endedAt.takeIf { it > 0L }
                            ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return
            }
            is SettlementDecider.Decision.CreateOrder -> {
                updated = updated.copy(identify = decision.identify)
                diagnostics.event(
                    DiagLevel.Info,
                    DiagFlow.Water,
                    "settle_create_order",
                    traceId = updated.traceId,
                    fields = mapOf("amount" to updated.amount, "identify" to updated.identify),
                )
            }
        }

        updated = updated.copy(
            phase = SessionPhase.SETTLING,
            settleAttempted = true,
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)

        try {
            val order = repository.afterPayCreate(session.identify)
            updated = updated.copy(
                payOrderNo = order.orderNo,
                payOrderId = order.orderId,
                payType = order.payType,
                updatedAt = System.currentTimeMillis(),
            )
            save(updated)
            diagnostics.event(
                DiagLevel.Info,
                DiagFlow.Water,
                "after_pay_create",
                traceId = updated.traceId,
                fields = mapOf(
                    "orderNo" to order.orderNo,
                    "payType" to order.payType,
                ),
            )
            val pay = pollPay(updated, order.orderNo, order.payType)
            updated = updated.copy(
                phase = if (pay.paid) SessionPhase.PAID else SessionPhase.PAY_UNKNOWN,
                error = if (pay.paid) "" else "支付状态未确认，请到原 App 核对",
                payPollAttempts = pay.attempts,
                endedAt = updated.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            diagnostics.event(
                if (pay.paid) DiagLevel.Info else DiagLevel.Warn,
                DiagFlow.Water,
                "pay_result",
                message = updated.error,
                traceId = updated.traceId,
                fields = mapOf(
                    "paid" to pay.paid.toString(),
                    "attempts" to pay.attempts.toString(),
                    "orderNo" to order.orderNo,
                ),
            )
            finish(updated)
        } catch (throwable: Throwable) {
            updated = updated.copy(
                phase = SessionPhase.PAY_UNKNOWN,
                error = throwable.userMessage(),
                endedAt = updated.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            diagnostics.event(
                DiagLevel.Error,
                DiagFlow.Water,
                "pay_fail",
                message = throwable.userMessage(),
                traceId = updated.traceId,
            )
            finish(updated)
        }
    }

    private suspend fun resumeSettlement(session: WaterSession) {
        if (session.payOrderNo.isBlank()) {
            finish(
                session.copy(
                    phase = SessionPhase.PAY_UNKNOWN,
                    error = "结算单状态未知，请到原 App 核对",
                    endedAt = session.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Water,
            "resume_settlement",
            traceId = session.traceId,
            fields = mapOf("orderNo" to session.payOrderNo),
        )
        val pay = pollPay(session, session.payOrderNo, session.payType)
        diagnostics.event(
            if (pay.paid) DiagLevel.Info else DiagLevel.Warn,
            DiagFlow.Water,
            "pay_result",
            traceId = session.traceId,
            fields = mapOf(
                "paid" to pay.paid.toString(),
                "attempts" to pay.attempts.toString(),
                "orderNo" to session.payOrderNo,
            ),
        )
        finish(
            session.copy(
                phase = if (pay.paid) SessionPhase.PAID else SessionPhase.PAY_UNKNOWN,
                error = if (pay.paid) "" else "支付状态未确认，请到原 App 核对",
                payPollAttempts = pay.attempts,
                endedAt = session.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun pollPay(
        session: WaterSession,
        orderNo: String,
        payType: String,
    ): PayOutcome {
        val deadline = System.currentTimeMillis() + PAY_TIMEOUT_MS
        var attempts = 0
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            attempts += 1
            if (repository.orderPayStatus(orderNo, payType)) return PayOutcome(true, attempts)
            delay(1_000)
        }
        return PayOutcome(false, attempts)
    }

    private suspend fun save(session: WaterSession) {
        current = session
        store.save(session)
        persistTrace(session)
        NotificationChannels.notify(
            this,
            session,
            terminal = session.phase in SessionPhase.TERMINAL,
            conversion = waterConversion,
        )
    }

    private suspend fun persistTrace(session: WaterSession) {
        val currentTrace = trace ?: return
        val next = tracker.track(currentTrace, session)
        trace = next
        diagnostics.replaceTrace(next)
    }

    private fun finish(session: WaterSession) {
        val endedAt = if (session.phase in SessionPhase.TERMINAL && session.endedAt == 0L) {
            System.currentTimeMillis()
        } else {
            session.endedAt
        }
        val finalSession = session.copy(endedAt = endedAt)
        current = finalSession
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Water,
            "terminal",
            traceId = finalSession.traceId,
            fields = mapOf(
                "phase" to finalSession.phase,
                "amount" to finalSession.amount,
                "error" to finalSession.error,
            ),
        )
        scope.launch {
            store.save(finalSession)
            persistTrace(finalSession)
            historyRepository.save(finalSession)
            NotificationChannels.notify(
                this@WaterSyncService,
                finalSession,
                terminal = true,
                conversion = waterConversion,
            )
            ServiceCompat.stopForeground(this@WaterSyncService, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun stopSession() {
        job?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(session: WaterSession?) {
        val notification = NotificationChannels.build(
            this,
            session,
            conversion = waterConversion,
        )
        ServiceCompat.startForeground(
            this,
            NotificationChannels.SESSION_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private data class PayOutcome(
        val paid: Boolean,
        val attempts: Int,
    )

    companion object {
        private const val ACTION_START = "io.github.winddrift.fuckpangguai.action.START_WATER"
        private const val ACTION_END = "io.github.winddrift.fuckpangguai.action.END_WATER"
        private const val ACTION_STOP = "io.github.winddrift.fuckpangguai.action.STOP_WATER"
        private const val EXTRA_SESSION = "session"
        private const val PAY_TIMEOUT_MS = 5 * 60_000L

        fun start(context: Context, session: WaterSession) {
            val intent = Intent(context, WaterSyncService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, Json.encodeToString(WaterSession.serializer(), session))
            ContextCompat.startForegroundService(context, intent)
        }

        fun end(context: Context) {
            val intent = Intent(context, WaterSyncService::class.java).setAction(ACTION_END)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WaterSyncService::class.java))
        }
    }
}
