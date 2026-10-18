package ru.ama.whereme.data.location

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** Temporary GMS adapter. Collection and cleanup belong to the calling service job. */
@Singleton
class FusedLocationSource @Inject constructor(
    private val client: FusedLocationProviderClient,
    private val availability: GoogleApiAvailability,
    private val application: Application
) {
    private var cancelCollection: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    suspend fun collectBestLocation(accuracyMeters: Int, timeoutMillis: Long): Location? =
        withContext(Dispatchers.Main.immediate) {
            check(cancelCollection == null) { "Location collection is already active" }
            if (availability.isGooglePlayServicesAvailable(application) != ConnectionResult.SUCCESS) {
                return@withContext null
            }
            var best: Location? = null
            withTimeoutOrNull(timeoutMillis) {
                callbackFlow {
                    var closed = false
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            if (closed) return
                            for (location in result.locations) {
                                if (location.hasAccuracy() && location.accuracy.isFinite() &&
                                    location.accuracy >= 0 && location.latitude.isFinite() &&
                                    location.longitude.isFinite() && location.latitude in -90.0..90.0 &&
                                    location.longitude in -180.0..180.0
                                ) {
                                    trySend(Location(location).apply { time = System.currentTimeMillis() })
                                }
                            }
                        }
                    }
                    cancelCollection = { cancel(CancellationException("Location collection stopped")) }
                    val request = LocationRequest.create().apply {
                        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                        interval = 10_000L
                        fastestInterval = 10_000L
                    }
                    try {
                        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                            .addOnFailureListener { close(it) }
                            .addOnSuccessListener {
                                // Registration can finish after cancellation/removal was requested.
                                if (closed) client.removeLocationUpdates(callback)
                            }
                        awaitClose()
                    } finally {
                        closed = true
                        cancelCollection = null
                        client.removeLocationUpdates(callback)
                    }
                }.first { sample ->
                    val current = best
                    if (current == null || sample.accuracy < current.accuracy) best = sample
                    sample.accuracy <= accuracyMeters
                }
            }
            coroutineContext.ensureActive()
            best
        }

    fun stop() {
        cancelCollection?.invoke()
    }

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): Location? = withTimeoutOrNull(5_000L) {
        client.lastLocation.await()
    }
}
