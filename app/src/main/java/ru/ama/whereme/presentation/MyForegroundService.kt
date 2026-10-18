package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.data.sync.LocationBatchUploader
import ru.ama.whereme.data.location.LocationRecorder
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.tracking.TrackingScheduler
import ru.ama.whereme.domain.entity.SettingsDomModel
import javax.inject.Inject

@AndroidEntryPoint
class MyForegroundService : LifecycleService() {

    private var timer: CountDownTimer? = null
    private lateinit var workingTimeModel: SettingsDomModel
    private var cycleJob: Job? = null
    private var lastRecordedRowId: Long? = null
    private var destroyed = false
    private var foregroundStarted = false
    var isTracking = false
        private set
    var isServiseAlive: ((Boolean) -> Unit)? = null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Inject
    lateinit var repo: LocationRecorder

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var scheduler: TrackingScheduler

    @Inject
    lateinit var uploader: LocationBatchUploader

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()
    }

    inner class LocalBinder : Binder() {
        fun getService() = this@MyForegroundService
    }

    private val notificationBuilder by lazy {
        createNotificationBuilder()
    }

    fun startGetLocations() {
        if (destroyed || !foregroundStarted || cycleJob?.isActive == true) return
        cycleJob = lifecycleScope.launch {
            workingTimeModel = settings.getWorkingTime()
            try {
                startTimer()
                val sample = repo.collectLocation(workingTimeModel)
                timer?.cancel()
                currentCoroutineContext().ensureActive()
                if (sample != null) {
                    lastRecordedRowId = repo.saveLocation(sample, sample.time, workingTimeModel, lastRecordedRowId)
                }
                currentCoroutineContext().ensureActive()
                cancelTimer(
                    getString(R.string.app_name),
                    getString(if (sample != null) R.string.service_timer_success else R.string.service_timer_povtor) +
                        repo.getDate(System.currentTimeMillis())
                )
                uploader.uploadPending()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                // Permissions can be revoked while collection is in progress.
                stopTracking()
            } catch (failure: Exception) {
                Log.w("TrackingCycle", "Cycle failed: " + failure.javaClass.simpleName)
            } finally {
                timer?.cancel()
                if (currentCoroutineContext().isActive && !destroyed && isTracking) reRunGetLocations()
            }
        }
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val resultIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val resultPendingIntent: PendingIntent? = PendingIntent.getActivity(
            this, 0, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notify_title))
            .setContentText(getString(R.string.service_notify_text))
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(resultPendingIntent)
        return b
    }

    private fun getFormattedLeftTime(millisUntilFinished: Long): String {
        val seconds = (millisUntilFinished / MILLIS_IN_SECONDS % SECONDS_IN_MINUTE).toInt()
        val minutes = millisUntilFinished / MILLIS_IN_SECONDS / SECONDS_IN_MINUTE
        return String.format(FORMATTED_STRING_MINUTE_SECOND, minutes, seconds)
    }

    private fun startTimer() {
        timer = object : CountDownTimer(
            workingTimeModel.timeOfWaitAccuracy.toLong() * MILLIS_IN_SECONDS,
            MILLIS_IN_SECONDS
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val notification = notificationBuilder
                    .setContentTitle(
                        buildString {
                            append(getString(R.string.service_notify_title))
                            append(
                                if (millisUntilFinished < MILLIS_IN_SECONDS) getString(R.string.service_povtor) else getFormattedLeftTime(
                                    millisUntilFinished
                                )
                            )
                        }
                    )
                    .setProgress(
                        workingTimeModel.timeOfWaitAccuracy,
                        (millisUntilFinished / MILLIS_IN_SECONDS).toInt(),
                        false
                    )
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            override fun onFinish() = Unit // Collection timeout is owned by the service coroutine.
        }
        timer?.start()
    }

    private fun reRunGetLocations() {
        if (!scheduler.isTimeToGetLocaton()) {
            stopTracking()
        } else {
            try {
                scheduler.runAlarm(workingTimeModel.timeOfWorkingWM.toLong())
            } catch (_: SecurityException) {
                Log.w("TrackingCycle", "Unable to schedule next collection")
                stopTracking()
            }
        }
    }

    private fun cancelTimer(title: String, txtBody: String) {
        timer?.cancel()
        val notification = notificationBuilder
            .setContentTitle(title)
            .setContentText(txtBody)
            .setProgress(0, 0, false)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onCreate() {
        super.onCreate()
        val hasLocationPermission = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasLocationPermission) {
            stopSelf()
            return
        }
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
            foregroundStarted = true
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    fun stopTracking() {
        clearTracking()
        stopSelf()
    }

    private fun clearTracking() {
        isTracking = false
        lastRecordedRowId = null
        cycleJob?.cancel()
        repo.stopLocationUpdates()
        scheduler.cancelAlarm()
        timer?.cancel()
        isServiseAlive?.invoke(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (!foregroundStarted) {
            stopSelf()
            return START_NOT_STICKY
        }
        isTracking = true
        isServiseAlive?.invoke(true)
        scheduler.scheduleNextAlarmClockIfEnabled()
        startGetLocations()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        clearTracking()
        notificationManager.cancel(NOTIFICATION_ID)
        isServiseAlive = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    companion object {
        private const val ACTION_STOP = "ru.ama.whereme.STOP_TRACKING"
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_ID = 1
        private const val MILLIS_IN_SECONDS = 1000L
        private const val SECONDS_IN_MINUTE = 60
        private const val FORMATTED_STRING_MINUTE_SECOND = "%02d:%02d"
        fun stopIntent(context: Context): Intent = newIntent(context).setAction(ACTION_STOP)

        fun newIntent(context: Context): Intent {
            return Intent(context, MyForegroundService::class.java)
        }
    }
}