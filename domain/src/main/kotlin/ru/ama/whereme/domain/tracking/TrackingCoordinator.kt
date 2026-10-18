package ru.ama.whereme.domain.tracking

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.usecase.SchedulePolicy

/** One command consumer owns state. Workers report generation-tagged progress, never write state. */
class TrackingCoordinator(
    private val settings: SettingsRepository,
    private val source: LocationSource,
    private val recorder: LocationRecorder,
    private val uploader: PendingLocationUploader,
    private val clock: TrackingClock,
    private val scope: CoroutineScope
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val owners = AtomicLong()
    private val mutableState = MutableStateFlow(TrackingSnapshot(null, 0, TrackingState.Stopped))
    val state = mutableState.asStateFlow()
    private var current: Session? = null // Accessed only by the command consumer.

    init {
        scope.launch {
            try { for (command in commands) accept(command) }
            finally {
                current?.handle?.invalidate()
                current?.job?.cancel()
                current?.observer?.cancel()
                commands.close()
            }
        }
    }

    /** A handle belongs to one service instance. Closing it immediately rejects further writes/commands. */
    fun attach(): Handle = Handle(owners.incrementAndGet()).also { send(Command.Attach(it)) }

    inner class Handle internal constructor(val ownerId: Long) {
        private val lock = Any()
        private val closed = AtomicBoolean()
        private val requests = AtomicLong()
        val latestRequest: Long get() = requests.get()
        val isClosed: Boolean get() = closed.get()

        fun start(mode: TrackingMode): Long = request { Command.Start(this, it, mode) }
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
        var configuration: SettingsDomModel? = null
        var observer: Job? = null
        var observerId = 0L
        var job: Job? = null
        var generation = 0L
        // A worker is the only writer; a replacement worker starts only after cancelAndJoin.
        var previousRowId: Long? = null
    }

    private sealed interface Command {
        data class Attach(val handle: Handle) : Command
        data class Detach(val handle: Handle) : Command
        data class Start(val handle: Handle, val request: Long, val mode: TrackingMode) : Command
        data class Stop(val handle: Handle, val request: Long, val terminal: TrackingState) : Command
        data class TimeChanged(val handle: Handle) : Command
        data class Configuration(val handle: Handle, val observer: Long, val value: SettingsDomModel) : Command
        data class SettingsFailed(val handle: Handle, val observer: Long) : Command
        data class Progress(val handle: Handle, val generation: Long, val value: TrackingState) : Command
        data class Ended(val handle: Handle, val generation: Long, val terminal: TrackingState) : Command
    }

    private fun send(command: Command) { check(commands.trySend(command).isSuccess) }

    private suspend fun accept(command: Command) {
        if (command is Command.Attach) {
            current?.let { old ->
                old.handle.invalidate()
                cancelRun(old)
                old.observer?.cancelAndJoin()
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
            if (wasRunning) publish(session, TrackingState.Stopped)
            current = null
            return
        }
        val handle = when (command) {
            is Command.Start -> command.handle
            is Command.Stop -> command.handle
            is Command.TimeChanged -> command.handle
            is Command.Configuration -> command.handle
            is Command.SettingsFailed -> command.handle
            is Command.Progress -> command.handle
            is Command.Ended -> command.handle
            else -> return
        }
        if (handle !== session.handle || (handle.isClosed && command !is Command.Stop)) return
        when (command) {
            is Command.Start -> {
                session.request = command.request
                val manualRunning = session.mode == TrackingMode.MANUAL &&
                    (session.job?.isActive == true || session.configuration == null)
                val mode = if (manualRunning) TrackingMode.MANUAL else command.mode
                if (session.mode == mode && (session.job?.isActive == true || session.configuration == null)) {
                    publish(session, state.value.state) // Acknowledge this duplicate Android start request.
                    return
                }
                val freshSession = session.mode == null
                cancelRun(session)
                session.mode = mode
                if (freshSession) session.previousRowId = null
                if (session.observer?.isActive != true) {
                    session.configuration = null
                    observeSettings(session)
                }
                beginRun(session)
            }
            is Command.Stop -> {
                session.request = command.request
                cancelRun(session)
                session.mode = null
                session.previousRowId = null
                publish(session, command.terminal)
            }
            is Command.Configuration -> {
                if (command.observer != session.observerId || command.value == session.configuration) return
                session.configuration = command.value
                if (session.mode != null) { cancelRun(session); beginRun(session) }
            }
            is Command.SettingsFailed -> {
                if (command.observer != session.observerId) return
                cancelRun(session)
                session.mode = null
                publish(session, TrackingState.Failed(TrackingFailure.SETTINGS))
            }
            is Command.TimeChanged -> if (session.mode != null) { cancelRun(session); beginRun(session) }
            is Command.Progress -> if (command.generation == session.generation) publish(session, command.value)
            is Command.Ended -> if (command.generation == session.generation) {
                session.job?.join()
                session.job = null
                session.mode = null
                publish(session, command.terminal)
            }
            else -> Unit
        }
    }

    private fun observeSettings(session: Session) {
        val observer = ++session.observerId
        session.observer = scope.launch {
            try {
                var received = false
                settings.settings.collect { value ->
                    received = true
                    send(Command.Configuration(session.handle, observer, value))
                }
                if (!received) send(Command.SettingsFailed(session.handle, observer))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { send(Command.SettingsFailed(session.handle, observer)) }
        }
    }

    private suspend fun cancelRun(session: Session) {
        ++session.generation // Reject already queued events before waiting for resource cleanup.
        if (session.job != null) {
            publish(session, TrackingState.Stopping)
            session.job?.cancelAndJoin()
            session.job = null
        }
    }

    private fun beginRun(session: Session) {
        if (session.handle.isClosed) return
        val mode = session.mode ?: return
        publish(session, TrackingState.Starting(mode))
        val configuration = session.configuration ?: return
        val generation = ++session.generation
        session.job = scope.launch {
            var stage = TrackingFailure.LOCATION
            fun progress(value: TrackingState) { send(Command.Progress(session.handle, generation, value)) }
            try {
                val end = if (mode == TrackingMode.SCHEDULED)
                    SchedulePolicy.activeUntil(configuration, clock.nowMillis(), clock.timeZone()) else null
                if (mode == TrackingMode.SCHEDULED && end == null) {
                    send(Command.Ended(session.handle, generation, TrackingState.Stopped))
                    return@launch
                }
                suspend fun loop(): TrackingState {
                    while (true) {
                        ensureOpen(session)
                        val remaining = end?.minus(clock.nowMillis())
                        if (remaining != null && remaining <= 0) return TrackingState.Stopped
                        val timeout = minOf(configuration.timeOfWaitAccuracy.toLong() * 1000, remaining ?: Long.MAX_VALUE)
                        stage = TrackingFailure.LOCATION
                        progress(TrackingState.Collecting(mode, clock.elapsedRealtimeNanos() / 1_000_000 + timeout, timeout))
                        val result = source.locate(configuration.accuracy, timeout)
                        ensureOpen(session)
                        if (end != null && clock.nowMillis() >= end) return TrackingState.Stopped
                        if (result is LocationOutcome.Unavailable && result.reason != LocationOutcome.Reason.NO_FIX) {
                            return TrackingState.LocationUnavailable(result.reason)
                        }
                        val sample = (result as? LocationOutcome.Fix)?.sample
                            ?.takeIf { it.isUsable(clock.elapsedRealtimeNanos()) }
                        if (sample != null) {
                            stage = TrackingFailure.STORAGE
                            progress(TrackingState.Saving(mode))
                            ensureOpen(session)
                            session.previousRowId = recorder.record(sample, configuration, session.previousRowId)
                        }
                        ensureOpen(session)
                        progress(TrackingState.Uploading(mode, noFix = sample == null))
                        val syncFailed = try { uploader.uploadPending(); false
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { true }
                        ensureOpen(session)
                        progress(TrackingState.Waiting(mode, noFix = sample == null, syncFailed = syncFailed))
                        delay(configuration.timeOfWorkingWM.toLong() * 1000)
                    }
                }
                val terminal = if (end == null) loop()
                    else withTimeoutOrNull((end - clock.nowMillis()).coerceAtLeast(1)) { loop() } ?: TrackingState.Stopped
                send(Command.Ended(session.handle, generation, terminal))
            } catch (cancelled: CancellationException) {
                if (currentCoroutineContext().isActive && !session.handle.isClosed) {
                    send(Command.Ended(session.handle, generation, TrackingState.Stopped))
                }
                throw cancelled
            } catch (_: SecurityException) {
                val terminal = if (stage == TrackingFailure.LOCATION)
                    TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED) else TrackingState.Failed(stage)
                send(Command.Ended(session.handle, generation, terminal))
            } catch (_: Exception) {
                send(Command.Ended(session.handle, generation, TrackingState.Failed(stage)))
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
