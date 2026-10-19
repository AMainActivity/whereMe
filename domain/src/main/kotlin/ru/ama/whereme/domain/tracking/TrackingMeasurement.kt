package ru.ama.whereme.domain.tracking

import ru.ama.whereme.domain.reportFailure
import kotlinx.coroutines.CancellationException
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
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
        val endsAtMillis: Long?,
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
            val end = request.endsAtMillis
            if (end != null && clock.nowMillis() >= end) return Result.Ended(TrackingState.Stopped)
            suspend fun collect(): Result {
                ensureOpen()
                val timeout = minOf(
                    request.settings.timeOfWaitAccuracy.toLong() * 1000,
                    request.settings.timeOfWorkingWM.toLong() * 1000,
                    (end?.minus(clock.nowMillis()) ?: Long.MAX_VALUE).coerceAtLeast(1)
                )
                progress(TrackingState.Collecting(request.mode, clock.elapsedRealtimeNanos() / 1_000_000 + timeout, timeout))
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
