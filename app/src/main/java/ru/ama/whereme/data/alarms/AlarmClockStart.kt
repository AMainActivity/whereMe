package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import ru.ama.whereme.tracking.TrackingScheduler
import ru.ama.whereme.tracking.TrackingAction
import ru.ama.whereme.tracking.TrackingRequirement
import ru.ama.whereme.tracking.trackingCapabilities
import ru.ama.whereme.presentation.MyForegroundService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.tracking.runTrackingWork

@AndroidEntryPoint
class AlarmClockStart : BroadcastReceiver() {
    @Inject
    lateinit var repo: TrackingScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        runTrackingWork(scope) {
            if (!repo.getWorkingTime().isEnable) return@runTrackingWork
            // An excluded weekday must not terminate the daily schedule chain.
            repo.scheduleNextAlarmClockIfEnabled()
            if (!repo.isTimeToGetLocation()) return@runTrackingWork
            if (context.trackingCapabilities().requirement(TrackingAction.SCHEDULE) != TrackingRequirement.READY) {
                Log.w("AlarmClockStart", "Scheduled tracking prerequisites are unavailable")
                return@runTrackingWork
            }
            try {
                ContextCompat.startForegroundService(context, MyForegroundService.newIntent(context, scheduled = true))
            } catch (failure: IllegalStateException) {
                Log.w("AlarmClockStart", "Service start restricted: " + failure.javaClass.simpleName)
            } catch (_: SecurityException) {
                Log.w("AlarmClockStart", "Location access is unavailable")
            }
        }
    }
}
