package ru.ama.whereme.domain.tracking

import ru.ama.whereme.domain.reportFailure
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.usecase.SchedulePolicy

/**
 * Owns recording state on a single control thread (Main on Android).
 * attach() and Handle calls belong to that same thread; restoration only enqueues commands and
 * may be called from any dispatcher. Platform adapters must keep blocking work off the control thread.
 */
class TrackingCoordinator(
    private val settings: SettingsRepository,
    source: LocationSource,
    recorder: LocationRecorder,
    private val uploader: PendingLocationUploader,
    private val clock: TrackingClock,
    private val scope: CoroutineScope,
    private val sessions: RecordingSessionStore,
    private val alarm: TrackingAlarm
) {
    private val measurement = TrackingMeasurement(source, recorder, clock)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<TrackingState>(TrackingState.Stopped)
    val state = mutableState.asStateFlow()
    private var current: Session? = null

    init {
        scope.launch {
            try {
                loadSavedState()
                for (command in commands) {
                    try {
                        accept(command)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        handleFailure(failure)
                        if (command is Command.Restore) command.done.completeExceptionally(failure)
                    }
                }
            } finally {
                current?.let {
                    it.handle.closed = true
                    it.job?.cancel()
                    it.syncJob?.cancel()
                    it.observer?.cancel()
                }
                commands.close()
            }
        }
    }

    fun attach(): Handle = Handle().also { send(Command.Attach(it)) }
    suspend fun restoreAfterSystemChange() = restore(RestoreReason.SYSTEM_CHANGE)
    suspend fun resumeEnabledSchedule() = restore(RestoreReason.USER_REQUEST)

    private suspend fun restore(reason: RestoreReason) {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Restore(reason, done))
        done.await()
    }

    /** A service connection; startId comes directly from Android, without another request counter. */
    inner class Handle internal constructor() {
        internal var closed = false
        private val mutableState = MutableStateFlow(TrackingServiceState(0, TrackingState.Stopped))
        val state = mutableState.asStateFlow()

        fun start(mode: TrackingMode, startId: Int = 0) = submit(Command.Start(this, startId, mode))
        fun wake(id: String, dueAtMillis: Long, startId: Int = 0) = submit(Command.Wake(this, startId, id, dueAtMillis))
        fun stop(startId: Int = 0) = submit(Command.Stop(this, startId, TrackingState.Stopped))
        fun block(reason: LocationOutcome.Reason, startId: Int = 0) =
            submit(Command.Stop(this, startId, TrackingState.LocationUnavailable(reason)))
        fun fail(reason: TrackingFailure, startId: Int = 0) =
            submit(Command.Stop(this, startId, TrackingState.Failed(reason)))

        fun close() {
            if (closed) return
            closed = true // Immediately invalidates a Start still waiting in the command queue.
            send(Command.Detach(this))
        }

        internal fun publish(startId: Int, state: TrackingState) {
            mutableState.value = TrackingServiceState(startId, state)
        }

        private fun submit(command: Command) {
            if (!closed) send(command)
        }
    }

    private class Session(val handle: Handle) {
        var startId = 0
        var recording: RecordingSession? = null
        val mode: TrackingMode? get() = recording?.mode
        var pendingDue: Long? = null
        var configuration: SettingsDomModel? = null
        var observer: Job? = null
        var job: Job? = null
        var syncJob: Job? = null
        var syncFailed = false
        var previousRowId: Long? = null
    }

    private enum class RestoreReason { SYSTEM_CHANGE, USER_REQUEST }
    private sealed interface Command {
        data class Attach(val handle: Handle) : Command
        data class Restore(val reason: RestoreReason, val done: CompletableDeferred<Unit>) : Command
        sealed interface Connected : Command { val handle: Handle }
        data class Detach(override val handle: Handle) : Connected
        data class Start(override val handle: Handle, val startId: Int, val mode: TrackingMode) : Connected
        data class Wake(override val handle: Handle, val startId: Int, val id: String, val due: Long) : Connected
        data class Stop(override val handle: Handle, val startId: Int, val terminal: TrackingState) : Connected
        data class Configuration(override val handle: Handle, val observer: Job, val value: Result<SettingsDomModel>) : Connected
        data class Progress(override val handle: Handle, val worker: Job, val value: TrackingState) : Connected
        data class Measured(override val handle: Handle, val worker: Job, val result: TrackingMeasurement.Result) : Connected
        data class Uploaded(override val handle: Handle, val worker: Job, val result: UploadResult) : Connected
    }

    private class AlarmFailure(cause: Exception) : Exception(cause)
    private fun send(command: Command) { check(commands.trySend(command).isSuccess) }
    private fun arm(recording: RecordingSession) {
        try {
            alarm.schedule(recording)
        } catch (failure: Exception) {
            throw AlarmFailure(failure)
        }
    }

    private suspend fun loadSavedState() {
        try {
            sessions.read()?.let { mutableState.value = TrackingState.Armed(it.mode, it.nextAtMillis) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = TrackingState.Failed(TrackingFailure.STORAGE)
        }
    }

    private suspend fun handleFailure(failure: Exception) {
        reportFailure("Tracking command", failure)
        val reason = if (failure is AlarmFailure) TrackingFailure.ALARM else TrackingFailure.STORAGE
        val session = current
        if (session == null) {
            mutableState.value = TrackingState.Failed(reason)
            return
        }
        cancelRun(session)
        session.recording = null
        try { sessions.write(null) } catch (cancelled: CancellationException) { throw cancelled } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        try { alarm.cancel() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        publish(session, TrackingState.Failed(reason))
    }

    private suspend fun accept(command: Command) {
        when (command) {
            is Command.Restore -> {
                restoreSession(command.reason)
                command.done.complete(Unit)
            }
            is Command.Attach -> attach(command.handle)
            is Command.Connected -> {
                val session = current ?: return
                if (command.handle !== session.handle) return
                if (command is Command.Detach) detach(session)
                else if (!session.handle.closed || command is Command.Stop) accept(session, command)
            }
        }
    }

    private suspend fun restoreSession(reason: RestoreReason) {
        val running = current?.takeIf { it.mode != null && !it.handle.closed }
        if (running != null) {
            if (reason == RestoreReason.SYSTEM_CHANGE) {
                cancelRun(running)
                beginRun(running)
            }
            return
        }
        val saved = sessions.read()
        if (saved == null && reason == RestoreReason.SYSTEM_CHANGE) {
            alarm.cancel()
            return
        }
        val configuration = settings.settings.first()
        val now = clock.nowMillis()
        val end = SchedulePolicy.activeUntil(configuration, now, clock.timeZone())
        val value = saved ?: end?.let {
            RecordingSession(UUID.randomUUID().toString(), TrackingMode.SCHEDULED, now + 1_000)
        }
        if (value == null || (value.mode == TrackingMode.SCHEDULED && end == null)) {
            sessions.write(null)
            alarm.cancel()
            if (current == null) mutableState.value = TrackingState.Stopped
            return
        }
        val deadline = if (value.nextAtMillis >= now) value.nextAtMillis else now + 1_000
        val next = if (value.mode == TrackingMode.SCHEDULED) minOf(deadline, requireNotNull(end)) else deadline
        val restored = value.copy(nextAtMillis = next)
        sessions.write(restored)
        arm(restored)
        if (current == null) mutableState.value = TrackingState.Armed(restored.mode, restored.nextAtMillis)
    }

    private suspend fun attach(handle: Handle) {
        current?.let {
            it.handle.closed = true
            cancelRun(it)
            it.observer?.cancelAndJoin()
        }
        val session = Session(handle)
        current = session
        publish(session, TrackingState.Stopped)
        if (!handle.closed) observeSettings(session)
    }

    private suspend fun detach(session: Session) {
        val wasRunning = state.value.isRunning
        cancelRun(session)
        session.observer?.cancelAndJoin()
        if (wasRunning) {
            val saved = session.recording
            mutableState.value = if (saved != null) TrackingState.Armed(saved.mode, saved.nextAtMillis)
                else TrackingState.Stopped
        }
        current = null
    }

    private suspend fun accept(session: Session, command: Command.Connected) {
        when (command) {
            is Command.Start -> start(session, command)
            is Command.Wake -> wake(session, command)
            is Command.Stop -> {
                session.startId = command.startId
                finish(session, command.terminal)
            }
            is Command.Configuration -> configure(session, command)
            is Command.Progress -> if (command.worker === session.job) publish(session, command.value)
            is Command.Measured -> if (command.worker === session.job) measured(session, command.result)
            is Command.Uploaded -> if (command.worker === session.syncJob) uploaded(session, command.result)
            is Command.Detach -> Unit
        }
    }

    private suspend fun start(session: Session, command: Command.Start) {
        session.startId = command.startId
        val existing = session.recording ?: sessions.read()
        val mode = if (existing?.mode == TrackingMode.MANUAL) TrackingMode.MANUAL else command.mode
        if (session.mode == mode) {
            publish(session, state.value)
            return
        }
        cancelRun(session)
        session.pendingDue = null
        session.previousRowId = null
        session.recording = RecordingSession(UUID.randomUUID().toString(), mode, clock.nowMillis())
        if (session.observer?.isActive != true) {
            session.configuration = null
            observeSettings(session)
        }
        beginRun(session)
    }

    private suspend fun wake(session: Session, command: Command.Wake) {
        session.startId = command.startId
        val saved = sessions.read()
        if (saved == null || saved.id != command.id || saved.nextAtMillis != command.due) {
            publish(session, state.value)
            return
        }
        if (clock.nowMillis() < command.due) {
            arm(saved)
            publish(session, state.value)
            return
        }
        session.recording = saved
        session.pendingDue = command.due
        if (session.job != null) {
            // A period boundary is not Stop. Let the bounded measurement return its best fix
            // and commit it; Measured will start the pending period without overlapping jobs.
            val configuration = session.configuration ?: return
            val end = if (saved.mode == TrackingMode.SCHEDULED)
                SchedulePolicy.activeUntil(configuration, clock.nowMillis(), clock.timeZone()) else null
            if (saved.mode == TrackingMode.MANUAL || end != null) {
                armNext(session, command.due, configuration, end)
            }
            publish(session, state.value)
            return
        }
        if (session.configuration == null || session.observer?.isActive != true) {
            session.configuration = null
            if (session.observer?.isActive != true) observeSettings(session)
            publish(session, TrackingState.Starting(saved.mode))
        } else {
            beginRun(session, command.due)
            session.pendingDue = null
        }
    }

    private suspend fun configure(session: Session, command: Command.Configuration) {
        if (command.observer !== session.observer) return
        val configuration = command.value.getOrNull()
        if (configuration == null) {
            finish(session, TrackingState.Failed(TrackingFailure.SETTINGS))
        } else if (configuration != session.configuration) {
            val previous = session.configuration
            session.configuration = configuration
            val measurementChanged = previous == null || previous.accuracy != configuration.accuracy ||
                previous.minDist != configuration.minDist || previous.timeOfWaitAccuracy != configuration.timeOfWaitAccuracy ||
                previous.timeOfWorkingWM != configuration.timeOfWorkingWM
            if (session.mode != null && (measurementChanged || session.mode == TrackingMode.SCHEDULED)) {
                cancelRun(session, cancelSync = false)
                beginRun(session, session.pendingDue ?: clock.nowMillis())
                session.pendingDue = null
            }
        }
    }

    private fun observeSettings(session: Session) {
        val handle = session.handle
        // Register the Job before it can publish a result, including on Main.immediate.
        session.observer = scope.launch(start = CoroutineStart.LAZY) {
            val observer = currentCoroutineContext().job
            try {
                var received = false
                settings.settings.collect {
                    received = true
                    send(Command.Configuration(handle, observer, Result.success(it)))
                }
                check(received) { "Settings stream completed without a value" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                send(Command.Configuration(handle, observer, Result.failure(failure)))
            }
        }
        session.observer?.start()
    }

    private suspend fun cancelRun(session: Session, cancelSync: Boolean = true) {
        val worker = session.job
        session.job = null
        if (worker != null || (cancelSync && session.syncJob != null)) publish(session, TrackingState.Stopping)
        worker?.cancelAndJoin()
        if (cancelSync) {
            val upload = session.syncJob
            session.syncJob = null
            upload?.cancelAndJoin()
            session.syncFailed = false
        }
    }

    private suspend fun finish(session: Session, terminal: TrackingState) {
        cancelRun(session)
        sessions.write(null) // An already delivered alarm must see Stop before alarm cancellation.
        alarm.cancel()
        session.recording = null
        session.pendingDue = null
        session.previousRowId = null
        publish(session, terminal)
    }

    private suspend fun beginRun(session: Session, due: Long = clock.nowMillis()) {
        if (session.handle.closed) return
        val mode = session.mode ?: return
        publish(session, TrackingState.Starting(mode))
        val configuration = session.configuration ?: return
        val now = clock.nowMillis()
        val end = if (mode == TrackingMode.SCHEDULED) SchedulePolicy.activeUntil(configuration, now, clock.timeZone()) else null
        if (mode == TrackingMode.SCHEDULED && end == null) {
            finish(session, TrackingState.Stopped)
            return
        }
        armNext(session, due, configuration, end)
        val handle = session.handle
        val request = TrackingMeasurement.Request(mode, configuration, end, session.previousRowId)
        session.job = scope.launch(start = CoroutineStart.LAZY) {
            val worker = currentCoroutineContext().job
            val result = measurement.run(request, ensureOpen = {
                currentCoroutineContext().ensureActive()
                if (handle.closed) throw CancellationException("Tracking service closed")
            }, progress = { send(Command.Progress(handle, worker, it)) })
            send(Command.Measured(handle, worker, result))
        }
        session.job?.start()
    }

    private suspend fun armNext(session: Session, due: Long, configuration: SettingsDomModel, end: Long?) {
        val period = configuration.timeOfWorkingWM.toLong() * 1000
        val next = due + ((clock.nowMillis() - due).coerceAtLeast(0) / period + 1) * period
        val recording = requireNotNull(session.recording).copy(nextAtMillis = minOf(next, end ?: Long.MAX_VALUE))
        sessions.write(recording)
        session.recording = recording
        arm(recording)
    }

    private suspend fun measured(session: Session, result: TrackingMeasurement.Result) {
        session.job?.join()
        session.job = null
        when (result) {
            is TrackingMeasurement.Result.Ended -> finish(session, result.state)
            is TrackingMeasurement.Result.Completed -> {
                if (result.rowId != null) session.previousRowId = result.rowId
                val mode = session.mode ?: return
                if (mode == TrackingMode.SCHEDULED && session.configuration?.let {
                        SchedulePolicy.activeUntil(it, clock.nowMillis(), clock.timeZone())
                    } == null) {
                    finish(session, TrackingState.Stopped)
                    return
                }
                val waiting = TrackingState.Waiting(mode, result.rowId == null, session.syncFailed, result.failure)
                if (result.failure != null || session.syncJob != null) publish(session, waiting)
                else startUpload(session, waiting)
                val pendingDue = session.pendingDue
                session.pendingDue = null
                if (pendingDue != null) beginRun(session, pendingDue)
            }
        }
    }

    private fun startUpload(session: Session, waiting: TrackingState.Waiting) {
        val handle = session.handle
        publish(session, TrackingState.Uploading(waiting.mode, waiting.noFix))
        session.syncJob = scope.launch(start = CoroutineStart.LAZY) {
            val worker = currentCoroutineContext().job
            val result = try {
                uploader.uploadPending()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportFailure("Upload locations", failure)
                UploadResult.FAILED
            }
            send(Command.Uploaded(handle, worker, result))
        }
        session.syncJob?.start()
    }

    private suspend fun uploaded(session: Session, result: UploadResult) {
        session.syncJob?.join()
        session.syncJob = null
        session.syncFailed = result == UploadResult.FAILED
        // Upload can span several measurements. Never replace a newer Collecting/Saving state.
        when (val visible = state.value) {
            is TrackingState.Uploading -> publish(session, TrackingState.Waiting(visible.mode, visible.noFix, session.syncFailed))
            is TrackingState.Waiting -> publish(session, visible.copy(syncFailed = session.syncFailed))
            else -> Unit
        }
    }

    private fun publish(session: Session, value: TrackingState) {
        mutableState.value = value
        session.handle.publish(session.startId, value)
    }
}
