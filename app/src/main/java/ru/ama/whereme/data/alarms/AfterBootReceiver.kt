package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

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
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Boot restores the user's enabled schedule; it must not enable it itself.
        runTrackingWork(scope) {
            repo.scheduleNextAlarmClockIfEnabled()
        }
    }
}
