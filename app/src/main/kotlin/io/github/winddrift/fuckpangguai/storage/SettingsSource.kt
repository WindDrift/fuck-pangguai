package io.github.winddrift.fuckpangguai.storage

import io.github.winddrift.fuckpangguai.confirm.UnlockAcceleration

interface SettingsSource {
    suspend fun isOneTapEnabled(): Boolean
    suspend fun isDryRun(): Boolean
    suspend fun getUnlockAcceleration(): UnlockAcceleration
}

interface DeviceConfigSource {
    suspend fun find(goodsId: String): ConfirmedDevice?
    suspend fun save(device: ConfirmedDevice)
}
