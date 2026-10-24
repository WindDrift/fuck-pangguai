package io.github.winddrift.fuckpangguai.location

internal class LocationCache(private val ttlMs: Long) {
    private var point: GeoPoint? = null
    private var storedAt: Long = 0L

    @Synchronized
    fun get(now: Long): GeoPoint? {
        val cached = point ?: return null
        val age = now - storedAt
        return cached.takeIf { age in 0..ttlMs }
    }

    @Synchronized
    fun put(point: GeoPoint, now: Long) {
        this.point = point
        storedAt = now
    }
}
