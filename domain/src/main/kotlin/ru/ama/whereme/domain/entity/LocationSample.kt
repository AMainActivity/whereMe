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
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters >= 0 &&
            speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0 &&
            timeMillis > 0 && elapsedRealtimeNanos > 0 &&
            nowElapsedRealtimeNanos >= elapsedRealtimeNanos
}

sealed interface LocationOutcome {
    data class Fix(val sample: LocationSample) : LocationOutcome
    data class Unavailable(val reason: Reason) : LocationOutcome
    enum class Reason { PERMISSION_REQUIRED, LOCATION_DISABLED, NO_PROVIDER, NO_FIX }
}
