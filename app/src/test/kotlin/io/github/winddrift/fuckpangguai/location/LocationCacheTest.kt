package io.github.winddrift.fuckpangguai.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationCacheTest {

    @Test
    fun `returns point within ttl`() {
        val cache = LocationCache(ttlMs = 1_000L)
        cache.put(GeoPoint(30.0, 120.0), now = 100L)

        assertEquals(GeoPoint(30.0, 120.0), cache.get(now = 1_100L))
    }

    @Test
    fun `returns null after ttl`() {
        val cache = LocationCache(ttlMs = 1_000L)
        cache.put(GeoPoint(30.0, 120.0), now = 100L)

        assertNull(cache.get(now = 1_101L))
    }

    @Test
    fun `returns null when empty`() {
        val cache = LocationCache(ttlMs = 1_000L)

        assertNull(cache.get(now = 100L))
    }

    @Test
    fun `returns null when time moves backwards`() {
        val cache = LocationCache(ttlMs = 1_000L)
        cache.put(GeoPoint(30.0, 120.0), now = 500L)

        assertNull(cache.get(now = 499L))
    }
}
