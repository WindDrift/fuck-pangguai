package io.github.winddrift.fuckpangguai.location

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

interface LocationSource {
    fun hasPermission(): Boolean
    suspend fun getCurrentLocation(): GeoPoint?
}
