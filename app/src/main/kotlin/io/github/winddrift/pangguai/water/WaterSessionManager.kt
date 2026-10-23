package io.github.winddrift.pangguai.water

import android.content.Context
import io.github.winddrift.pangguai.confirm.UnlockSession
import io.github.winddrift.pangguai.service.WaterSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WaterSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: WaterSessionStore,
    private val waterRepository: WaterRepository,
) {
    val session = store.session

    init {
        scope.launch {
            val persisted = store.load() ?: return@launch
            if (persisted.phase in SessionPhase.ACTIVE) {
                WaterSyncService.start(context, persisted)
            }
        }
    }

    fun start(unlocked: UnlockSession) {
        scope.launch {
            val session = WaterSession(
                goodsId = unlocked.device.scan.id,
                skuId = unlocked.sku.skuId,
                deviceName = unlocked.device.detail.name.ifBlank { "饮水机" },
                imei = unlocked.device.detail.imei,
                orderNo = unlocked.unlock.orderNo,
                msgId = unlocked.unlock.msgId,
                supportAppUserFinish = unlocked.device.detail.supportAppUserFinish,
                startedAt = System.currentTimeMillis(),
            )
            store.save(session)
            WaterSyncService.start(context, session)
        }
    }

    fun end() {
        WaterSyncService.end(context)
    }

    fun clear() {
        WaterSyncService.stop(context)
        scope.launch { store.clear() }
    }
}
