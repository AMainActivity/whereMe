package ru.ama.whereme.tracking

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

enum class TrackingAction { MANUAL, SCHEDULE }
enum class TrackingRequirement { FOREGROUND_LOCATION, LOCATION_ENABLED, PRECISE_LOCATION, PROVIDER, BACKGROUND_LOCATION, EXACT_ALARM, READY }

data class TrackingCapabilities(
    val foreground: Boolean,
    val locationEnabled: Boolean,
    val provider: Boolean,
    val preciseWouldProvideLocation: Boolean,
    val background: Boolean,
    val exactAlarm: Boolean
) {
    fun requirement(): TrackingRequirement = when {
        !foreground -> TrackingRequirement.FOREGROUND_LOCATION
        !locationEnabled -> TrackingRequirement.LOCATION_ENABLED
        !provider && preciseWouldProvideLocation -> TrackingRequirement.PRECISE_LOCATION
        !provider -> TrackingRequirement.PROVIDER
        !background -> TrackingRequirement.BACKGROUND_LOCATION
        !exactAlarm -> TrackingRequirement.EXACT_ALARM
        else -> TrackingRequirement.READY
    }
}

fun Context.trackingCapabilities(): TrackingCapabilities {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val enabled = LocationManagerCompat.isLocationEnabled(manager)
    fun provider(name: String) = name in manager.allProviders && manager.isProviderEnabled(name)
    val gps = provider(LocationManager.GPS_PROVIDER)
    val network = provider(LocationManager.NETWORK_PROVIDER)
    return TrackingCapabilities(fine || coarse, enabled, (fine && gps) || ((fine || coarse) && network),
        !fine && gps, Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        Build.VERSION.SDK_INT < 31 || (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms())
}
