package ru.ama.whereme.domain.tracking

import java.util.TimeZone
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.entity.SettingsDomModel

interface TrackingClock {
    fun nowMillis(): Long
    fun elapsedRealtimeNanos(): Long
    fun timeZone(): TimeZone
}

fun interface LocationRecorder {
    /** Must honour cancellation and complete only after the local transaction is committed. */
    suspend fun record(sample: LocationSample, settings: SettingsDomModel, previousRowId: Long?): Long
}

fun interface PendingLocationUploader {
    /** Network failures retain pending rows; cancellation must stop the current attempt. */
    suspend fun uploadPending(): UploadResult
}

/** SENT means the server accepted the snapshot; local ACK still checks session and row identity. */
enum class UploadResult { SKIPPED, SENT, FAILED }

/** Durable recording intent, independent of the service instance and authentication session. */
data class RecordingSession(val id: String, val mode: TrackingMode, val nextAtMillis: Long) {
    init { require(id.isNotBlank() && nextAtMillis > 0) }
}

interface RecordingSessionStore {
    suspend fun read(): RecordingSession?
    suspend fun write(session: RecordingSession?)
}

/** Exactly one pending sampling alarm; implementations must wake the device, not use a process timer. */
interface TrackingAlarm {
    fun schedule(session: RecordingSession)
    fun cancel()
}
