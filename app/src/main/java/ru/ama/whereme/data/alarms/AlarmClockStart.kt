package ru.ama.whereme.data.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.domain.tracking.TrackingRequirement
import ru.ama.whereme.presentation.MyForegroundService
import ru.ama.whereme.tracking.TrackingScheduler
import ru.ama.whereme.tracking.runTrackingWork
import ru.ama.whereme.tracking.trackingCapabilities

@AndroidEntryPoint
class AlarmClockStart : BroadcastReceiver() {
    @Inject
    lateinit var repo: TrackingScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        runTrackingWork(scope) { startScheduledTracking(context) }
    }

    internal suspend fun startScheduledTracking(context: Context) {
        if (!repo.getWorkingTime().isEnable) return
        try {
            if (!repo.isTimeToGetLocation()) return
            if (context.trackingCapabilities().requirement() != TrackingRequirement.READY) {
                Log.w(TAG, "Scheduled tracking prerequisites are unavailable")
                return
            }
            try {
                ContextCompat.startForegroundService(context, MyForegroundService.newIntent(context, scheduled = true))
            } catch (failure: IllegalStateException) {
                Log.w(TAG, "Service start restricted: " + failure.javaClass.simpleName)
            } catch (_: SecurityException) {
                Log.w(TAG, "Location access is unavailable")
            }
        } finally {
            // Start today's service before rearming. Excluded days and failed starts must
            // still leave the next selected day armed.
            repo.scheduleNextAlarmClockIfEnabled()
        }
    }
    private companion object {
        const val TAG = "AlarmClockStart"
    }
}
