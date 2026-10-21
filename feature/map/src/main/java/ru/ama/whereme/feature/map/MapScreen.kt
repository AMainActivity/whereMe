package ru.ama.whereme.feature.map

import java.time.ZoneOffset
import java.time.Instant
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import ru.ama.whereme.domain.state.LoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(component: MapComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()
    val locations = state.locations
    var page by remember { mutableStateOf(MapPage.LOADING) }
    var webView by remember { mutableStateOf<MapWebView?>(null) }
    var route by remember { mutableStateOf<RouteRequest?>(null) }
    var externalFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val background = MaterialTheme.colorScheme.surface.toArgb()
    fun open(uri: Uri, fallback: Uri? = null) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            externalFailed = false
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
                    externalFailed = false
                } catch (_: ActivityNotFoundException) {
                    externalFailed = true
                }
            } else externalFailed = true
        }
    }
    DisposableEffect(webView, lifecycle) {
        val current = webView
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current?.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) current?.onPause()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) current?.onResume()
        else current?.onPause()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(modifier.fillMaxSize()) {
        if (locations == LoadState.Failed) {
            Text(
                stringResource(R.string.map_storage_failed),
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { component.accept(MapIntent.Retry) }) {
                Text(stringResource(R.string.map_retry))
            }
        }
        if (externalFailed)
            Text(
                stringResource(R.string.map_external_failed),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        if (page == MapPage.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (page == MapPage.FAILED) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.map_failed))
                    TextButton(onClick = { webView?.reload() }) {
                        Text(stringResource(R.string.map_retry))
                    }
                }
            }
        }
        val points = (locations as? LoadState.Ready)?.value
        if (points == EMPTY_POINTS_JSON)
            Text(
                stringResource(R.string.map_empty),
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { viewContext ->
                MapWebView(
                        viewContext,
                        onPage = { page = it },
                        onExternal = { open(it) },
                        onRoute = {
                            if (
                                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                    route == null
                            )
                                route = it
                        },
                    )
                    .also {
                        webView = it
                        it.loadUrl(MAP_URL)
                    }
            },
            update = { view ->
                view.setBackgroundColor(background)
                // Native loading/error UI owns these states, including TalkBack visibility.
                view.visibility = if (page == MapPage.READY) View.VISIBLE else View.INVISIBLE
                if (page == MapPage.READY) view.render(points ?: EMPTY_POINTS_JSON)
            },
            onRelease = { view ->
                view.release()
                if (webView === view) webView = null
            },
        )
    }
    route?.let { request ->
        AlertDialog(
            onDismissRequest = { route = null },
            title = { Text(stringResource(R.string.map_route)) },
            text = {
                Text(
                    stringResource(
                        R.string.map_route_coordinates,
                        request.fromLatitude.toString(),
                        request.fromLongitude.toString(),
                        request.toLatitude.toString(),
                        request.toLongitude.toString(),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        route = null
                        open(
                            String.format(
                                    Locale.ROOT,
                                    ROUTE_URI_FORMAT,
                                    request.fromLongitude,
                                    request.fromLatitude,
                                    request.toLongitude,
                                    request.toLatitude,
                                )
                                .toUri(),
                            ROUTE_APP_STORE_URI.toUri(),
                        )
                    }
                ) {
                    Text(stringResource(R.string.map_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { route = null }) { Text(stringResource(R.string.map_cancel)) }
            },
        )
    }
}

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

private const val ROUTE_URI_FORMAT = "dgis://2gis.ru/routeSearch/rsType/car/from/%s,%s/to/%s,%s"
private const val ROUTE_APP_STORE_URI = "market://details?id=ru.dublgis.dgismobile"
private const val EMPTY_POINTS_JSON = "[]"
