package ru.ama.whereme.feature.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.state.LoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(component: MapComponent, darkTheme: Boolean, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(MapPage.LOADING) }
    var webView by remember { mutableStateOf<MapWebView?>(null) }
    var tilesFailed by remember { mutableStateOf(false) }
    var layersOpen by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptyList<String>()) }
    var externalFailed by remember { mutableStateOf(false) }
    val measuring = state.location == MapLocationState.Locating
    val locationBusy = measuring || state.location is MapLocationState.AwaitingPermission
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme
    val theme = MapTheme(colors.background.hex(), colors.surface.hex(), colors.onSurface.hex(), colors.primary.hex(), dark = darkTheme)
    val ready = page == MapPage.READY && state.locations is LoadState.Ready
    val selected = state.points.filter { point ->
        point._id.toString() in selectedIds && (state.layers.previousPointsVisible || point == state.points.lastOrNull())
    }

    fun open(uri: Uri, fallback: Uri? = null) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            externalFailed = false
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, fallback)); externalFailed = false }
                catch (_: ActivityNotFoundException) { externalFailed = true }
            } else externalFailed = true
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        component.accept(MapIntent.LocationPermissionResult(granted.values.any { it }))
    }
    fun requestLocation(action: MapLocationAction) {
        val granted = LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        component.accept(MapIntent.RequestLocation(action, granted))
        if (!granted) permissions.launch(LOCATION_PERMISSIONS)
    }
    LaunchedEffect(state.location, webView, page) {
        val sample = (state.location as? MapLocationState.Position)?.sample ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (page != MapPage.READY) return@LaunchedEffect
        view.showLocation(sample.latitude, sample.longitude, sample.accuracyMeters)
        component.accept(MapIntent.DismissLocation)
    }
    DisposableEffect(webView, lifecycle) {
        val current = webView
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current?.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) current?.onPause()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) current?.onResume() else current?.onPause()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(modifier.fillMaxSize()) {
        if (state.locations == LoadState.Failed) MapMessage(R.string.map_storage_failed) { component.accept(MapIntent.Retry) }
        if (externalFailed) Text(stringResource(R.string.map_external_failed), Modifier.padding(16.dp), color = colors.error)
        if (page == MapPage.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (page == MapPage.FAILED) MapMessage(R.string.map_failed) { tilesFailed = false; page = MapPage.LOADING }
        if (tilesFailed && page == MapPage.READY) MapMessage(R.string.map_tiles_failed) { webView?.retryTiles() }
        if (state.locations is LoadState.Ready && state.points.isEmpty()) Text(stringResource(R.string.map_empty), Modifier.padding(horizontal = 16.dp))
        if (measuring) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.map_locating), Modifier.padding(horizontal = 16.dp))
        }
        if (state.location == MapLocationState.SettingsUnavailable) {
            Text(stringResource(R.string.map_location_settings_failed), Modifier.padding(16.dp), color = colors.error)
        }
        (state.location as? MapLocationState.Failed)?.reason?.let { reason ->
            val message = when (reason) {
                LocationOutcome.Reason.PERMISSION_REQUIRED -> R.string.map_permission_required
                LocationOutcome.Reason.LOCATION_DISABLED -> R.string.map_location_disabled
                LocationOutcome.Reason.NO_PROVIDER, LocationOutcome.Reason.NO_FIX -> R.string.map_location_failed
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(message), Modifier.weight(1f), color = colors.error)
                if (reason == LocationOutcome.Reason.PERMISSION_REQUIRED || reason == LocationOutcome.Reason.LOCATION_DISABLED) {
                    TextButton(onClick = {
                        val intent = if (reason == LocationOutcome.Reason.PERMISSION_REQUIRED)
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                        else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { externalFailed = true }
                    }) { Text(stringResource(R.string.map_open_settings)) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (page != MapPage.FAILED) AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    MapWebView(viewContext, onPage = { page = it }, onExternal = { open(it) },
                        onSelection = { selectedIds = it }, onTilesFailed = { tilesFailed = it })
                        .also { webView = it; it.loadUrl(MAP_URL) }
                },
                update = { view ->
                    view.setBackgroundColor(colors.background.toArgb())
                    if (page == MapPage.READY) {
                        view.configure(state.layers, theme)
                        view.render(state.date.toString(), (state.locations as? LoadState.Ready)?.value)
                    }
                },
                onRelease = { view -> view.release(); if (webView === view) webView = null },
            )
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                Column {
                    MapButton(R.drawable.ic_fit, R.string.map_fit, ready && state.points.isNotEmpty()) { webView?.fitAll() }
                    MapButton(R.drawable.ic_last_point, R.string.map_last_point, ready && state.points.isNotEmpty()) { webView?.showLast() }
                    MapButton(R.drawable.ic_layers, R.string.map_layers, true) { layersOpen = true }
                    MapButton(
                        R.drawable.ic_my_location,
                        R.string.map_my_location,
                        page == MapPage.READY && !locationBusy,
                    ) { requestLocation(MapLocationAction.Show) }
                }
            }
        }
    }
    if (layersOpen) ModalBottomSheet(onDismissRequest = { layersOpen = false }) {
        Text(stringResource(R.string.map_layers), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        LayerSwitch(R.string.map_previous_points, state.layers.previousPointsVisible) { component.accept(MapIntent.PreviousPoints(it)) }
        LayerSwitch(R.string.map_track, state.layers.trackVisible) { component.accept(MapIntent.Track(it)) }
    }
    if (selected.isNotEmpty()) ModalBottomSheet(onDismissRequest = { selectedIds = emptyList(); webView?.select(null) }) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(selected, key = { it._id }) { point ->
                ListItem(
                    headlineContent = {
                        Text(if (point.dateend != null)
                            stringResource(R.string.map_interval, point.datestart, point.dateend)
                        else point.datestart)
                    },
                    supportingContent = { Text(stringResource(R.string.map_accuracy, point.accuracy)) },
                    modifier = Modifier.clickable { selectedIds = listOf(point._id.toString()); webView?.select(point._id.toString()) },
                )
            }
            if (selected.size == 1) item {
                TextButton(onClick = {
                    val point = selected.single()
                    selectedIds = emptyList()
                    webView?.select(null)
                    requestLocation(MapLocationAction.Route(point.latitude, point.longitude))
                }, enabled = !locationBusy, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.map_route_action)) }
            }
        }
    }
    (state.location as? MapLocationState.Route)?.request?.let { request ->
        AlertDialog(onDismissRequest = { component.accept(MapIntent.DismissLocation) }, title = { Text(stringResource(R.string.map_route)) },
            text = {
                Text(stringResource(
                    R.string.map_route_coordinates,
                    request.fromLatitude.toString(), request.fromLongitude.toString(),
                    request.toLatitude.toString(), request.toLongitude.toString(),
                ))
            },
            confirmButton = { TextButton(onClick = {
                component.accept(MapIntent.DismissLocation)
                open(
                    String.format(Locale.ROOT, ROUTE_URI_FORMAT,
                        request.fromLongitude, request.fromLatitude,
                        request.toLongitude, request.toLatitude,
                    ).toUri(),
                    ROUTE_APP_STORE_URI.toUri(),
                )
            }) { Text(stringResource(R.string.map_route_action)) } },
            dismissButton = { TextButton(onClick = { component.accept(MapIntent.DismissLocation) }) { Text(stringResource(R.string.map_cancel)) } },
        )
    }
}

