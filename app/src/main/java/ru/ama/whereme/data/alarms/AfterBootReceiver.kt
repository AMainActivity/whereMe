package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.ama.whereme.tracking.TrackingScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.tracking.runTrackingWork

@AndroidEntryPoint
class AfterBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repo: TrackingScheduler
    @Inject lateinit var coordinator: ru.ama.whereme.domain.tracking.TrackingCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        // Boot restores the user's enabled schedule; it must not enable it itself.
        runTrackingWork(scope) {
            repo.scheduleNextAlarmClockIfEnabled()
            coordinator.restoreAfterSystemChange()
        }
    }
}
