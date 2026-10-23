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
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.SettlementDecider
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
    private lateinit var store: WaterSessionStore
    private lateinit var repository: WaterRepository
    private lateinit var historyRepository: HistoryRepository
    private var job: Job? = null

    @Volatile
    private var current: WaterSession? = null

    @Volatile
    private var endRequested = false

    private var endSent = false

    override fun onCreate() {
        super.onCreate()
        val graph = (application as PangguaiApp).graph
        store = graph.waterSessionStore
        repository = graph.waterRepository
        historyRepository = graph.historyRepository
        NotificationChannels.ensure(this)
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
            val updated = session.copy(
                phase = SessionPhase.PAY_UNKNOWN,
                error = "前台服务超时，请回到 App 或原 App 核对",
                updatedAt = System.currentTimeMillis(),
            )
            current = updated
            scope.launch {
                store.save(updated)
                historyRepository.save(updated)
                NotificationChannels.notify(this@WaterSyncService, updated, terminal = true)
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
        var session = initial
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

        while (currentCoroutineContext().isActive) {
            if (endRequested && !endSent) {
                endSent = true
                runCatching { repository.endWater(session.goodsId) }
                session = session.copy(phase = SessionPhase.ENDING, updatedAt = System.currentTimeMillis())
                save(session)
            }

            try {
                val sync = repository.waterSync(session.skuId)
                session = session.copy(
                    amount = sync.amount.ifBlank { session.amount },
                    identify = sync.identify.ifBlank { session.identify },
                    updatedAt = System.currentTimeMillis(),
                )
                save(session)

                when (sync.status) {
                    3, 5 -> {
                        settle(session)
                        return
                    }
                    6 -> {
                        session = session.copy(
                            phase = SessionPhase.FAULT,
                            error = "设备异常",
                            updatedAt = System.currentTimeMillis(),
                        )
                        finish(session)
                        return
                    }
                }
            } catch (throwable: Throwable) {
                session = session.copy(
                    error = throwable.userMessage(),
                    updatedAt = System.currentTimeMillis(),
                )
                save(session)
                delay(3_000)
            }

            delay(1_500)
        }
    }

    private suspend fun settle(session: WaterSession) {
        var updated = session
        when (val decision = SettlementDecider.decide(session)) {
            SettlementDecider.Decision.NoCharge -> {
                finish(
                    updated.copy(
                        phase = SessionPhase.FINISHED_NO_CHARGE,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return
            }
            SettlementDecider.Decision.AlreadyAttempted -> {
                finish(
                    updated.copy(
                        phase = SessionPhase.PAY_UNKNOWN,
                        error = "结算单状态未知，请到原 App 核对",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return
            }
            is SettlementDecider.Decision.CreateOrder -> {
                updated = updated.copy(identify = decision.identify)
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
                payType = order.payType,
                updatedAt = System.currentTimeMillis(),
            )
            save(updated)
            val paid = pollPay(updated, order.orderNo, order.payType)
            updated = updated.copy(
                phase = if (paid) SessionPhase.PAID else SessionPhase.PAY_UNKNOWN,
                error = if (paid) "" else "支付状态未确认，请到原 App 核对",
                updatedAt = System.currentTimeMillis(),
            )
            finish(updated)
        } catch (throwable: Throwable) {
            updated = updated.copy(
                phase = SessionPhase.PAY_UNKNOWN,
                error = throwable.userMessage(),
                updatedAt = System.currentTimeMillis(),
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
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        val paid = pollPay(session, session.payOrderNo, session.payType)
        finish(
            session.copy(
                phase = if (paid) SessionPhase.PAID else SessionPhase.PAY_UNKNOWN,
                error = if (paid) "" else "支付状态未确认，请到原 App 核对",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun pollPay(
        session: WaterSession,
        orderNo: String,
        payType: String,
    ): Boolean {
        val deadline = System.currentTimeMillis() + PAY_TIMEOUT_MS
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            if (repository.orderPayStatus(orderNo, payType)) return true
            delay(1_000)
        }
        return false
    }

    private suspend fun save(session: WaterSession) {
        current = session
        store.save(session)
        NotificationChannels.notify(
            this,
            session,
            terminal = session.phase in SessionPhase.TERMINAL,
        )
    }

    private fun finish(session: WaterSession) {
        current = session
        scope.launch {
            store.save(session)
            historyRepository.save(session)
            NotificationChannels.notify(this@WaterSyncService, session, terminal = true)
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
        val notification = NotificationChannels.build(this, session)
        ServiceCompat.startForeground(
            this,
            NotificationChannels.SESSION_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

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
