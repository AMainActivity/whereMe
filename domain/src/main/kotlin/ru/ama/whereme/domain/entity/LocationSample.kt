package ru.ama.whereme.domain.entity

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long
) {
    fun isUsable(nowElapsedRealtimeNanos: Long): Boolean =
        latitude.isFinite() && latitude in LATITUDE_RANGE &&
            longitude.isFinite() && longitude in LONGITUDE_RANGE &&
            accuracyMeters.isFinite() && accuracyMeters >= 0 &&
            speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0 &&
            timeMillis > 0 && elapsedRealtimeNanos > 0 &&
            nowElapsedRealtimeNanos >= elapsedRealtimeNanos

    companion object {
        val LATITUDE_RANGE = -90.0..90.0
        val LONGITUDE_RANGE = -180.0..180.0
    }
}

sealed interface LocationOutcome {
    data class Fix(val sample: LocationSample) : LocationOutcome
    data class Unavailable(val reason: Reason) : LocationOutcome
    enum class Reason { PERMISSION_REQUIRED, LOCATION_DISABLED, NO_PROVIDER, NO_FIX }
}
