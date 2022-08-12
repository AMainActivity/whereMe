package ru.ama.whereme.presentation

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MaViewModel @Inject constructor(
    private val getLocationUseCase: GetLocationUseCase,
    private val getLocation2UseCase: GetLocation2UseCase,
    private val getLastLocationUseCase: GetLastLocationUseCase,
    private val saveLocationOnBDUseCase: SaveLocationOnBDUseCase,
    private val stopLocationsUpdateUseCase: StopLocationsUpdateUseCase,
    private val runWorkerUpdateUseCase: RunWorkerUpdateUseCase
) : ViewModel() {

     //var lld : LocationLiveData?=null
     var lld2 : LiveData<Location?>?=null


    init {
        val sd=viewModelScope.async {
           // runWorkerUpdateUseCase(15)
         //   Log.e("runWorker1","15")
       //   lld2=getLastLocationUseCase()
        }
viewModelScope.launch {

  //  sd.await()
   //     lld2 = getLocation2UseCase()

}

       /* val d=viewModelScope.async(Dispatchers.IO)
        {
            val r=getTestInfoUseCase()
            r
        }

        viewModelScope.launch {
            val p=d.await()
            Log.e("ppp",p.toString())
            _testInfo.value = p
        */
    }

fun startLocationService()
{
    viewModelScope.launch {
        runWorkerUpdateUseCase(10)
        //		delay(1000*120)
        //	stopLocationsUpdateUseCase()
    }
}

    private val _testInfo = MutableLiveData<Location>()
    val testInfo: LiveData<Location>
        get() = _testInfo

    companion object {}
}


/*
/*
 * Copyright (C) 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.location.sample.foregroundlocation

import android.content.ServiceConnection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.sample.foregroundlocation.PlayServicesAvailableState.Initializing
import com.google.android.gms.location.sample.foregroundlocation.PlayServicesAvailableState.PlayServicesAvailable
import com.google.android.gms.location.sample.foregroundlocation.PlayServicesAvailableState.PlayServicesUnavailable
import com.google.android.gms.location.sample.foregroundlocation.data.LocationPreferences
import com.google.android.gms.location.sample.foregroundlocation.data.LocationRepository
import com.google.android.gms.location.sample.foregroundlocation.data.PlayServicesAvailabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
class TestListViewModel @Inject constructor(
    private val getTestInfoUseCase: GetTestInfoUseCase
) : ViewModel() {

    init {
        val d=viewModelScope.async(Dispatchers.IO)
        {
            val r=getTestInfoUseCase()
            r
        }

        viewModelScope.launch {
            val p=d.await()
            Log.e("ppp",p.toString())
            _testInfo.value = p
        }
    }
   private val _testInfo = MutableLiveData<List<TestInfo>>()
    val testInfo: LiveData<List<TestInfo>>
        get() = _testInfo

    companion object {}
}

/*

/

package com.google.android.gms.location.sample.foregroundlocation

import android.Manifest.permission
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Binder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.sample.foregroundlocation.ForegroundLocationService.LocalBinder
import com.google.android.gms.location.sample.foregroundlocation.data.LocationPreferences
import com.google.android.gms.location.sample.foregroundlocation.data.LocationRepository
import com.google.android.gms.location.sample.foregroundlocation.ui.hasPermission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


 * Service which manages turning location updates on and off. UI clients should bind to this service
 * to access this functionality.
 *
 * This service can be started the usual way (i.e. startService), but it will also start itself when
 * the first client binds to it. Thereafter it will manage its own lifetime as follows:
 *   - While there are any bound clients, the service remains started in the background. If it was
 *     in the foreground, it will exit the foreground, cancelling any ongoing notification.
 *   - When there are no bound clients and location updates are on, the service moves to the
 *     foreground and shows an ongoing notification with the latest location.
 *   - When there are no bound clients and location updates are off, the service stops itself.
 *
@AndroidEntryPoint
class ForegroundLocationService : LifecycleService() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var locationPreferences: LocationPreferences

    private val localBinder = LocalBinder()
    private var bindCount = 0

    private var started = false
    private var isForeground = false

    private fun isBound() = bindCount > 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // This action comes from our ongoing notification. The user requested to stop updates.
        if (intent?.action == ACTION_STOP_UPDATES) {
            stopLocationUpdates()
            lifecycleScope.launch {
                locationPreferences.setLocationTurnedOn(false)
            }
        }

        // Startup tasks only happen once.
        if (!started) {
            started = true
            // Check if we should turn on location updates.
            lifecycleScope.launch {
                if (locationPreferences.isLocationTurnedOn.first()) {
                    // If the service is restarted for any reason, we may have lost permission to
                    // access location since last time. In that case we won't turn updates on here,
                    // and the service will stop when we manage its lifetime below. Then the user
                    // will have to open the app to turn updates on again.
                    if (hasPermission(permission.ACCESS_FINE_LOCATION) ||
                        hasPermission(permission.ACCESS_COARSE_LOCATION)
                    ) {
                        locationRepository.startLocationUpdates()
                    }
                }
            }
            // Update any foreground notification when we receive location updates.
            lifecycleScope.launch {
                locationRepository.lastLocation.collect(::showNotification)
            }
        }

        // Decide whether to remain in the background, promote to the foreground, or stop.
        manageLifetime()

        // In case we are stopped by the system, have the system restart this service so we can
        // manage our lifetime appropriately.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        handleBind()
        return localBinder
    }

    override fun onRebind(intent: Intent?) {
        handleBind()
    }

    private fun handleBind() {
        bindCount++
        // Start ourself. This will let us manage our lifetime separately from bound clients.
        startService(Intent(this, this::class.java))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bindCount--
        lifecycleScope.launch {
            // UI client can unbind because it went through a configuration change, in which case it
            // will be recreated and bind again shortly. Wait a few seconds, and if still not bound,
            // manage our lifetime accordingly.
            delay(UNBIND_DELAY_MILLIS)
            manageLifetime()
        }
        // Allow clients to rebind, in which case onRebind will be called.
        return true
    }

    private fun manageLifetime() {
        when {
            // We should not be in the foreground while UI clients are bound.
            isBound() -> exitForeground()

            // Location updates were started.
            locationRepository.isReceivingLocationUpdates.value -> enterForeground()

            // Nothing to do, so we can stop.
            else -> stopSelf()
        }
    }

    private fun exitForeground() {
        if (isForeground) {
            isForeground = false
            stopForeground(true)
        }
    }

    private fun enterForeground() {
        if (!isForeground) {
            isForeground = true

            // Show notification with the latest location.
            showNotification(locationRepository.lastLocation.value)
        }
    }

    private fun showNotification(location: Location?) {
        if (!isForeground) {
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(location))
    }

    private fun createNotificationChannel() {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(notificationChannel)
        }
    }

    private fun buildNotification(location: Location?) : Notification {
        // Tapping the notification opens the app.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(this.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Include an action to stop location updates without going through the app UI.
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, this::class.java).setAction(ACTION_STOP_UPDATES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (location != null) {
            getString(R.string.location_lat_lng, location.latitude, location.longitude)
        } else {
            getString(R.string.waiting_for_location)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_location)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    // Methods for clients.

    fun startLocationUpdates() {
        locationRepository.startLocationUpdates()
    }

    fun stopLocationUpdates() {
        locationRepository.stopLocationUpdates()
    }

    // Binder which provides clients access to the service.
    internal inner class LocalBinder : Binder() {
        fun getService(): ForegroundLocationService = this@ForegroundLocationService
    }

    private companion object {
        const val UNBIND_DELAY_MILLIS = 2000.toLong() // 2 seconds
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "LocationUpdates"
        const val ACTION_STOP_UPDATES = BuildConfig.APPLICATION_ID + ".ACTION_STOP_UPDATES"
    }
}

//
 // ServiceConnection that provides access to a [ForegroundLocationService].
//
class ForegroundLocationServiceConnection @Inject constructor() : ServiceConnection {

    var service: ForegroundLocationService? = null
        private set

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as LocalBinder).getService()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Note: this should never be called since the service is in the same process.
        service = null
    }
}

*/


