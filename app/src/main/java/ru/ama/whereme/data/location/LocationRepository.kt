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

package com.google.android.gms.location.sample.foregroundlocation.data

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class LocationRepository @Inject constructor(
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {
    private val callback = Callback()

    private val _isReceivingUpdates = MutableStateFlow(false)
    val isReceivingLocationUpdates = _isReceivingUpdates.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    private val _infoLocation = MutableLiveData<Location?>()
    val infoLocation: LiveData<Location?>
        get() = _infoLocation
    val lastLocation = _lastLocation.asStateFlow()

    @SuppressLint("MissingPermission") // Only called when holding location permission.
    fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000 // 10 seconds
        }
        // Note: For this sample it's fine to use the main looper, so our callback will run on the
        // main thread. If your callback will perform any intensive operations (writing to disk,
        // making a network request, etc.), either change to a background thread from the callback,
        // or create a HandlerThread and pass its Looper here instead.
        // See https://developer.android.com/reference/android/os/HandlerThread.
        fusedLocationProviderClient.requestLocationUpdates(
            request,
            /*object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    Log.e("getLocation0",locationResult.lastLocation.toString())
                }}*/callback,
            Looper.getMainLooper()
        )
        _isReceivingUpdates.value = true
        Log.e("getLocation00",fusedLocationProviderClient.toString())
    }

    fun stopLocationUpdates() {
        Log.e("getLocationStop",fusedLocationProviderClient.toString())
        fusedLocationProviderClient.removeLocationUpdates(callback)
        _isReceivingUpdates.value = false
        _lastLocation.value = null
        _infoLocation.value = null
    }

    private inner class Callback: LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            _infoLocation.value = result.lastLocation
            _lastLocation.value = result.lastLocation
            Log.e("getLocation0",result.lastLocation.toString())
        }
    }
}
