package ru.ama.whereme.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample

/** All enabled providers share one window. Only a normal timeout returns the best fallback. */
suspend fun selectLocationFix(
    updates: Flow<LocationOutcome>, accuracyMeters: Int, timeoutMillis: Long
): LocationOutcome {
    require(accuracyMeters > 0 && timeoutMillis > 0)
    var best: LocationSample? = null
    val result = withTimeoutOrNull(timeoutMillis) {
        updates.first { outcome ->
            when (outcome) {
                is LocationOutcome.Unavailable -> true
                is LocationOutcome.Fix -> {
                    val previous = best
                    if (previous == null || outcome.sample.accuracyMeters <= previous.accuracyMeters) best = outcome.sample
                    outcome.sample.accuracyMeters <= accuracyMeters
                }
            }
        }
    }
    currentCoroutineContext().ensureActive()
    return result ?: best?.let(LocationOutcome::Fix) ?: LocationOutcome.Unavailable(LocationOutcome.Reason.NO_FIX)
}
