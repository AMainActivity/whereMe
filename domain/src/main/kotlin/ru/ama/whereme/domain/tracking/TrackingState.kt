package ru.ama.whereme.domain.tracking

import java.time.Instant
import ru.ama.whereme.domain.entity.LocationOutcome

enum class TrackingMode { MANUAL, SCHEDULED }
enum class TrackingFailure { SETTINGS, LOCATION, STORAGE, FOREGROUND, ALARM }

sealed interface TrackingState {
    val isRunning: Boolean get() = this is Starting || this is Collecting || this is Saving ||
        this is Uploading || this is Waiting || this is Armed || this is Stopping

    data class Starting(val mode: TrackingMode) : TrackingState
    data class Armed(val mode: TrackingMode, val nextAt: Instant) : TrackingState
    data class Collecting(val mode: TrackingMode, val deadlineElapsedMillis: Long, val timeoutMillis: Long) : TrackingState
    data class Saving(val mode: TrackingMode) : TrackingState
    data class Uploading(val mode: TrackingMode, val noFix: Boolean) : TrackingState
    data class Waiting(val mode: TrackingMode, val noFix: Boolean, val syncFailed: Boolean,
        val failure: TrackingFailure? = null) : TrackingState
    data object Stopping : TrackingState
    data object Stopped : TrackingState
    data class LocationUnavailable(val reason: LocationOutcome.Reason) : TrackingState
    data class Failed(val reason: TrackingFailure) : TrackingState
}

/** State for one service connection, correlated with the native Android startId. */
data class TrackingServiceState(val startId: Int, val state: TrackingState)
