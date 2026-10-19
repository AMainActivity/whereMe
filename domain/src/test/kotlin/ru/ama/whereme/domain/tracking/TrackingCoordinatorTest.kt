package ru.ama.whereme.domain.tracking

import java.io.IOException
import java.time.Instant
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    private class MemorySessions : RecordingSessionStore {
        var value: RecordingSession? = null
        var failWrites = false
        override suspend fun read() = value
        override suspend fun write(session: RecordingSession?) {
            if (failWrites) throw IOException("synthetic state write failure")
            value = session
        }
    }

    private class Fixture(scope: TestScope, initial: SettingsDomModel, epoch: String = "2026-10-19T12:00:00Z",
        val sessions: MemorySessions = MemorySessions()) {
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
        var autoDeliver = true
        var alarmFailure = false
        var pending: RecordingSession? = null
        var alarmCount = 0
        private var alarmJob: Job? = null
        private val alarm = object : TrackingAlarm {
            override fun schedule(session: RecordingSession) {
                if (alarmFailure) throw SecurityException("synthetic revoked alarm access")
                pending = session; alarmCount++
                alarmJob?.cancel()
                if (autoDeliver) alarmJob = scope.backgroundScope.launch {
                    delay((session.nextAtMillis - clock.nowMillis()).coerceAtLeast(0))
                    handle.wake(session.id, session.nextAtMillis)
                }
            }
            override fun cancel() { pending = null; alarmJob?.cancel() }
        }
        val coordinator: TrackingCoordinator = TrackingCoordinator(settings, source, recorder, uploader, clock, scope.backgroundScope, sessions, alarm)
        fun recreateWithoutService(scope: TestScope) =
            TrackingCoordinator(settings, source, recorder, uploader, clock, scope.backgroundScope, sessions, alarm)
        val handle: TrackingCoordinator.Handle = coordinator.attach()
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

    @Test fun temporaryLocationFailureKeepsTheNextSamplingAlarm() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { throw IOException("synthetic source failure") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingFailure.LOCATION, (f.state as TrackingState.Waiting).failure)
        advanceTimeBy(15_000); runCurrent(); assertEquals(2, f.calls)
    }

    @Test fun failedLocalWriteDoesNotReportSuccessOrAttemptUpload() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }; f.recordHandler = { throw IOException("synthetic write failure") }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(TrackingFailure.STORAGE, (f.state as TrackingState.Waiting).failure)
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

    @Test fun sampleOlderThanThirtySecondsIsPersisted() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { val fix = f.fix().sample; LocationOutcome.Fix(fix.copy(elapsedRealtimeNanos = fix.elapsedRealtimeNanos - 31_000_000_000L)) }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(1, f.writes); assertFalse((f.state as TrackingState.Waiting).noFix)
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


    @Test fun elapsedTimeAloneCannotStartAnotherMeasurement() = runTest {
        val f = Fixture(this, defaults)
        f.autoDeliver = false
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, f.calls)
        assertNotNull(f.pending)
    }

    @Test fun nextAlarmIsArmedBeforeLocationAndDoesNotWaitForHttp() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { assertNotNull(f.pending); f.fix() }
        f.uploadHandler = { awaitCancellation() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(1, f.uploads)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, f.calls); assertEquals(2, f.writes)
        assertEquals(1, f.uploads) // One network attempt can span several sampling periods.
    }

    @Test fun stopCancelsTheAlarmAndRejectsAnAlreadyDeliveredWakeup() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val old = requireNotNull(f.pending)
        f.handle.stop(); runCurrent()
        assertNull(f.pending); assertNull(f.sessions.value)
        advanceTimeBy(15_000)
        f.handle.wake(old.id, old.nextAtMillis); runCurrent()
        assertEquals(1, f.calls); assertEquals(TrackingState.Stopped, f.state)
    }

    @Test fun anOldAlarmCannotReplaceANewManualSession() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val old = requireNotNull(f.pending)
        f.handle.stop(); f.handle.start(TrackingMode.MANUAL); runCurrent()
        val fresh = requireNotNull(f.pending)
        assertNotEquals(old.id, fresh.id)
        f.handle.wake(old.id, old.nextAtMillis); runCurrent()
        assertEquals(fresh, f.pending); assertEquals(2, f.calls)
    }

    @Test fun delayedDeliverySkipsMissedPeriodsWithoutBursting() = runTest {
        val f = Fixture(this, defaults)
        f.autoDeliver = false; f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val alarm = requireNotNull(f.pending)
        advanceTimeBy(61_000)
        f.handle.wake(alarm.id, alarm.nextAtMillis); runCurrent()
        assertEquals(2, f.calls)
        assertEquals(f.clock.nowMillis() + 14_000, requireNotNull(f.pending).nextAtMillis)
        f.handle.wake(alarm.id, alarm.nextAtMillis); runCurrent()
        assertEquals(2, f.calls)
    }

    @Test fun earlyOrUpdatedPendingIntentCannotCollectBeforeItsDeadline() = runTest {
        val f = Fixture(this, defaults)
        f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val pending = requireNotNull(f.pending)
        f.handle.wake(pending.id, pending.nextAtMillis); runCurrent()
        assertEquals(1, f.calls); assertEquals(pending, f.pending)
    }

    @Test fun destroyedServiceLeavesTheAlarmForANewProcessOwner() = runTest {
        val durable = MemorySessions()
        val first = Fixture(this, defaults, sessions = durable)
        first.autoDeliver = false; first.sourceHandler = { first.fix() }
        first.handle.start(TrackingMode.MANUAL); runCurrent()
        val pending = requireNotNull(first.pending)
        first.handle.close(); runCurrent()
        assertEquals(pending, durable.value)
        assertEquals(TrackingState.Armed(pending.mode, pending.nextAtMillis), first.state)
        val replacement = Fixture(this, defaults, sessions = durable)
        replacement.sourceHandler = { replacement.fix() }
        advanceTimeBy(15_000)
        replacement.handle.wake(pending.id, pending.nextAtMillis); runCurrent()
        assertEquals(1, replacement.calls)
        assertEquals(pending.id, requireNotNull(replacement.pending).id)
        assertEquals(TrackingMode.MANUAL, requireNotNull(durable.value).mode)
    }

    @Test fun revokedAlarmAccessFailsVisiblyBeforeAnyLocationRequest() = runTest {
        val f = Fixture(this, defaults)
        f.alarmFailure = true
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(0, f.calls)
        assertEquals(TrackingState.Failed(TrackingFailure.ALARM), f.state)
    }

    @Test fun recordingStateWriteFailureDoesNotStartAnUnrecoverableCycle() = runTest {
        val f = Fixture(this, defaults)
        f.sessions.failWrites = true
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        assertEquals(0, f.calls); assertEquals(0, f.alarmCount)
        assertEquals(TrackingState.Failed(TrackingFailure.STORAGE), f.state)
    }

    @Test fun periodicWakeWaitsForCancelledLocationCleanup() = runTest {
        val f = Fixture(this, defaults)
        f.sourceCleanup = { withContext(NonCancellable) { delay(100) } }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        advanceTimeBy(15_000); runCurrent()
        assertEquals(1, f.calls)
        advanceTimeBy(100); runCurrent()
        assertEquals(2, f.calls); assertEquals(1, f.maximumActive)
    }

    @Test fun restorationRearmsManualStateButDoesNotStartLocationInTheReceiver() = runTest {
        val f = Fixture(this, defaults)
        f.sessions.value = RecordingSession("restored", TrackingMode.MANUAL, f.clock.nowMillis() - 1_000)
        f.coordinator.restoreAlarms()
        assertEquals(0, f.calls)
        assertEquals(f.clock.nowMillis() + 1_000, requireNotNull(f.pending).nextAtMillis)
    }

    @Test fun restorationDropsAnExpiredScheduledSession() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "09:00", end = "10:00"))
        f.sessions.value = RecordingSession("expired", TrackingMode.SCHEDULED, f.clock.nowMillis() - 1_000)
        f.coordinator.restoreAlarms()
        assertNull(f.pending); assertNull(f.sessions.value); assertEquals(0, f.calls)
    }

    @Test fun aColdProcessKeepsTheCadenceAfterALateAlarmAndSlowSettingsRead() = runTest {
        val f = Fixture(this, defaults)
        f.autoDeliver = false
        val gate = CompletableDeferred<Unit>()
        f.settings.readGate = gate
        val due = f.clock.nowMillis() + 15_000
        f.sessions.value = RecordingSession("cold", TrackingMode.MANUAL, due)
        advanceTimeBy(61_000)
        f.handle.wake("cold", due); runCurrent()
        assertEquals(0, f.calls)
        gate.complete(Unit); runCurrent()
        assertEquals(1, f.calls)
        assertEquals(f.clock.nowMillis() + 14_000, requireNotNull(f.pending).nextAtMillis)
    }

    @Test fun coldSettingsUiShowsTheDurableRecordingBeforeAnyServiceStarts() = runTest {
        val f = Fixture(this, defaults)
        f.autoDeliver = false; f.sourceHandler = { f.fix() }
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val pending = requireNotNull(f.pending)
        f.handle.close(); runCurrent()
        val reopened = f.recreateWithoutService(this); runCurrent()
        assertEquals(TrackingState.Armed(TrackingMode.MANUAL, pending.nextAtMillis), reopened.state.value.state)
        assertEquals(1, f.calls)
    }

    @Test fun openingSettingsDoesNotRestartAnAlreadyRunningMeasurement() = runTest {
        val f = Fixture(this, defaults)
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val pending = f.pending
        f.coordinator.restoreAlarms(refreshRunning = false)
        assertEquals(1, f.calls); assertEquals(0, f.cleanups); assertEquals(pending, f.pending)
    }

    @Test fun openingTheAppStartsAMissingSessionInsideTheEnabledSchedule() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "12:00", end = "13:00"))
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        assertEquals(TrackingMode.SCHEDULED, requireNotNull(f.sessions.value).mode)
        assertNotNull(f.pending)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, f.calls)
        assertEquals(TrackingMode.SCHEDULED, (f.state as TrackingState.Collecting).mode)
    }

    @Test fun appEntryDoesNotEnableDisabledTrackingOrStartOutsideTheInterval() = runTest {
        for (settings in listOf(defaults,
            defaults.copy(isEnable = true, start = "13:00", end = "14:00"),
            defaults.copy(isEnable = true, start = "11:00", end = "12:00"),
            defaults.copy(isEnable = true, days = List(7) { "0" }))) {
            val f = Fixture(this, settings)
            f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
            assertNull(f.sessions.value); assertNull(f.pending); assertEquals(0, f.calls)
            assertEquals(settings, f.settings.values.value.getOrThrow())
        }
    }

    @Test fun appEntryHonoursTheStartingWeekdayOfAnOvernightSchedule() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true, start = "22:00", end = "02:00",
            days = listOf("1", "0", "0", "0", "0", "0", "0")), "2026-10-20T00:30:00Z")
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, f.calls)
        assertEquals(TrackingMode.SCHEDULED, (f.state as TrackingState.Collecting).mode)
    }

    @Test fun appEntryDoesNotRestartOrDowngradeAnActiveManualSession() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.MANUAL); runCurrent()
        val pending = f.pending
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        assertEquals(1, f.calls); assertEquals(0, f.cleanups)
        assertEquals(pending, f.pending)
        assertEquals(TrackingMode.MANUAL, requireNotNull(f.sessions.value).mode)
    }

    @Test fun duplicateAppEntryDoesNotReplaceThePendingSessionOrShiftItsDeadline() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.autoDeliver = false
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        val pending = f.pending
        advanceTimeBy(500)
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        assertEquals(pending, f.pending); assertEquals(0, f.calls)
    }

    @Test fun stopAfterAppEntryPreventsThePreparedAutomaticStart() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        f.handle.stop(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertNull(f.sessions.value); assertNull(f.pending); assertEquals(0, f.calls)
    }

    @Test fun existingStoppedScheduleCanResumeOnTheNextExplicitAppEntry() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.handle.start(TrackingMode.SCHEDULED); runCurrent()
        val previous = requireNotNull(f.sessions.value).id
        f.handle.stop(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, f.calls) // Stop alone never resumes recording.
        f.coordinator.restoreAlarms(refreshRunning = false, startScheduledIfActive = true)
        assertNotEquals(previous, requireNotNull(f.sessions.value).id)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, f.calls)
    }

    @Test fun bootAndSettingsRestoreDoNotCreateAMissingScheduledSession() = runTest {
        val f = Fixture(this, defaults.copy(isEnable = true))
        f.coordinator.restoreAlarms()
        f.coordinator.restoreAlarms(refreshRunning = false)
        assertNull(f.pending); assertNull(f.sessions.value); assertEquals(0, f.calls)
    }
}
