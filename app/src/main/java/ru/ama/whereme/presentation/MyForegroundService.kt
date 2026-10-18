package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import ru.ama.whereme.domain.entity.LocationOutcome

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.data.sync.LocationBatchUploader
import ru.ama.whereme.data.location.LocationRecorder
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.tracking.TrackingScheduler
import ru.ama.whereme.tracking.TrackingRuntime
import ru.ama.whereme.tracking.TrackingState
import ru.ama.whereme.tracking.TrackingAction
import ru.ama.whereme.tracking.TrackingRequirement
import ru.ama.whereme.tracking.trackingCapabilities
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.usecase.SchedulePolicy
import java.util.TimeZone
import android.app.Notification
import javax.inject.Inject

@AndroidEntryPoint
class MyForegroundService : LifecycleService() {

    private var timer: CountDownTimer? = null
    private lateinit var workingTimeModel: SettingsDomModel
    private var cycleJob: Job? = null
    private var lastRecordedRowId: Long? = null
    private var destroyed = false
    private var foregroundStarted = false
    private var scheduled = false

    @Inject lateinit var runtime: TrackingRuntime
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

    private val notificationBuilder by lazy {
        createNotificationBuilder()
    }

    private fun startGetLocations() {
        if (destroyed || !foregroundStarted || cycleJob?.isActive == true) return
        runtime.publish(TrackingState.Starting)
        cycleJob = lifecycleScope.launch {
            try {
                while (isActive) {
                    workingTimeModel = settings.settings.first()
                    if (scheduled && !scheduler.isTimeToGetLocation()) { stopTracking(); return@launch }
                    runtime.publish(TrackingState.Collecting)
                    startTimer()
                    val outcome = repo.collectLocation(workingTimeModel)
                    timer?.cancel()
                    currentCoroutineContext().ensureActive()
                    if (outcome is LocationOutcome.Unavailable && outcome.reason != LocationOutcome.Reason.NO_FIX) {
                        stopTracking()
                        runtime.publish(TrackingState.LocationUnavailable(outcome.reason))
                        return@launch
                    }
                    if (scheduled && !scheduler.isTimeToGetLocation()) { stopTracking(); return@launch }
                    val sample = (outcome as? LocationOutcome.Fix)?.sample?.let { fix ->
                        Location("platform").apply {
                            latitude = fix.latitude; longitude = fix.longitude
                            accuracy = fix.accuracyMeters; speed = fix.speedMetersPerSecond
                            time = fix.timeMillis; elapsedRealtimeNanos = fix.elapsedRealtimeNanos
                        }
                    }
                    if (sample != null) lastRecordedRowId =
                        repo.saveLocation(sample, sample.time, workingTimeModel, lastRecordedRowId)
                    currentCoroutineContext().ensureActive()
                    runtime.publish(TrackingState.Waiting(noFix = sample == null))
                    cancelTimer(getString(R.string.app_name),
                        getString(if (sample != null) R.string.service_timer_success else R.string.service_timer_povtor) +
                            repo.getDate(System.currentTimeMillis()))
                    uploader.uploadPending()
                    val period = workingTimeModel.timeOfWorkingWM.toLong() * MILLIS_IN_SECONDS
                    val untilEnd = if (scheduled) SchedulePolicy.activeUntil(workingTimeModel,
                        System.currentTimeMillis(), TimeZone.getDefault())?.minus(System.currentTimeMillis()) ?: 0L
                        else period
                    val wait = minOf(period, untilEnd).coerceAtLeast(1L)
                    // Wake for the next sample, interval end, or a real settings change; Stop cancels all three.
                    withTimeoutOrNull(wait) { settings.settings.first { it != workingTimeModel } }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                stopTracking()
                runtime.publish(TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED))
            } catch (failure: Exception) {
                Log.w("TrackingCycle", "Cycle failed: " + failure.javaClass.simpleName)
                stopTracking()
                runtime.publish(TrackingState.Failed)
            } finally {
                timer?.cancel()
                runtime.stopped()
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
            .addAction(0, getString(R.string.tracking_stop), PendingIntent.getService(this, 1,
                stopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
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
                postNotification(notification)
            }

            override fun onFinish() = Unit // Collection timeout is owned by the service coroutine.
        }
        timer?.start()
    }

    private fun cancelTimer(title: String, txtBody: String) {
        timer?.cancel()
        val notification = notificationBuilder
            .setContentTitle(title)
            .setContentText(txtBody)
            .setProgress(0, 0, false)
            .build()
        postNotification(notification)
    }

    private fun postNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private val providersChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != LocationManager.PROVIDERS_CHANGED_ACTION || !runtime.state.value.isRunning) return
            val reason = when (trackingCapabilities().requirement(TrackingAction.MANUAL)) {
                TrackingRequirement.FOREGROUND_LOCATION, TrackingRequirement.PRECISE_LOCATION -> LocationOutcome.Reason.PERMISSION_REQUIRED
                TrackingRequirement.LOCATION_ENABLED -> LocationOutcome.Reason.LOCATION_DISABLED
                TrackingRequirement.PROVIDER -> LocationOutcome.Reason.NO_PROVIDER
                else -> return
            }
            stopTracking()
            runtime.publish(TrackingState.LocationUnavailable(reason))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, providersChanged, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        val hasLocationPermission = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasLocationPermission) {
            runtime.publish(TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED))
            stopSelf()
            return
        }
        lifecycleScope.launch {
            try {
                settings.settings.collect { if (scheduled && !it.isEnable) stopTracking() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                Log.w("TrackingSettings", "Settings unavailable: " + failure.javaClass.simpleName)
                stopTracking()
                runtime.publish(TrackingState.Failed)
            }
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
            runtime.publish(TrackingState.LocationUnavailable(LocationOutcome.Reason.PERMISSION_REQUIRED))
            stopSelf()
        }
    }

    fun stopTracking() {
        clearTracking()
        stopSelf()
    }

    private fun clearTracking() {
        runtime.stopped()
        lastRecordedRowId = null
        cycleJob?.cancel()
        timer?.cancel()
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
        if (cycleJob?.isActive != true) scheduled = intent?.getBooleanExtra(EXTRA_SCHEDULED, false) == true
        else if (intent?.getBooleanExtra(EXTRA_SCHEDULED, false) != true) scheduled = false
        startGetLocations()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        unregisterReceiver(providersChanged)
        clearTracking()
        notificationManager.cancel(NOTIFICATION_ID)
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
        private const val EXTRA_SCHEDULED = "scheduled"
        private const val ACTION_STOP = "ru.ama.whereme.STOP_TRACKING"
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_ID = 1
        private const val MILLIS_IN_SECONDS = 1000L
        private const val SECONDS_IN_MINUTE = 60
        private const val FORMATTED_STRING_MINUTE_SECOND = "%02d:%02d"
        fun stopIntent(context: Context): Intent = newIntent(context).setAction(ACTION_STOP)

        fun newIntent(context: Context, scheduled: Boolean = false): Intent {
            return Intent(context, MyForegroundService::class.java).putExtra(EXTRA_SCHEDULED, scheduled)
        }
    }
}