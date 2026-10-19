package ru.ama.whereme.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ru.ama.whereme.domain.tracking.*

/** Activity-result launchers are view-owned; the requested operation belongs to the component. */
@Composable
internal fun TrackingAccessRequest(component: SettingsComponent, action: TrackingAction?) {
    val context = LocalContext.current
    var waiting by rememberSaveable { mutableStateOf<AccessRequest?>(null) }
    var requirement by remember(action) { mutableStateOf(component.capabilities().requirement()) }
    fun finish(error: Int? = null) {
        waiting = null
        component.accept(SettingsIntent.AccessFinished(error))
    }
    fun returned() {
        val previous = waiting ?: return
        waiting = null
        if (component.state.value.accessRequest == null) return
        val next = component.capabilities().requirement()
        if (previous == AccessRequest.NOTIFICATIONS) finish()
        else if (next == previous.requirement) finish(R.string.settings_access_failed)
        else requirement = next
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            returned()
        }
    val systemSettings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            returned()
        }
    if (action == null) return
    fun openSettings(intent: Intent, request: AccessRequest) {
        waiting = request
        try {
            systemSettings.launch(intent)
        } catch (_: ActivityNotFoundException) {
            finish(R.string.settings_no_activity)
        }
    }
    if (waiting != null) return
    when (requirement) {
        TrackingRequirement.READY ->
            LaunchedEffect(action, requirement) {
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                ) {
                    waiting = AccessRequest.NOTIFICATIONS
                    permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else finish()
            }
        TrackingRequirement.PROVIDER ->
            LaunchedEffect(action, requirement) { finish(R.string.tracking_no_provider) }
        else ->
            AlertDialog(
                onDismissRequest = { component.accept(SettingsIntent.CancelAccess) },
                title = { Text(stringResource(R.string.settings_access_title)) },
                text = {
                    Text(
                        stringResource(
                            when (requirement) {
                                TrackingRequirement.FOREGROUND_LOCATION,
                                TrackingRequirement.PRECISE_LOCATION ->
                                    R.string.tracking_location_explanation
                                TrackingRequirement.LOCATION_ENABLED ->
                                    R.string.tracking_location_disabled
                                TrackingRequirement.BACKGROUND_LOCATION ->
                                    R.string.tracking_background_explanation
                                TrackingRequirement.EXACT_ALARM ->
                                    R.string.tracking_alarm_explanation
                                else -> R.string.tracking_requirements_changed
                            }
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (requirement) {
                                TrackingRequirement.FOREGROUND_LOCATION,
                                TrackingRequirement.PRECISE_LOCATION -> {
                                    waiting =
                                        if (requirement == TrackingRequirement.PRECISE_LOCATION)
                                            AccessRequest.PRECISE_LOCATION
                                        else AccessRequest.FOREGROUND_LOCATION
                                    permissions.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                }
                                TrackingRequirement.LOCATION_ENABLED ->
                                    openSettings(
                                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                        AccessRequest.LOCATION_SETTINGS,
                                    )
                                TrackingRequirement.BACKGROUND_LOCATION ->
                                    if (Build.VERSION.SDK_INT >= 30) {
                                        openSettings(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                "package:${context.packageName}".toUri(),
                                            ),
                                            AccessRequest.BACKGROUND_LOCATION,
                                        )
                                    } else if (Build.VERSION.SDK_INT >= 29) {
                                        waiting = AccessRequest.BACKGROUND_LOCATION
                                        permissions.launch(
                                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                        )
                                    }
                                TrackingRequirement.EXACT_ALARM ->
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        openSettings(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                "package:${context.packageName}".toUri(),
                                            ),
                                            AccessRequest.EXACT_ALARM,
                                        )
                                    }
                                else -> Unit
                            }
                        }
                    ) {
                        Text(stringResource(R.string.tracking_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { component.accept(SettingsIntent.CancelAccess) }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                },
            )
    }
}

private enum class AccessRequest(val requirement: TrackingRequirement?) {
    FOREGROUND_LOCATION(TrackingRequirement.FOREGROUND_LOCATION),
    PRECISE_LOCATION(TrackingRequirement.PRECISE_LOCATION),
    LOCATION_SETTINGS(TrackingRequirement.LOCATION_ENABLED),
    BACKGROUND_LOCATION(TrackingRequirement.BACKGROUND_LOCATION),
    EXACT_ALARM(TrackingRequirement.EXACT_ALARM),
    NOTIFICATIONS(null),
}
