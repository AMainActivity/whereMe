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
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.ama.whereme.R
import ru.ama.whereme.domain.state.LoadState
import ru.ama.whereme.domain.tracking.TrackingFailure
import ru.ama.whereme.domain.tracking.TrackingState
import ru.ama.whereme.presentation.MyForegroundService
import ru.ama.whereme.presentation.SplashActivity

/** Displays persisted fix time; acquisition and upload progress do not change it. */
internal class TrackingNotification(private val context: Context) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val builder: NotificationCompat.Builder by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.service_notify_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val open =
            Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    OPEN_APP_REQUEST_CODE,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                0,
                context.getString(R.string.tracking_stop),
                PendingIntent.getService(
                    context,
                    STOP_REQUEST_CODE,
                    MyForegroundService.stopIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
    }

    fun initial(): Notification =
        builder
            .setContentText(context.getString(R.string.tracking_recording))
            .setShowWhen(false)
            .build()

    fun render(state: TrackingState, lastRecorded: LoadState<Long?>) {
        if (!state.isRunning) return
        post(build(state, lastRecorded))
    }

    private fun build(state: TrackingState, lastRecorded: LoadState<Long?>): Notification {
        val text =
            when (lastRecorded) {
                LoadState.Loading -> context.getString(R.string.tracking_recording)
                LoadState.Failed -> context.getString(R.string.tracking_history_failed)
                is LoadState.Ready ->
                    lastRecorded.value?.let { time ->
                        val locale = Locale.getDefault()
                        val skeleton =
                            if (DateFormat.is24HourFormat(context)) DATE_TIME_24_HOUR
                            else DATE_TIME_12_HOUR
                        val formatter =
                            SimpleDateFormat(
                                DateFormat.getBestDateTimePattern(locale, skeleton),
                                locale,
                            )
                        context.getString(R.string.tracking_last_fix, formatter.format(Date(time)))
                    } ?: context.getString(R.string.tracking_no_recorded_fix)
            }
        val problem =
            when {
                state == TrackingState.Stopping -> R.string.tracking_stopping
                state is TrackingState.Waiting && state.failure == TrackingFailure.STORAGE ->
                    R.string.tracking_storage_failed
                state is TrackingState.Waiting && (state.failure != null || state.noFix) ->
                    R.string.tracking_fix_failed
                state is TrackingState.Uploading && state.noFix -> R.string.tracking_fix_failed
                else -> null
            }?.let(context::getString)
        val content = if (problem == null) text else "$text\n$problem"
        return builder
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setShowWhen(false)
            .build()
    }

    private fun post(notification: Notification) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
            manager.notify(ID, notification)
    }

    fun remove() {
        manager.cancel(ID)
    }

    companion object {
        const val ID = 1
        private const val CHANNEL_ID = "channel_id"
        private const val OPEN_APP_REQUEST_CODE = 0
        private const val STOP_REQUEST_CODE = 1
        private const val DATE_TIME_24_HOUR = "yMdHms"
        private const val DATE_TIME_12_HOUR = "yMdhms"
    }
}
