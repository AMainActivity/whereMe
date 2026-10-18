package ru.ama.whereme.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.ama.whereme.R
import ru.ama.whereme.domain.tracking.TrackingClock
import ru.ama.whereme.domain.tracking.TrackingState
import ru.ama.whereme.presentation.MyForegroundService
import ru.ama.whereme.presentation.SplashActivity

/** Notification timing is presentation only; recording deadlines belong to the coordinator. */
internal class TrackingNotification(private val context: Context, private val clock: TrackingClock) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val builder: NotificationCompat.Builder by lazy {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.service_notify_title), NotificationManager.IMPORTANCE_LOW))
        val open = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_location).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, context.getString(R.string.tracking_stop), PendingIntent.getService(context, 1,
                MyForegroundService.stopIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun initial(): Notification = builder.setContentText(context.getString(R.string.tracking_starting))
        .setProgress(0, 0, true).build()

    suspend fun render(state: TrackingState) {
        if (state is TrackingState.Collecting) {
            val maximum = ((state.timeoutMillis + 999) / 1000).toInt().coerceAtLeast(1)
            while (currentCoroutineContext().isActive) {
                val left = (state.deadlineElapsedMillis - clock.elapsedRealtimeNanos() / 1_000_000).coerceAtLeast(0)
                val seconds = (left + 999) / 1000
                val text = context.getString(R.string.tracking_collecting) + " " +
                    String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
                post(builder.setContentText(text).setProgress(maximum, seconds.toInt(), false).build())
                if (left == 0L) return
                delay(minOf(1000, left))
            }
        } else {
            val text = when (state) {
                is TrackingState.Starting -> R.string.tracking_starting
                is TrackingState.Saving -> R.string.tracking_saving
                is TrackingState.Uploading -> R.string.tracking_uploading
                TrackingState.Stopping -> R.string.tracking_stopping
                is TrackingState.Waiting -> when {
                    state.syncFailed -> R.string.tracking_sync_failed
                    state.noFix -> R.string.tracking_no_fix
                    else -> R.string.tracking_waiting
                }
                else -> return
            }
            post(builder.setContentText(context.getString(text)).setProgress(0, 0, state is TrackingState.Starting).build())
        }
    }

    private fun post(notification: Notification) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) manager.notify(ID, notification)
    }

    fun remove() { manager.cancel(ID) }

    companion object {
        const val ID = 1
        private const val CHANNEL_ID = "channel_id"
    }
}
