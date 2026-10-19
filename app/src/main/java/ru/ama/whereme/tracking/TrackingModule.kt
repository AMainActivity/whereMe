package ru.ama.whereme.tracking

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.TimeZone
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
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
        override fun nowMillis() = System.currentTimeMillis()
        override fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()
        override fun timeZone(): TimeZone = TimeZone.getDefault()
    }

    @Provides @Singleton
    fun coordinator(settings: SettingsRepository, source: LocationSource, recorder: LocationRecorder,
        uploader: PendingLocationUploader, clock: TrackingClock, sessions: RecordingSessionStore,
        alarm: TrackingAlarm, @ApplicationScope scope: CoroutineScope) =
        TrackingCoordinator(settings, source, recorder, uploader, clock, scope, sessions, alarm)
}
