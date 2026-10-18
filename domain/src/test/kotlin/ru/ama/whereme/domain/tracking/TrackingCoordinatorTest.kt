package ru.ama.whereme.domain.tracking

import java.io.IOException
import java.time.Instant
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.ThemeMode
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.repository.SettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingCoordinatorTest {
    private val defaults = SettingsDomModel.DEFAULT.copy(timeOfWaitAccuracy = 20, timeOfWorkingWM = 15)

    private class Settings(initial: SettingsDomModel) : SettingsRepository {
        val values = MutableStateFlow(Result.success(initial))
        var readGate: CompletableDeferred<Unit>? = null
        override val settings = flow {
            readGate?.await()
            emitAll(values.map { it.getOrThrow() })
        }
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel): SettingsDomModel =
            transform(values.value.getOrThrow()).also { values.value = Result.success(it) }
        override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
        fun set(value: SettingsDomModel) { values.value = Result.success(value) }
        fun fail() { values.value = Result.failure(IOException("synthetic settings failure")) }
    }

    private class Fixture(scope: TestScope, initial: SettingsDomModel, epoch: String = "2026-10-19T12:00:00Z") {
        val settings = Settings(initial)
        var wallShift = 0L
        var zone = TimeZone.getTimeZone("UTC")
        private val origin = Instant.parse(epoch).toEpochMilli()
        val clock = object : TrackingClock {
            override fun nowMillis() = origin + wallShift + scope.testScheduler.currentTime
            override fun elapsedRealtimeNanos() = (100_000 + scope.testScheduler.currentTime) * 1_000_000
            override fun timeZone(): TimeZone = zone
        }
        fun fix() = LocationOutcome.Fix(LocationSample(0.0, 0.0, 10f, 0f, clock.nowMillis(), clock.elapsedRealtimeNanos()))
        var sourceHandler: suspend () -> LocationOutcome = { awaitCancellation() }
        var sourceCleanup: suspend () -> Unit = {}
        var calls = 0
        var active = 0
        var maximumActive = 0
        var cleanups = 0
        val accuracyRequests = mutableListOf<Int>()
        val timeouts = mutableListOf<Long>()
        private val source = object : LocationSource {
            override suspend fun locate(accuracyMeters: Int, timeoutMillis: Long): LocationOutcome {
                calls++; active++; maximumActive = maxOf(maximumActive, active)
                accuracyRequests.add(accuracyMeters); timeouts.add(timeoutMillis)
                try { return sourceHandler() }
                finally { sourceCleanup(); active--; cleanups++ }
            }
        }
        var recordHandler: suspend () -> Unit = {}
        var writes = 0
        var writeAttempts = 0
        val previousIds = mutableListOf<Long?>()
        private val recorder = LocationRecorder { _, _, previous ->
            writeAttempts++
            previousIds.add(previous)
            recordHandler()
            ++writes
            writes.toLong()
        }
        var uploadHandler: suspend () -> Unit = {}
        var uploads = 0
        private val uploader = PendingLocationUploader { uploads++; uploadHandler() }
        val coordinator = TrackingCoordinator(settings, source, recorder, uploader, clock, scope.backgroundScope)
        val handle = coordinator.attach()
        val state get() = coordinator.state.value.state
    }

    @Test fun repeatedStartDoesNotCreateASecondCollector() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        repeat(20) { f.handle.start(TrackingMode.MANUAL) }; runCurrent()
        assertEquals(1, f.calls); assertEquals(1, f.active)
        assertEquals(f.handle.latestRequest, f.coordinator.state.value.requestId)
    }

    @Test fun manualRecordingDoesNotDependOnDisabledOrEmptySchedule() = runTest {
        val f = Fixture(this, defaults.copy(days = List(7) { "0" }))
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(1, f.writes)
        assertEquals(TrackingMode.MANUAL, (f.state as TrackingState.Waiting).mode)
    }

    @Test fun stopCancelsLocationAndWaitsForCleanup() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop(); runCurrent()
        assertEquals(0, f.active); assertEquals(1, f.cleanups)
        assertEquals(TrackingState.Stopped, f.state); assertEquals(0, f.writes)
    }

    @Test fun stopDuringIntervalPreventsAnotherMeasurement() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue(f.state is TrackingState.Waiting)
        f.handle.stop(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun rapidStopThenStartKeepsTheNewRequestAlive() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop()
        val latest = f.handle.start(TrackingMode.MANUAL)
        runCurrent()
        assertTrue(f.state is TrackingState.Collecting)
        assertEquals(latest, f.coordinator.state.value.requestId)
        assertEquals(2, f.calls); assertEquals(1, f.active); assertEquals(1, f.maximumActive)
    }

    @Test fun closeImmediatelyInvalidatesAnUnprocessedStart() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL)
        f.handle.close(); runCurrent()
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(0, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun aNewServiceWaitsForPreviousProviderCleanup() = runTest {
        val f = Fixture(this, defaults)
        f.sourceCleanup = { if (f.calls == 1) withContext(NonCancellable) { delay(100) } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.close()
        val next = f.coordinator.attach()
        next.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(1, f.calls)
        advanceTimeBy(100); runCurrent()
        assertEquals(2, f.calls); assertEquals(1, f.maximumActive)
        assertEquals(next.ownerId, f.coordinator.state.value.ownerId)
        f.handle.stop(); runCurrent()
        assertTrue(f.state is TrackingState.Collecting)
    }

    @Test fun stoppedIsPublishedOnlyAfterProviderCleanupCompletes() = runTest {
        val f = Fixture(this, defaults)
        f.sourceCleanup = { withContext(NonCancellable) { delay(100) } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop(); runCurrent()
        assertEquals(TrackingState.Stopping, f.state); assertEquals(1, f.active)
        advanceTimeBy(100); runCurrent()
        assertEquals(TrackingState.Stopped, f.state); assertEquals(0, f.active)
    }

    @Test fun aLateProviderCallbackCannotSaveAfterStop() = runTest {
        val f = Fixture(this, defaults)
        var callback: CancellableContinuation<LocationOutcome>? = null
        f.sourceHandler = { suspendCancellableCoroutine { callback = it } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop(); runCurrent()
        requireNotNull(callback).resume(f.fix()); runCurrent()
        assertEquals(0, f.writes); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun stopCancelsAnUncommittedLocalWrite() = runTest {
        val f = Fixture(this, defaults)
        var cancelled = false
        f.sourceHandler = { f.fix() }
        f.recordHandler = { try { awaitCancellation() } finally { cancelled = true } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue(f.state is TrackingState.Saving)
        f.handle.stop(); runCurrent()
        assertTrue(cancelled); assertEquals(0, f.writes); assertEquals(0, f.uploads)
    }

    @Test fun stopCancelsUploadButKeepsTheCommittedPoint() = runTest {
        val f = Fixture(this, defaults)
        var cancelled = false
        f.sourceHandler = { f.fix() }
        f.uploadHandler = { try { awaitCancellation() } finally { cancelled = true } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue(f.state is TrackingState.Uploading); assertEquals(1, f.writes)
        f.handle.stop(); runCurrent()
        assertTrue(cancelled); assertEquals(1, f.writes); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun noFixIsRetriedWithoutWritingFakeCoordinates() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { LocationOutcome.Unavailable(LocationOutcome.Reason.NO_FIX) }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue((f.state as TrackingState.Waiting).noFix)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, f.calls); assertEquals(0, f.writes)
    }

    @Test fun synchronizationFailureDoesNotStopLocalRecording() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.uploadHandler = { throw IOException("synthetic sync failure") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue((f.state as TrackingState.Waiting).syncFailed)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, f.writes); assertTrue(f.state.isRunning)
    }

    @Test fun disabledScheduleDoesNotStartScheduledRecording() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        assertEquals(0, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun scheduledStartOutsideTheIntervalDoesNotCollect() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, end = "10:00"))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        assertEquals(0, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun intervalEndCancelsLocationInsteadOfWaitingForItsFullTimeout() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "12:00", end = "12:01"), "2026-10-19T12:00:50Z")
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        assertEquals(10_000L, f.timeouts.single())
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, f.active); assertEquals(0, f.writes); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun intervalEndAlsoCancelsAnUploadInProgress() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "12:00", end = "12:01"), "2026-10-19T12:00:50Z")
        var cancelled = false
        f.sourceHandler = { f.fix() }
        f.uploadHandler = { try { awaitCancellation() } finally { cancelled = true } }
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertTrue(cancelled); assertEquals(1, f.writes); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun disablingScheduleStopsAnActiveScheduledCollector() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        f.settings.set(defaults); runCurrent()
        assertEquals(0, f.active); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun disablingScheduleKeepsManualRecordingAvailable() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.settings.set(defaults); runCurrent()
        assertEquals(TrackingMode.MANUAL, (f.state as TrackingState.Collecting).mode)
        assertEquals(1, f.active); assertEquals(1, f.maximumActive)
    }

    @Test fun anAlarmCannotDowngradeManualRecordingToScheduledMode() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        assertEquals(TrackingMode.MANUAL, (f.state as TrackingState.Collecting).mode)
        assertEquals(1, f.calls)
    }

    @Test fun manualStartTakesOverAScheduledSessionWithoutOverlap() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingMode.MANUAL, (f.state as TrackingState.Collecting).mode)
        assertEquals(2, f.calls); assertEquals(1, f.maximumActive)
    }

    @Test fun settingsChangesReconfigureLocationAfterCleaningUpTheOldRequest() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.settings.set(defaults.copy(accuracy = 99)); runCurrent()
        assertEquals(listOf(50, 99), f.accuracyRequests)
        assertEquals(1, f.cleanups); assertEquals(1, f.maximumActive)
    }

    @Test fun wallClockChangeRechecksTheActiveScheduleImmediately() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "12:00", end = "13:00"))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        f.wallShift = 2 * 60 * 60 * 1000L
        f.handle.timeChanged(); runCurrent()
        assertEquals(0, f.active); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun timeZoneChangeRechecksTheIntervalInTheNewZone() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "12:00", end = "13:00"))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        f.zone = TimeZone.getTimeZone("Asia/Yekaterinburg")
        f.handle.timeChanged(); runCurrent()
        assertEquals(0, f.active); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun settingsReadFailureNeverStartsWithDefaultValues() = runTest {
        val f = Fixture(this, defaults)
        f.settings.fail()
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingState.Failed(TrackingFailure.SETTINGS), f.state); assertEquals(0, f.calls)
    }

    @Test fun explicitRetryRestartsSettingsObservationAfterReadFailure() = runTest {
        val f = Fixture(this, defaults)
        f.settings.fail(); f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.settings.set(defaults.copy(accuracy = 77))
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(listOf(77), f.accuracyRequests)
        assertTrue(f.state is TrackingState.Collecting)
    }

    @Test fun locationFailureIsReportedAndNeedsAnExplicitRestart() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { throw IOException("synthetic source failure") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingState.Failed(TrackingFailure.LOCATION), f.state)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, f.calls)
    }

    @Test fun failedLocalWriteDoesNotReportSuccessOrAttemptUpload() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }; f.recordHandler = { throw IOException("synthetic write failure") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingState.Failed(TrackingFailure.STORAGE), f.state)
        assertEquals(0, f.writes); assertEquals(0, f.uploads)
    }

    @Test fun revokedPermissionIsDistinctFromAnUnexpectedSourceFailure() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { throw SecurityException("synthetic revocation") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED), f.state)
        f.handle.close(); runCurrent()
        assertEquals(TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED), f.state)
    }

    @Test fun staleSampleIsNotPersistedEvenIfASourceReturnsIt() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { val fix = f.fix().sample; LocationOutcome.Fix(fix.copy(elapsedRealtimeNanos = fix.elapsedRealtimeNanos - 31_000_000_000L)) }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(0, f.writes); assertTrue((f.state as TrackingState.Waiting).noFix)
    }

    @Test fun explicitStopStartsANewPersistenceIntervalOnTheNextStart() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop(); runCurrent()
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(listOf(null, null), f.previousIds)
    }

    @Test fun foregroundFailureIsNotErasedWhenTheServiceClosesImmediately() = runTest {
        val f = Fixture(this, defaults)
        f.handle.fail(TrackingFailure.FOREGROUND)
        f.handle.close(); runCurrent()
        assertEquals(TrackingState.Failed(TrackingFailure.FOREGROUND), f.state)
        assertEquals(0, f.calls)
    }

    @Test fun stopBeforeSettingsArriveCannotBeResurrectedByTheLateRead() = runTest {
        val f = Fixture(this, defaults)
        val gate = CompletableDeferred<Unit>()
        f.settings.readGate = gate
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertTrue(f.state is TrackingState.Starting)
        f.handle.stop(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(0, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun successReturnedByAnUncooperativeCancelledSourceCannotBeWritten() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = {
            try { awaitCancellation() } catch (_: CancellationException) { f.fix() }
        }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.handle.stop(); runCurrent()
        assertEquals(0, f.writes); assertEquals(0, f.uploads)
        assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun settingsFailureWhileRecordingCancelsTheExistingSubscription() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        f.settings.fail(); runCurrent()
        assertEquals(0, f.active); assertEquals(1, f.cleanups)
        assertEquals(TrackingState.Failed(TrackingFailure.SETTINGS), f.state)
    }

}
