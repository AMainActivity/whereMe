package ru.ama.whereme.domain.tracking

import kotlinx.coroutines.flow.StateFlow

enum class TrackingRequirement {
    FOREGROUND_LOCATION,
    LOCATION_ENABLED,
    PRECISE_LOCATION,
    PROVIDER,
    BACKGROUND_LOCATION,
    EXACT_ALARM,
    READY,
}

data class TrackingCapabilities(
    val foreground: Boolean,
    val locationEnabled: Boolean,
    val provider: Boolean,
    val preciseWouldProvideLocation: Boolean,
    val background: Boolean,
    val exactAlarm: Boolean,
) {
    fun requirement(): TrackingRequirement =
        when {
            !foreground -> TrackingRequirement.FOREGROUND_LOCATION
            !locationEnabled -> TrackingRequirement.LOCATION_ENABLED
            !provider && preciseWouldProvideLocation -> TrackingRequirement.PRECISE_LOCATION
            !provider -> TrackingRequirement.PROVIDER
            !background -> TrackingRequirement.BACKGROUND_LOCATION
            !exactAlarm -> TrackingRequirement.EXACT_ALARM
            else -> TrackingRequirement.READY
        }
}

/** Platform operations exposed to screens without an Activity or service reference. */
interface TrackingControl {
    val state: StateFlow<TrackingState>

    fun capabilities(): TrackingCapabilities

    suspend fun setSchedule(enabled: Boolean)

    suspend fun refreshSchedule()

    fun start()

    fun stop()
}
