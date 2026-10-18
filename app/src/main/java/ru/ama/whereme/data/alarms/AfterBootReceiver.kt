package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.ama.whereme.tracking.TrackingScheduler
import javax.inject.Inject

@AndroidEntryPoint
class AfterBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repo: TrackingScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Boot restores the user's enabled schedule; it must not enable it itself.
        repo.scheduleNextAlarmClockIfEnabled()
    }
}
