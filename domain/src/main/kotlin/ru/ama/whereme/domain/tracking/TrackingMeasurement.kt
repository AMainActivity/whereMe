package ru.ama.whereme.domain.tracking

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.reportFailure
import ru.ama.whereme.domain.repository.LocationSource

/** One bounded measurement. It never mutates the coordinator's session or schedules another alarm. */
internal class TrackingMeasurement(
    private val source: LocationSource,
    private val recorder: LocationRecorder,
    private val clock: TrackingClock
) {
    data class Request(
        val mode: TrackingMode,
        val settings: SettingsDomModel,
        val endsAt: Instant?,
        val previousRowId: Long?
    )

    sealed interface Result {
        data class Completed(val rowId: Long?, val failure: TrackingFailure? = null) : Result
        data class Ended(val state: TrackingState) : Result
    }

    suspend fun run(
        request: Request,
        ensureOpen: suspend () -> Unit,
        progress: (TrackingState) -> Unit
    ): Result {
        var stage = TrackingFailure.LOCATION
        return try {
            val end = request.endsAt
            if (end != null && clock.now() >= end) return Result.Ended(TrackingState.Stopped)
            suspend fun collect(): Result {
                ensureOpen()
                val timeout = minOf(
                    TimeUnit.SECONDS.toMillis(request.settings.timeOfWaitAccuracy.toLong()),
                    TimeUnit.SECONDS.toMillis(request.settings.timeOfWorkingWM.toLong()),
                    (end?.let { Duration.between(clock.now(), it).toMillis() } ?: Long.MAX_VALUE).coerceAtLeast(1)
                )
                progress(TrackingState.Collecting(request.mode, TimeUnit.NANOSECONDS.toMillis(clock.elapsedRealtimeNanos()) + timeout, timeout))
                val result = source.locate(request.settings.accuracy, timeout)
                ensureOpen()
                if (result is LocationOutcome.Unavailable && result.reason != LocationOutcome.Reason.NO_FIX) {
                    return Result.Ended(TrackingState.LocationUnavailable(result.reason))
                }
                val sample = (result as? LocationOutcome.Fix)?.sample
                    ?.takeIf { it.isUsable(clock.elapsedRealtimeNanos()) }
                    ?: return Result.Completed(null)
                stage = TrackingFailure.STORAGE
                progress(TrackingState.Saving(request.mode))
                ensureOpen()
                val rowId = recorder.record(sample, request.settings, request.previousRowId)
                ensureOpen()
                return Result.Completed(rowId)
            }
            // The source owns its measurement timeout and returns best at the boundary.
            // End of schedule closes acquisition, not the transaction for its last result.
            collect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            val state = if (stage == TrackingFailure.LOCATION)
                TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED)
            else TrackingState.Failed(stage)
            Result.Ended(state)
        } catch (failure: Exception) {
            reportFailure("Measurement $stage", failure)
            Result.Completed(null, stage)
        }
    }
}
