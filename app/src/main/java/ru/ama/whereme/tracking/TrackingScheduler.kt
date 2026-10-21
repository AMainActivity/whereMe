package ru.ama.whereme.tracking

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.tracking.TrackingClock
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.domain.tracking.TrackingRequirement
import ru.ama.whereme.domain.usecase.SchedulePolicy
import ru.ama.whereme.presentation.SplashActivity

@Singleton
class TrackingScheduler @Inject constructor(
    private val application: Application,
    private val settings: SettingsRepository,
    private val coordinator: TrackingCoordinator,
    private val clock: TrackingClock
) {
    private val updates = Mutex()

    suspend fun getWorkingTime() = settings.settings.first()

    suspend fun isTimeToGetLocation(): Boolean {
        return SchedulePolicy.activeUntil(getWorkingTime(), clock.nowMillis(), clock.timeZone()) != null
    }

    suspend fun runAlarmClock() = updates.withLock {
        check(application.trackingCapabilities().requirement() == TrackingRequirement.READY)
        settings.updateSettings { current ->
            require(current.start != current.end && current.days.any { it == SettingsDomModel.DAY_ENABLED })
            current.copy(isEnable = true)
        }
        refreshSchedule()
    }

    /** Settings changes may enter the current interval as well as move the next daily alarm. */
    suspend fun refreshEnabledSchedule() = updates.withLock { refreshSchedule() }

    private suspend fun refreshSchedule() {
        applyLatestSchedule()
        if (application.trackingCapabilities().requirement() == TrackingRequirement.READY) {
            coordinator.resumeEnabledSchedule()
        }
    }

    suspend fun scheduleNextAlarmClockIfEnabled() = updates.withLock { applyLatestSchedule() }

    private suspend fun applyLatestSchedule() {
        val alarm = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent()
        // Keep the existing alarm if reading storage fails. setAlarmClock replaces the same identity.
        val value = getWorkingTime()
        val next = SchedulePolicy.nextStart(value, clock.nowMillis(), clock.timeZone())
        if (next == null) {
            alarm.cancel(pending)
        } else {
            val open = PendingIntent.getActivity(application, OPEN_APP_REQUEST_CODE, Intent(application, SplashActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(next, open), pending)
        }
    }

    suspend fun cancelAlarmClock() = updates.withLock {
        settings.updateSettings { it.copy(isEnable = false) }
        refreshSchedule()
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(application, SCHEDULE_REQUEST_CODE,
        Intent(application, AlarmClockStart::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val OPEN_APP_REQUEST_CODE = 0
        const val SCHEDULE_REQUEST_CODE = 0
    }
}
