package io.github.winddrift.fuckpangguai.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) : LocationSource {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }

        for (provider in providers) {
            val location = withTimeoutOrNull(10_000) { requestCurrent(manager, provider) }
            if (location != null) return location.toGeoPoint()
        }

        return providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider)?.toGeoPoint() }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        val executor = ContextCompat.getMainExecutor(context)
        try {
            manager.getCurrentLocation(provider, signal, executor) { location ->
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(location)
                }
            }
        } catch (throwable: SecurityException) {
            if (finished.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(null)
            }
        } catch (throwable: Throwable) {
            if (finished.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}

private fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
