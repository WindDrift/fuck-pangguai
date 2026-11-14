package io.github.winddrift.fuckpangguai.water

import android.content.Context
import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.confirm.UnlockSession
import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.NoopDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.service.WaterSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WaterSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: WaterSessionStore,
    private val waterRepository: WaterRepository,
    private val accountStore: AccountStore,
    private val diagnostics: DiagSink = NoopDiagSink,
) {
    val session = store.session

    init {
        scope.launch {
            val persisted = store.load() ?: return@launch
            if (persisted.phase in SessionPhase.ACTIVE) {
                diagnostics.event(
                    DiagLevel.Debug,
                    DiagFlow.Water,
                    "session_recover",
                    traceId = persisted.traceId,
                    fields = mapOf(
                        "goodsId" to persisted.goodsId,
                        "phase" to persisted.phase,
                    ),
                )
                WaterSyncService.start(context, persisted)
            }
        }
    }

    fun start(unlocked: UnlockSession) {
        scope.launch {
            val session = unlocked.toWaterSession(accountStore.activeLocalId().orEmpty())
            store.save(session)
            diagnostics.event(
                DiagLevel.Info,
                DiagFlow.Water,
                "session_create",
                traceId = session.traceId,
                fields = mapOf(
                    "goodsId" to session.goodsId,
                    "skuId" to session.skuId,
                    "deviceName" to session.deviceName,
                    "supportAppUserFinish" to session.supportAppUserFinish.toString(),
                ),
            )
            WaterSyncService.start(context, session)
        }
    }

    /** 对账接管的会话与正常解锁创建的会话字段必须一致，因此共用同一份构造逻辑。 */
    private fun UnlockSession.toWaterSession(accountId: String): WaterSession = WaterSession(
        goodsId = device.scan.id,
        skuId = sku.skuId,
        deviceName = device.detail.name.ifBlank { "饮水机" },
        imei = device.detail.imei,
        orderNo = unlock.orderNo,
        msgId = unlock.msgId,
        supportAppUserFinish = device.detail.supportAppUserFinish,
        startedAt = System.currentTimeMillis(),
        accountId = accountId,
        traceId = traceId,
    )

    fun end() {
        val current = store.session.value
        val phase = current?.phase
        if (phase == SessionPhase.SETTLING || phase == SessionPhase.ENDING) return
        diagnostics.event(
            DiagLevel.Info,
            DiagFlow.Water,
            "end_request",
            traceId = current?.traceId.orEmpty(),
            fields = mapOf("goodsId" to current?.goodsId.orEmpty()),
        )
        WaterSyncService.end(context)
    }

    fun clear() {
        val current = store.session.value
        diagnostics.event(
            DiagLevel.Debug,
            DiagFlow.Water,
            "session_clear",
            traceId = current?.traceId.orEmpty(),
            fields = mapOf("phase" to current?.phase.orEmpty()),
        )
        WaterSyncService.stop(context)
        scope.launch { store.clear() }
    }
}
