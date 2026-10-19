package ru.ama.whereme.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.tracking.TrackingClock
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.domain.tracking.TrackingFailure
import ru.ama.whereme.domain.tracking.TrackingMode
import ru.ama.whereme.domain.tracking.RecordingSession
import ru.ama.whereme.tracking.TrackingAction
import ru.ama.whereme.tracking.TrackingNotification
import ru.ama.whereme.tracking.TrackingRequirement
import ru.ama.whereme.tracking.trackingCapabilities

@AndroidEntryPoint
class MyForegroundService : LifecycleService() {
    @Inject lateinit var coordinator: TrackingCoordinator
    @Inject lateinit var clock: TrackingClock
    private lateinit var handle: TrackingCoordinator.Handle
    private val notification by lazy { TrackingNotification(this, clock) }
    private var foregroundStarted = false
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        handle = coordinator.attach()
        ContextCompat.registerReceiver(this, environmentChanged, IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            coordinator.state.collectLatest { snapshot ->
                if (snapshot.ownerId != handle.ownerId || snapshot.requestId != handle.latestRequest || lastStartId == 0) return@collectLatest
                Log.d("TrackingService", "State: ${snapshot.state}")
                if (snapshot.state.isRunning) {
                    notification.render(snapshot.state)
                } else if (stopSelfResult(lastStartId)) {
                    // Android also checks for a newer startId which has not reached onStartCommand yet.
                    removeForeground()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId
        when {
            intent?.action == ACTION_STOP -> handle.stop()
            intent == null -> stopSelfResult(startId)
            intent.action == ACTION_WAKE && promoteToForeground() -> {
                val id = intent.getStringExtra(EXTRA_RECORDING)
                val due = intent.getLongExtra(EXTRA_DUE, 0)
                if (id.isNullOrBlank() || due <= 0) stopSelfResult(startId)
                else {
                    Log.d("TrackingAlarm", "Sampling alarm delivered; due=$due")
                    handle.wake(id, due)
                }
            }
            intent.action == ACTION_WAKE -> stopSelfResult(startId)
            promoteToForeground() -> handle.start(if (intent.getBooleanExtra(EXTRA_SCHEDULED, false))
                TrackingMode.SCHEDULED else TrackingMode.MANUAL)
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground(): Boolean {
        if (trackingCapabilities().requirement(TrackingAction.MANUAL) == TrackingRequirement.EXACT_ALARM) {
            handle.fail(TrackingFailure.ALARM); return false
        }
        val unavailable = unavailableLocation()
        if (unavailable != null) { handle.block(unavailable); return false }
        if (foregroundStarted) return true
        return try {
            val initial = notification.initial()
            if (Build.VERSION.SDK_INT >= 29) startForeground(TrackingNotification.ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(TrackingNotification.ID, initial)
            foregroundStarted = true
            true
        } catch (_: SecurityException) {
            handle.block(LocationOutcome.Reason.PERMISSION_REQUIRED)
            false
        } catch (_: IllegalStateException) {
            handle.fail(TrackingFailure.FOREGROUND)
            false
        }
    }

    private fun unavailableLocation(): LocationOutcome.Reason? = when (trackingCapabilities().requirement(TrackingAction.MANUAL)) {
        TrackingRequirement.FOREGROUND_LOCATION, TrackingRequirement.PRECISE_LOCATION, TrackingRequirement.BACKGROUND_LOCATION -> LocationOutcome.Reason.PERMISSION_REQUIRED
        TrackingRequirement.LOCATION_ENABLED -> LocationOutcome.Reason.LOCATION_DISABLED
        TrackingRequirement.PROVIDER -> LocationOutcome.Reason.NO_PROVIDER
        else -> null
    }

    private val environmentChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> handle.timeChanged()
                LocationManager.PROVIDERS_CHANGED_ACTION -> unavailableLocation()?.let { handle.block(it) }
            }
        }
    }

    private fun removeForeground() {
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    override fun onDestroy() {
        handle.close() // Non-blocking on Main; the coordinator cancels and joins resource cleanup.
        unregisterReceiver(environmentChanged)
        removeForeground()
        notification.remove()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SCHEDULED = "scheduled"
        private const val ACTION_STOP = "ru.ama.whereme.STOP_TRACKING"
        private const val ACTION_WAKE = "ru.ama.whereme.SAMPLE_LOCATION"
        private const val EXTRA_RECORDING = "recording_id"
        private const val EXTRA_DUE = "sample_due_millis"
        fun wakeIntent(context: Context, session: RecordingSession?): Intent =
            Intent(context, MyForegroundService::class.java).setAction(ACTION_WAKE).apply {
                if (session != null) {
                    putExtra(EXTRA_RECORDING, session.id)
                    putExtra(EXTRA_DUE, session.nextAtMillis)
                }
            }
        fun stopIntent(context: Context): Intent = newIntent(context).setAction(ACTION_STOP)
        fun newIntent(context: Context, scheduled: Boolean = false): Intent =
            Intent(context, MyForegroundService::class.java).putExtra(EXTRA_SCHEDULED, scheduled)
    }
}
