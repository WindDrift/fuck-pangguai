package io.github.winddrift.fuckpangguai.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class LocationProvider(
    context: Context,
    private val elapsedNow: () -> Long = { SystemClock.elapsedRealtime() },
) : LocationSource {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val executor = ContextCompat.getMainExecutor(appContext)
    private val cache = LocationCache(CACHE_TTL_MS)
    private val requestMutex = Mutex()

    @Volatile
    private var inFlight: InFlight? = null

    private data class InFlight(
        val startedAt: Long,
        val deferred: Deferred<GeoPoint?>,
    )

    private data class TimedPoint(val point: GeoPoint, val ageMs: Long)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun warmUp() {
        if (!hasPermission()) return
        scope.launch {
            try {
                getCurrentLocation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
            }
        }
    }

    override suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasPermission()) return null

        cache.get(elapsedNow())?.let { return it }

        val freshLastKnown = readFreshestLastKnown()?.takeIf { isFresh(it.ageMs) }
        if (freshLastKnown != null) {
            cache.put(freshLastKnown.point, elapsedNow())
            return freshLastKnown.point
        }

        val request = obtainRequest()
        val remaining = (REQUEST_BUDGET_MS - (elapsedNow() - request.startedAt)).coerceAtLeast(0L)
        val point = if (remaining > 0) {
            withTimeoutOrNull(remaining) { request.deferred.await() }
        } else {
            null
        }
        if (point != null) {
            cache.put(point, elapsedNow())
            return point
        }

        // 实时定位失败时只回退旧坐标，不把它当作新的 5 分钟缓存。
        return readFreshestLastKnown()?.point
    }

    private suspend fun obtainRequest(): InFlight = requestMutex.withLock {
        val now = elapsedNow()
        val current = inFlight
        if (current != null && current.deferred.isActive && now - current.startedAt < REQUEST_BUDGET_MS) {
            current
        } else {
            InFlight(
                startedAt = now,
                deferred = scope.async { fetchFresh() },
            ).also { inFlight = it }
        }
    }

    private suspend fun fetchFresh(): GeoPoint? {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return null

        val freshLastKnown = readFreshestLastKnown(manager)?.takeIf { isFresh(it.ageMs) }
        if (freshLastKnown != null) {
            cache.put(freshLastKnown.point, elapsedNow())
            return freshLastKnown.point
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        val current = withTimeoutOrNull(REQUEST_BUDGET_MS) {
            requestFirstCurrent(manager, providers)
        }
        val point = current?.toGeoPoint()
        if (point != null) {
            cache.put(point, elapsedNow())
            return point
        }
        return readFreshestLastKnown(manager)?.point
    }

    @SuppressLint("MissingPermission")
    private fun readFreshestLastKnown(
        manager: LocationManager? = appContext.getSystemService(LocationManager::class.java),
    ): TimedPoint? {
        if (manager == null || !hasPermission()) return null
        return LAST_KNOWN_PROVIDERS.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.mapNotNull { location ->
            val ageMs = locationAgeMs(location) ?: return@mapNotNull null
            TimedPoint(location.toGeoPoint(), ageMs)
        }.minByOrNull { it.ageMs }
    }

    private fun locationAgeMs(location: Location): Long? {
        if (location.elapsedRealtimeNanos <= 0L) return null
        val ageNanos = elapsedNow() * 1_000_000L - location.elapsedRealtimeNanos
        return (ageNanos / 1_000_000L).coerceAtLeast(0L)
    }

    private fun isFresh(ageMs: Long): Boolean =
        ageMs >= 0 && ageMs <= LAST_KNOWN_FRESH_MS

    @SuppressLint("MissingPermission")
    private suspend fun requestFirstCurrent(
        manager: LocationManager,
        providers: List<String>,
    ): Location? {
        if (providers.isEmpty()) return null
        return suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val pending = AtomicInteger(providers.size)
            val signals = providers.map { CancellationSignal() }

            fun finish(location: Location?) {
                if (!finished.compareAndSet(false, true)) return
                signals.forEach { signal -> runCatching { signal.cancel() } }
                if (continuation.isActive) continuation.resume(location)
            }

            fun oneDone() {
                if (pending.decrementAndGet() == 0) finish(null)
            }

            continuation.invokeOnCancellation {
                finished.set(true)
                signals.forEach { signal -> runCatching { signal.cancel() } }
            }

            providers.forEachIndexed { index, provider ->
                try {
                    manager.getCurrentLocation(provider, signals[index], executor) { location ->
                        if (location == null) oneDone() else finish(location)
                    }
                } catch (_: Throwable) {
                    oneDone()
                }
            }
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60_000L
        const val LAST_KNOWN_FRESH_MS = 5 * 60_000L
        const val REQUEST_BUDGET_MS = 4_000L
        val LAST_KNOWN_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}

private fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