@Composable
private fun MapMessage(message: Int, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(message), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text(stringResource(R.string.map_retry)) }
    }
}

@Composable
private fun MapButton(icon: Int, description: Int, enabled: Boolean, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled) { Icon(painterResource(icon), stringResource(description)) }
}

@Composable
private fun LayerSwitch(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(stringResource(label)) }, trailingContent = { Switch(checked, null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = change))
}

private fun Color.hex() = String.format(Locale.ROOT, "#%06X", toArgb() and RGB_MASK)
private const val RGB_MASK = 0xFFFFFF
private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private const val ROUTE_URI_FORMAT = "dgis://2gis.ru/routeSearch/rsType/car/from/%s,%s/to/%s,%s"
private const val ROUTE_APP_STORE_URI = "market://details?id=ru.dublgis.dgismobile"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapToolbarActions(component: MapComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val date = state.date
    var datePicker by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { datePicker = true }) {
        Icon(painterResource(R.drawable.ic_calendar), stringResource(R.string.map_choose_date))
    }
    if (datePicker) {
        val picker =
            rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            component.accept(
                                MapIntent.SelectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            )
                        }
                        datePicker = false
                    },
                ) {
                    Text(stringResource(R.string.map_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePicker = false }) {
                    Text(stringResource(R.string.map_cancel))
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}
