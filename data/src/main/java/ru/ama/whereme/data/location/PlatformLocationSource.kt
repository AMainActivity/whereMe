package ru.ama.whereme.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.repository.LocationSource
import ru.ama.whereme.domain.usecase.selectLocationFix
import javax.inject.Inject
import javax.inject.Singleton

/** GNSS works without GMS or a network connection; NETWORK_PROVIDER is optional. */
@Singleton
class PlatformLocationSource @Inject constructor(private val application: Application) : LocationSource {
    private val manager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(application, permission) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked immediately before registration; revocation is handled below.
    override suspend fun locate(accuracyMeters: Int, timeoutMillis: Long): LocationOutcome =
        withContext(Dispatchers.Main.immediate) {
            require(accuracyMeters > 0 && timeoutMillis > 0)
            val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return@withContext unavailable(LocationOutcome.Reason.PERMISSION_REQUIRED)
            }
            if (!LocationManagerCompat.isLocationEnabled(manager)) {
                return@withContext unavailable(LocationOutcome.Reason.LOCATION_DISABLED)
            }
            val providers = buildList {
                if (fine) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }.filter { it in manager.allProviders && manager.isProviderEnabled(it) }
            if (providers.isEmpty()) return@withContext unavailable(LocationOutcome.Reason.NO_PROVIDER)
            try {
                val updates = callbackFlow<LocationOutcome> {
                    val listeners = mutableListOf<LocationListenerCompat>()
                    val registered = mutableSetOf<String>()
                    val request = LocationRequestCompat.Builder(1_000L)
                        .setMinUpdateIntervalMillis(1_000L)
                        .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
                    try {
                        for (provider in providers) {
                            val listener = object : LocationListenerCompat {
                                override fun onLocationChanged(location: Location) {
                                    if (!location.hasAccuracy()) return
                                    val sample = LocationSample(location.latitude, location.longitude,
                                        location.accuracy, if (location.hasSpeed()) location.speed else 0f,
                                        location.time, location.elapsedRealtimeNanos)
                                    if (sample.isUsable(SystemClock.elapsedRealtimeNanos())) {
                                        trySend(LocationOutcome.Fix(sample))
                                    }
                                }
                                override fun onProviderDisabled(provider: String) {
                                    if (registered.none { manager.isProviderEnabled(it) }) {
                                        trySend(unavailable(if (LocationManagerCompat.isLocationEnabled(manager))
                                            LocationOutcome.Reason.NO_PROVIDER else LocationOutcome.Reason.LOCATION_DISABLED))
                                    }
                                }
                            }
                            // Include the listener before registration so partial failures are cleaned up too.
                            listeners.add(listener)
                            try {
                                LocationManagerCompat.requestLocationUpdates(manager, provider, request,
                                    ContextCompat.getMainExecutor(application), listener)
                                registered.add(provider)
                            } catch (_: IllegalArgumentException) {
                                // A provider may disappear between enumeration and registration.
                                LocationManagerCompat.removeUpdates(manager, listener)
                            }
                        }
                        if (registered.isEmpty()) trySend(unavailable(LocationOutcome.Reason.NO_PROVIDER))
                        awaitClose()
                    } finally {
                        listeners.forEach { LocationManagerCompat.removeUpdates(manager, it) }
                    }
                }
                selectLocationFix(updates, accuracyMeters, timeoutMillis)
            } catch (_: SecurityException) {
                unavailable(LocationOutcome.Reason.PERMISSION_REQUIRED)
            }
        }

    private fun unavailable(reason: LocationOutcome.Reason) = LocationOutcome.Unavailable(reason)
}
