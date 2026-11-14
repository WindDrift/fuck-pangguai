package io.github.winddrift.fuckpangguai.water

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一次"解锁请求已成功、但设备启动未确认"的待对账记录。
 *
 * 解锁接口返回成功后订单就已经产生；轮询超时不能把订单丢掉，否则用户再点一次就会重复下单。
 * 这里只保存对账需要的订单标识与创建接水会话所需的字段，不落盘 imei/SN。
 */
@Serializable
data class PendingUnlock(
    val orderNo: String,
    val msgId: String,
    val goodsId: String,
    val skuId: String,
    val deviceName: String = "",
    val supportAppUserFinish: Boolean = false,
    val traceId: String = "",
    val payType: String = "",
    val usePoints: Boolean = false,
    val createdAt: Long = 0L,
    val probes: Int = 0,
) {
    /**
     * 对账成功后的接水会话：沿用原订单号、msgId 与 trace，保证不会产生第二笔订单。
     * imei 留空——它没有任何读取方，没必要额外落盘设备标识。
     */
    fun toSession(now: Long, accountId: String): WaterSession = WaterSession(
        goodsId = goodsId,
        skuId = skuId,
        deviceName = deviceName.ifBlank { "饮水机" },
        imei = "",
        orderNo = orderNo,
        msgId = msgId,
        supportAppUserFinish = supportAppUserFinish,
        startedAt = now,
        phase = SessionPhase.USING,
        payType = payType.ifBlank { "0" },
        updatedAt = now,
        accountId = accountId,
        traceId = traceId,
    )
}

interface PendingUnlockStore {
    suspend fun load(): PendingUnlock?
    suspend fun save(pending: PendingUnlock)
    suspend fun clear()
}

/** 默认实现：不持久化。JVM 单测与不关心对账的调用方直接使用。 */
object NoopPendingUnlockStore : PendingUnlockStore {
    override suspend fun load(): PendingUnlock? = null

    override suspend fun save(pending: PendingUnlock) = Unit

    override suspend fun clear() = Unit
}

class DataStorePendingUnlockStore(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PendingUnlockStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(): PendingUnlock? {
        val raw = context.appDataStore.data.first()[KEY] ?: return null
        val pending = runCatching {
            json.decodeFromString(PendingUnlock.serializer(), raw)
        }.getOrNull()
        if (pending == null) {
            clear()
            return null
        }
        if (now() - pending.createdAt > PendingUnlockPolicy.TTL_MS) {
            clear()
            return null
        }
        return pending
    }

    override suspend fun save(pending: PendingUnlock) {
        if (pending.orderNo.isBlank()) return
        val encoded = json.encodeToString(PendingUnlock.serializer(), pending)
        context.appDataStore.edit { it[KEY] = encoded }
    }

    override suspend fun clear() {
        context.appDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("pending_unlock_v1")
    }
}