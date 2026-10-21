package ru.ama.whereme.tracking

import java.time.ZoneId
import java.time.Instant
import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.tracking.LocationRecorder
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import ru.ama.whereme.domain.tracking.TrackingClock
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.domain.tracking.RecordingSessionStore
import ru.ama.whereme.domain.tracking.TrackingAlarm

@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {
    @Provides @Singleton
    fun alarm(impl: AlarmClockTrackingAlarm): TrackingAlarm = impl

    @Provides @Singleton
    fun clock(): TrackingClock = object : TrackingClock {
        override fun now() = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        override fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()
        override fun zoneId(): ZoneId = ZoneId.systemDefault()
    }

    @Provides @Singleton
    fun coordinator(settings: SettingsRepository, source: LocationSource, recorder: LocationRecorder,
        uploader: PendingLocationUploader, clock: TrackingClock, sessions: RecordingSessionStore,
        alarm: TrackingAlarm, @ApplicationScope scope: CoroutineScope) =
        TrackingCoordinator(
            settings, source, recorder, uploader, clock,
            // Same application Job; only the control dispatcher changes. Never cancel it on service stop.
            scope + Dispatchers.Main.immediate,
            sessions, alarm
        )
}
