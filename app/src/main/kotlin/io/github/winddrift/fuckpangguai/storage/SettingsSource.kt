package io.github.winddrift.fuckpangguai.storage

interface SettingsSource {
    suspend fun isOneTapEnabled(): Boolean
    suspend fun isDryRun(): Boolean
}

interface DeviceConfigSource {
    suspend fun find(goodsId: String): ConfirmedDevice?
    suspend fun save(device: ConfirmedDevice)
}
