package ru.ama.whereme.tracking

import android.app.Application
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton
import ru.ama.whereme.domain.tracking.*
import ru.ama.whereme.presentation.MyForegroundService

@Singleton
class AndroidTrackingControl
@Inject
constructor(
    private val application: Application,
    private val coordinator: TrackingCoordinator,
    private val scheduler: TrackingScheduler,
) : TrackingControl {
    override val state
        get() = coordinator.state

    override fun capabilities() = application.trackingCapabilities()

    override suspend fun setSchedule(enabled: Boolean) {
        if (enabled) scheduler.runAlarmClock() else scheduler.cancelAlarmClock()
    }

    override suspend fun refreshSchedule() = scheduler.refreshEnabledSchedule()

    override fun start() {
        check(capabilities().requirement() == TrackingRequirement.READY)
        ContextCompat.startForegroundService(
            application,
            MyForegroundService.newIntent(application),
        )
    }

    override fun stop() {
        application.startService(MyForegroundService.stopIntent(application))
    }
}
