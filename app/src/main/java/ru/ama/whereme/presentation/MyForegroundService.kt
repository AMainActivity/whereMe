package ru.ama.whereme.presentation

import java.time.Instant
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.state.asLoadState
import ru.ama.whereme.domain.tracking.RecordingSession
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.domain.tracking.TrackingFailure
import ru.ama.whereme.domain.tracking.TrackingMode
import ru.ama.whereme.domain.tracking.TrackingRequirement
import ru.ama.whereme.tracking.TrackingNotification
import ru.ama.whereme.tracking.trackingCapabilities

@AndroidEntryPoint
class MyForegroundService : LifecycleService() {
    @Inject lateinit var coordinator: TrackingCoordinator
    @Inject lateinit var sync: ru.ama.whereme.sync.LocationSyncScheduler
    @Inject lateinit var history: LocationHistoryRepository
    private lateinit var handle: TrackingCoordinator.Handle
    private val notification by lazy { TrackingNotification(this) }
    private var foregroundStarted = false
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        handle = coordinator.attach()
        ContextCompat.registerReceiver(this, environmentChanged, IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            combine(handle.state, history.lastRecordedTime.asLoadState()) { snapshot, lastRecorded ->
                snapshot to lastRecorded
            }.collect { (snapshot, lastRecorded) ->
                if (snapshot.startId != lastStartId || lastStartId == 0) return@collect
                Log.d(TAG, "State: ${snapshot.state}")
                if (snapshot.state.isRunning) {
                    notification.render(snapshot.state, lastRecorded)
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
            intent?.action == ACTION_STOP -> handle.stop(startId)
            intent == null -> stopSelfResult(startId)
            intent.action == ACTION_WAKE && promoteToForeground() -> {
                val id = intent.getStringExtra(EXTRA_RECORDING)
                val due = intent.getLongExtra(EXTRA_DUE, 0)
                if (id.isNullOrBlank() || due <= 0) stopSelfResult(startId)
                else {
                    Log.d(ALARM_TAG, "Sampling alarm delivered; due=$due")
                    handle.wake(id, Instant.ofEpochMilli(due), startId)
                }
            }
            intent.action == ACTION_WAKE -> stopSelfResult(startId)
            promoteToForeground() -> handle.start(if (intent.getBooleanExtra(EXTRA_SCHEDULED, false))
                TrackingMode.SCHEDULED else TrackingMode.MANUAL, startId)
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground(): Boolean {
        if (trackingCapabilities().requirement() == TrackingRequirement.EXACT_ALARM) {
            handle.fail(TrackingFailure.ALARM, lastStartId); return false
        }
        val unavailable = unavailableLocation()
        if (unavailable != null) { handle.block(unavailable, lastStartId); return false }
        if (foregroundStarted) return true
        return try {
            val initial = notification.initial()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(TrackingNotification.ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(TrackingNotification.ID, initial)
            foregroundStarted = true
            true
        } catch (_: SecurityException) {
            handle.block(LocationOutcome.Reason.PERMISSION_REQUIRED, lastStartId)
            false
        } catch (_: IllegalStateException) {
            handle.fail(TrackingFailure.FOREGROUND, lastStartId)
            false
        }
    }

    private fun unavailableLocation(): LocationOutcome.Reason? = when (trackingCapabilities().requirement()) {
        TrackingRequirement.FOREGROUND_LOCATION, TrackingRequirement.PRECISE_LOCATION, TrackingRequirement.BACKGROUND_LOCATION -> LocationOutcome.Reason.PERMISSION_REQUIRED
        TrackingRequirement.LOCATION_ENABLED -> LocationOutcome.Reason.LOCATION_DISABLED
        TrackingRequirement.PROVIDER -> LocationOutcome.Reason.NO_PROVIDER
        else -> null
    }

    private val environmentChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                LocationManager.PROVIDERS_CHANGED_ACTION -> unavailableLocation()?.let { handle.block(it, lastStartId) }
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
        sync.schedule()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val ALARM_TAG = "TrackingAlarm"
        private const val EXTRA_SCHEDULED = "scheduled"
        private const val ACTION_STOP = "ru.ama.whereme.STOP_TRACKING"
        private const val ACTION_WAKE = "ru.ama.whereme.SAMPLE_LOCATION"
        private const val EXTRA_RECORDING = "recording_id"
        private const val EXTRA_DUE = "sample_due_millis"
        fun wakeIntent(context: Context, session: RecordingSession?): Intent =
            Intent(context, MyForegroundService::class.java).setAction(ACTION_WAKE).apply {
                if (session != null) {
                    putExtra(EXTRA_RECORDING, session.id)
                    putExtra(EXTRA_DUE, session.nextAt.toEpochMilli())
                }
            }
        fun stopIntent(context: Context): Intent = newIntent(context).setAction(ACTION_STOP)
        fun newIntent(context: Context, scheduled: Boolean = false): Intent =
            Intent(context, MyForegroundService::class.java).putExtra(EXTRA_SCHEDULED, scheduled)
    }
}
