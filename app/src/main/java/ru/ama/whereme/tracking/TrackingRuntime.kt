package ru.ama.whereme.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ama.whereme.domain.entity.LocationOutcome
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TrackingState {
    val isRunning: Boolean get() = this is Starting || this is Collecting || this is Waiting
    data object Starting : TrackingState
    data object Stopped : TrackingState
    data object Collecting : TrackingState
    data class Waiting(val noFix: Boolean) : TrackingState
    data class LocationUnavailable(val reason: LocationOutcome.Reason) : TrackingState
    data object Failed : TrackingState
}

/** Process-local service state, reported by the service, independently of configured schedules. */
@Singleton
class TrackingRuntime @Inject constructor() {
    private val mutableState = MutableStateFlow<TrackingState>(TrackingState.Stopped)
    val state = mutableState.asStateFlow()

    // MyForegroundService is the only writer. Screens observe the read-only flow.
    internal fun publish(state: TrackingState) { mutableState.value = state }
    internal fun stopped() {
        if (mutableState.value.isRunning) mutableState.value = TrackingState.Stopped
    }
}
