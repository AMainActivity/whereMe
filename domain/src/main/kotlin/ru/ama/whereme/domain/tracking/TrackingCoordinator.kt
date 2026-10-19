package ru.ama.whereme.domain.tracking

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.usecase.SchedulePolicy

/** Serial commands own the recording intent. Only OS alarms start subsequent sampling cycles. */
class TrackingCoordinator(
    private val settings: SettingsRepository,
    private val source: LocationSource,
    private val recorder: LocationRecorder,
    private val uploader: PendingLocationUploader,
    private val clock: TrackingClock,
    private val scope: CoroutineScope,
    private val sessions: RecordingSessionStore,
    private val alarm: TrackingAlarm
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val owners = AtomicLong()
    private val mutableState = MutableStateFlow(TrackingSnapshot(null, 0, TrackingState.Stopped))
    val state = mutableState.asStateFlow()
    private var current: Session? = null

    init {
        scope.launch {
            try {
                // UI can be opened before the service is recreated by its next alarm.
                try {
                    sessions.read()?.let { saved ->
                        mutableState.value = TrackingSnapshot(null, 0, TrackingState.Armed(saved.mode, saved.nextAtMillis))
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) {
                    mutableState.value = TrackingSnapshot(null, 0, TrackingState.Failed(TrackingFailure.STORAGE))
                }
                for (command in commands) {
                    try { accept(command)
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: Exception) {
                        val reason = (failure as? OperationFailure)?.reason ?: TrackingFailure.STORAGE
                        val session = current
                        if (session != null) {
                            cancelRun(session)
                            session.mode = null
                            session.recording = null
                            // Keep the consumer alive and expose persistence/alarm failure instead of success.
                            try { sessions.write(null) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                            try { alarm.cancel() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                            publish(session, TrackingState.Failed(reason))
                        } else mutableState.value = TrackingSnapshot(null, 0, TrackingState.Failed(reason))
                        if (command is Command.Restore) command.done.completeExceptionally(failure)
                    }
                }
            } finally {
                current?.handle?.invalidate()
                current?.job?.cancel()
                current?.syncJob?.cancel()
                current?.observer?.cancel()
                commands.close()
            }
        }
    }

    fun attach(): Handle = Handle(owners.incrementAndGet()).also { send(Command.Attach(it)) }

    /** Recreate the OS alarm after boot/update/clock changes without depending on a visible screen. */
    suspend fun restoreAlarms(refreshRunning: Boolean = true, startScheduledIfActive: Boolean = false) {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Restore(done, refreshRunning, startScheduledIfActive))
        done.await()
    }

    inner class Handle internal constructor(val ownerId: Long) {
        private val lock = Any()
        private val closed = AtomicBoolean()
        private val requests = AtomicLong()
        val latestRequest: Long get() = requests.get()
        val isClosed: Boolean get() = closed.get()
        fun start(mode: TrackingMode): Long = request { Command.Start(this, it, mode) }
        fun wake(id: String, dueAtMillis: Long): Long = request { Command.Wake(this, it, id, dueAtMillis) }
        fun stop(): Long = request { Command.Stop(this, it, TrackingState.Stopped) }
        fun block(reason: LocationOutcome.Reason): Long = request { Command.Stop(this, it, TrackingState.LocationUnavailable(reason)) }
        fun fail(reason: TrackingFailure): Long = request { Command.Stop(this, it, TrackingState.Failed(reason)) }
        fun timeChanged() = synchronized(lock) { if (!isClosed) send(Command.TimeChanged(this)) }
        fun close() = synchronized(lock) {
            if (closed.compareAndSet(false, true)) send(Command.Detach(this))
        }
        internal fun invalidate() = synchronized(lock) { closed.set(true) }
        private fun request(command: (Long) -> Command): Long = synchronized(lock) {
            if (isClosed) return@synchronized requests.get()
            requests.incrementAndGet().also { send(command(it)) }
        }
    }

    private class Session(val handle: Handle) {
        var request = 0L
        var mode: TrackingMode? = null
        var recording: RecordingSession? = null
        var pendingDue: Long? = null
        var configuration: SettingsDomModel? = null
        var observer: Job? = null
        var observerId = 0L
        var job: Job? = null
        var syncJob: Job? = null
        var generation = 0L
        var previousRowId: Long? = null
    }

    private sealed interface Command {
        data class Attach(val handle: Handle) : Command
        data class Detach(val handle: Handle) : Command
        data class Start(val handle: Handle, val request: Long, val mode: TrackingMode) : Command
        data class Wake(val handle: Handle, val request: Long, val id: String, val due: Long) : Command
        data class Stop(val handle: Handle, val request: Long, val terminal: TrackingState) : Command
        data class TimeChanged(val handle: Handle) : Command
        data class Configuration(val handle: Handle, val observer: Long, val value: SettingsDomModel) : Command
        data class SettingsFailed(val handle: Handle, val observer: Long) : Command
        data class Progress(val handle: Handle, val generation: Long, val value: TrackingState) : Command
        data class Completed(val handle: Handle, val generation: Long, val noFix: Boolean, val failure: TrackingFailure?) : Command
        data class SyncEnded(val handle: Handle, val generation: Long, val noFix: Boolean, val failed: Boolean) : Command
        data class Ended(val handle: Handle, val generation: Long, val terminal: TrackingState) : Command
        data class Restore(val done: CompletableDeferred<Unit>, val refreshRunning: Boolean,
            val startScheduledIfActive: Boolean) : Command
    }

    private class OperationFailure(val reason: TrackingFailure, cause: Exception) : Exception(cause)
    private fun send(command: Command) { check(commands.trySend(command).isSuccess) }
    private fun arm(value: RecordingSession) {
        try { alarm.schedule(value) } catch (failure: Exception) { throw OperationFailure(TrackingFailure.ALARM, failure) }
    }
    private suspend fun accept(command: Command) {
        if (command is Command.Restore) {
            val running = current?.takeIf { it.mode != null && !it.handle.isClosed }
            if (running != null) {
                if (command.refreshRunning) { cancelRun(running); beginRun(running) }
            }
            else {
                val saved = sessions.read()
                // Only an explicit app-foreground event may start a missing scheduled session.
                // Boot/time changes and merely opening Settings restore existing intent only.
                val value = saved ?: if (command.startScheduledIfActive) {
                    val configuration = settings.settings.first()
                    val now = clock.nowMillis()
                    if (SchedulePolicy.activeUntil(configuration, now, clock.timeZone()) != null)
                        RecordingSession(UUID.randomUUID().toString(), TrackingMode.SCHEDULED, now + 1_000)
                    else null
                } else null
                if (value != null) {
                    val configuration = settings.settings.first()
                    val end = if (value.mode == TrackingMode.SCHEDULED)
                        SchedulePolicy.activeUntil(configuration, clock.nowMillis(), clock.timeZone()) else null
                    if (value.mode == TrackingMode.SCHEDULED && end == null) {
                        sessions.write(null); alarm.cancel()
                        if (current == null) mutableState.value = TrackingSnapshot(null, 0, TrackingState.Stopped)
                    } else {
                        val now = clock.nowMillis()
                        val deadline = if (value.nextAtMillis >= now) value.nextAtMillis else now + 1_000
                        val restored = value.copy(nextAtMillis = minOf(deadline, end ?: Long.MAX_VALUE))
                        sessions.write(restored); arm(restored)
                        if (current == null) mutableState.value = TrackingSnapshot(null, 0,
                            TrackingState.Armed(restored.mode, restored.nextAtMillis))
                    }
                } else alarm.cancel()
            }
            command.done.complete(Unit)
            return
        }
        if (command is Command.Attach) {
            current?.let { old ->
                old.handle.invalidate(); cancelRun(old); old.observer?.cancelAndJoin()
            }
            val session = Session(command.handle)
            current = session
            publish(session, TrackingState.Stopped)
            if (!session.handle.isClosed) observeSettings(session)
            return
        }
        val session = current ?: return
        if (command is Command.Detach) {
            if (command.handle !== session.handle) return
            val wasRunning = state.value.state.isRunning
            cancelRun(session)
            session.observer?.cancelAndJoin()
            // Service destruction is not a user Stop: the durable alarm must survive it.
            if (wasRunning) {
                val saved = session.recording
                if (saved != null && session.mode != null) {
                    mutableState.value = TrackingSnapshot(null, 0, TrackingState.Armed(saved.mode, saved.nextAtMillis))
                } else publish(session, TrackingState.Stopped)
            }
            current = null
            return
        }
        val handle = when (command) {
            is Command.Start -> command.handle
            is Command.Wake -> command.handle
            is Command.Stop -> command.handle
            is Command.TimeChanged -> command.handle
            is Command.Configuration -> command.handle
            is Command.SettingsFailed -> command.handle
            is Command.Progress -> command.handle
            is Command.Completed -> command.handle
            is Command.SyncEnded -> command.handle
            is Command.Ended -> command.handle
            else -> return
        }
        if (handle !== session.handle || (handle.isClosed && command !is Command.Stop)) return
        when (command) {
            is Command.Start -> {
                session.request = command.request
                val existing = session.recording ?: sessions.read()
                val mode = if (existing?.mode == TrackingMode.MANUAL && command.mode == TrackingMode.SCHEDULED)
                    TrackingMode.MANUAL else command.mode
                if (session.mode == mode) { publish(session, state.value.state); return }
                cancelRun(session)
                session.mode = mode
                session.pendingDue = null
                session.previousRowId = null
                session.recording = RecordingSession(UUID.randomUUID().toString(), mode, clock.nowMillis())
                if (session.observer?.isActive != true) {
                    session.configuration = null; observeSettings(session)
                }
                beginRun(session)
            }
            is Command.Wake -> {
                session.request = command.request
                val persisted = sessions.read()
                if (persisted == null || persisted.id != command.id || persisted.nextAtMillis != command.due) {
                    publish(session, state.value.state); return
                }
                if (clock.nowMillis() < command.due) { arm(persisted); publish(session, state.value.state); return }
                cancelRun(session, cancelSync = false)
                session.mode = persisted.mode
                session.recording = persisted
                session.pendingDue = command.due
                if (session.configuration == null || session.observer?.isActive != true) {
                    // Configuration will start the restored cycle after the actual DataStore read.
                    session.configuration = null
                    if (session.observer?.isActive != true) observeSettings(session)
                    publish(session, TrackingState.Starting(persisted.mode))
                } else { beginRun(session, command.due); session.pendingDue = null }
            }
            is Command.Stop -> {
                session.request = command.request
                finish(session, command.terminal)
            }
            is Command.Configuration -> {
                if (command.observer != session.observerId || command.value == session.configuration) return
                session.configuration = command.value
                if (session.mode != null) {
                    cancelRun(session); beginRun(session, session.pendingDue ?: clock.nowMillis())
                    session.pendingDue = null
                }
            }
            is Command.SettingsFailed -> if (command.observer == session.observerId) {
                finish(session, TrackingState.Failed(TrackingFailure.SETTINGS))
            }
            is Command.TimeChanged -> if (session.mode != null) { cancelRun(session); beginRun(session) }
            is Command.Progress -> if (command.generation == session.generation) publish(session, command.value)
            is Command.Completed -> if (command.generation == session.generation) {
                session.job?.join(); session.job = null
                val mode = session.mode ?: return
                if (command.failure != null) {
                    publish(session, TrackingState.Waiting(mode, command.noFix, false, command.failure))
                } else if (session.syncJob?.isActive != true) {
                    publish(session, TrackingState.Uploading(mode, command.noFix))
                    session.syncJob = scope.launch {
                        val failed = try { uploader.uploadPending(); false
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { true }
                        send(Command.SyncEnded(handle, command.generation, command.noFix, failed))
                    }
                } else publish(session, TrackingState.Waiting(mode, command.noFix, false))
            }
            is Command.SyncEnded -> if (command.generation == session.generation && session.mode != null) {
                publish(session, TrackingState.Waiting(requireNotNull(session.mode), command.noFix, command.failed))
            }
            is Command.Ended -> if (command.generation == session.generation) finish(session, command.terminal)
            else -> Unit
        }
    }

    private fun observeSettings(session: Session) {
        val observer = ++session.observerId
        session.observer = scope.launch {
            try {
                var received = false
                settings.settings.collect { value -> received = true; send(Command.Configuration(session.handle, observer, value)) }
                if (!received) send(Command.SettingsFailed(session.handle, observer))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { send(Command.SettingsFailed(session.handle, observer)) }
        }
    }

    private suspend fun cancelRun(session: Session, cancelSync: Boolean = true) {
        ++session.generation
        if (session.job != null || (cancelSync && session.syncJob?.isActive == true)) publish(session, TrackingState.Stopping)
        session.job?.cancelAndJoin(); session.job = null
        if (cancelSync) { session.syncJob?.cancelAndJoin(); session.syncJob = null }
    }

    private suspend fun finish(session: Session, terminal: TrackingState) {
        cancelRun(session)
        // Persist Stop before canceling, so an already delivered wakeup cannot revive the session.
        sessions.write(null)
        alarm.cancel()
        session.mode = null; session.recording = null; session.pendingDue = null; session.previousRowId = null
        publish(session, terminal)
    }

    private suspend fun beginRun(session: Session, due: Long = clock.nowMillis()) {
        if (session.handle.isClosed) return
        val mode = session.mode ?: return
        publish(session, TrackingState.Starting(mode))
        val configuration = session.configuration ?: return
        val now = clock.nowMillis()
        val end = if (mode == TrackingMode.SCHEDULED) SchedulePolicy.activeUntil(configuration, now, clock.timeZone()) else null
        if (mode == TrackingMode.SCHEDULED && end == null) { finish(session, TrackingState.Stopped); return }
        val period = configuration.timeOfWorkingWM.toLong() * 1000
        // Skip missed periods instead of replaying a burst after sleep or a clock change.
        val next = due + ((now - due).coerceAtLeast(0) / period + 1) * period
        val recording = requireNotNull(session.recording).copy(nextAtMillis = minOf(next, end ?: Long.MAX_VALUE))
        sessions.write(recording)
        session.recording = recording
        arm(recording) // The next measurement exists before location/Room/network can suspend.
        val generation = ++session.generation
        session.job = scope.launch {
            var stage = TrackingFailure.LOCATION
            try {
                suspend fun collectOne() {
                    ensureOpen(session)
                    val timeout = minOf(configuration.timeOfWaitAccuracy.toLong() * 1000, period,
                        (end?.minus(clock.nowMillis()) ?: Long.MAX_VALUE).coerceAtLeast(1))
                    send(Command.Progress(session.handle, generation,
                        TrackingState.Collecting(mode, clock.elapsedRealtimeNanos() / 1_000_000 + timeout, timeout)))
                    val result = source.locate(configuration.accuracy, timeout)
                    ensureOpen(session)
                    if (end != null && clock.nowMillis() >= end) {
                        send(Command.Ended(session.handle, generation, TrackingState.Stopped)); return
                    }
                    if (result is LocationOutcome.Unavailable && result.reason != LocationOutcome.Reason.NO_FIX) {
                        send(Command.Ended(session.handle, generation, TrackingState.LocationUnavailable(result.reason))); return
                    }
                    val sample = (result as? LocationOutcome.Fix)?.sample?.takeIf { it.isUsable(clock.elapsedRealtimeNanos()) }
                    if (sample != null) {
                        stage = TrackingFailure.STORAGE
                        send(Command.Progress(session.handle, generation, TrackingState.Saving(mode)))
                        ensureOpen(session)
                        session.previousRowId = recorder.record(sample, configuration, session.previousRowId)
                    }
                    ensureOpen(session)
                    send(Command.Completed(session.handle, generation, sample == null, null))
                }
                if (end == null) collectOne()
                else if (withTimeoutOrNull((end - clock.nowMillis()).coerceAtLeast(1)) { collectOne(); true } == null) {
                    send(Command.Ended(session.handle, generation, TrackingState.Stopped))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                val terminal = if (stage == TrackingFailure.LOCATION)
                    TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED) else TrackingState.Failed(stage)
                send(Command.Ended(session.handle, generation, terminal))
            } catch (_: Exception) {
                // A temporary provider/Room failure does not erase the already armed next measurement.
                send(Command.Completed(session.handle, generation, true, stage))
            }
        }
    }

    private suspend fun ensureOpen(session: Session) {
        currentCoroutineContext().ensureActive()
        if (session.handle.isClosed) throw CancellationException("Tracking owner closed")
    }
    private fun publish(session: Session, value: TrackingState) {
        mutableState.value = TrackingSnapshot(session.handle.ownerId, session.request, value)
    }
}
