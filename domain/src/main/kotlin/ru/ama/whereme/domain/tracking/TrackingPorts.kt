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
    suspend fun uploadPending()
}