/*

@HiltViewModel
class MainViewModel @Inject constructor(
    playServicesAvailabilityChecker: PlayServicesAvailabilityChecker,
    locationRepository: LocationRepository,
    private val locationPreferences: LocationPreferences,
    private val serviceConnection: ForegroundLocationServiceConnection
) : ViewModel(), ServiceConnection by serviceConnection {

    val playServicesAvailableState = flow {
        emit(
            if (playServicesAvailabilityChecker.isGooglePlayServicesAvailable()) {
                PlayServicesAvailable
            } else {
                PlayServicesUnavailable
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Initializing)

    val isReceivingLocationUpdates = locationRepository.isReceivingLocationUpdates
    val lastLocation = locationRepository.lastLocation

    fun toggleLocationUpdates() {
        if (isReceivingLocationUpdates.value) {
            stopLocationUpdates()
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        serviceConnection.service?.startLocationUpdates()
        // Store that the user turned on location updates.
        // It's possible that the service was not connected for the above call. In that case, when
        // the service eventually starts, it will check the persisted value and react appropriately.
        viewModelScope.launch {
            locationPreferences.setLocationTurnedOn(true)
        }
    }

    private fun stopLocationUpdates() {
        serviceConnection.service?.stopLocationUpdates()
        // Store that the user turned off location updates.
        // It's possible that the service was not connected for the above call. In that case, when
        // the service eventually starts, it will check the persisted value and react appropriately.
        viewModelScope.launch {
            locationPreferences.setLocationTurnedOn(false)
        }
    }
}

enum class PlayServicesAvailableState {
    Initializing, PlayServicesUnavailable, PlayServicesAvailable
}

*/