package ru.ama.whereme.feature.map

import java.time.format.ResolverStyle
import java.time.format.DateTimeFormatter
import java.io.IOException
import java.time.LocalDate
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
import com.arkivanov.essenty.lifecycle.doOnPause
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.state.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapComponent(
    context: ComponentContext,
    history: LocationHistoryRepository,
    private val locateCurrent: suspend () -> LocationOutcome,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ComponentContext by context {
    private val scope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val date =
        MutableStateFlow(stateKeeper.consume(DATE_STATE_KEY, String.serializer())?.let { LocalDate.parse(it, MAP_DATE_FORMAT) } ?: LocalDate.now())
    private val retry = MutableStateFlow(0)
    private val json = Json { explicitNulls = false }
    private val mutable = MutableStateFlow(MapState(
        date.value, LoadState.Loading,
        layers = stateKeeper.consume(LAYERS_STATE_KEY, MapLayers.serializer()) ?: MapLayers(),
    ))
    val state = mutable.asStateFlow()
    private var locationJob: Job? = null
    private val observations =
        combine(date, retry) { selected, _ -> selected }
            .flatMapLatest { selected ->
                history
                    .getLocationById(selected)
                    .mapLatest { rows -> withContext(defaultDispatcher) { rows.map(MapPoint::from) } }
                    .distinctUntilChanged()
                    .mapLatest { points ->
                        withContext(defaultDispatcher) { MapHistory(points, json.encodeToString(points)) }
                    }
                    .asLoadState()
                    .map { result ->
                        when (result) {
                            is LoadState.Ready -> MapState(selected, LoadState.Ready(result.value.json), result.value.points)
                            LoadState.Loading -> MapState(selected, LoadState.Loading)
                            LoadState.Failed -> MapState(selected, LoadState.Failed)
                        }
                    }
            }

    init {
        stateKeeper.register(DATE_STATE_KEY, String.serializer()) { date.value.format(MAP_DATE_FORMAT) }
        stateKeeper.register(LAYERS_STATE_KEY, MapLayers.serializer()) { mutable.value.layers }
        lifecycle.doOnPause {
            if (mutable.value.location == MapLocationState.Locating) dismissLocation()
        }
        scope.launch {
            // Retaining the tab must not keep preparing map data while another tab is visible
            // or the Activity is in the background.
            lifecycle.repeatOnLifecycle {
                observations.collect { day -> mutable.update { day.copy(layers = it.layers, location = it.location) } }
            }
        }
    }

    fun accept(intent: MapIntent) {
        when (intent) {
            is MapIntent.SelectDate -> date.value = intent.date
            MapIntent.Retry -> retry.value++
            is MapIntent.PreviousPoints -> mutable.update { it.copy(layers = it.layers.copy(previousPointsVisible = intent.visible)) }
            is MapIntent.Track -> mutable.update { it.copy(layers = it.layers.copy(trackVisible = intent.visible)) }
            is MapIntent.RequestLocation -> {
                val current = mutable.value.location
                if (current == MapLocationState.Locating || current is MapLocationState.AwaitingPermission) return
                if (intent.permissionGranted) locate(intent.action)
                else setLocation(MapLocationState.AwaitingPermission(intent.action))
            }
            is MapIntent.LocationPermissionResult -> {
                val pending = mutable.value.location as? MapLocationState.AwaitingPermission ?: return
                if (intent.granted) locate(pending.action)
                else setLocation(MapLocationState.Failed(LocationOutcome.Reason.PERMISSION_REQUIRED))
            }
            MapIntent.DismissLocation -> dismissLocation()
        }
    }

    private fun locate(action: MapLocationAction) {
        setLocation(MapLocationState.Locating)
        locationJob = scope.launch {
            val outcome = try {
                locateCurrent()
            } catch (_: IOException) {
                setLocation(MapLocationState.SettingsUnavailable)
                return@launch
            }
            val result = when (outcome) {
                is LocationOutcome.Unavailable -> MapLocationState.Failed(outcome.reason)
                is LocationOutcome.Fix -> when (action) {
                    MapLocationAction.Show -> MapLocationState.Position(outcome.sample)
                    is MapLocationAction.Route -> {
                        val route = RouteRequest.create(
                            outcome.sample.latitude, outcome.sample.longitude, action.latitude, action.longitude,
                        )
                        if (route != null) MapLocationState.Route(route)
                        else MapLocationState.Failed(LocationOutcome.Reason.NO_FIX)
                    }
                }
            }
            setLocation(result)
        }
    }

    private fun dismissLocation() {
        locationJob?.cancel()
        locationJob = null
        setLocation(MapLocationState.Idle)
    }

    private fun setLocation(value: MapLocationState) {
        mutable.update { it.copy(location = value) }
    }

    private companion object {
        const val DATE_STATE_KEY = "selected_date"
        const val LAYERS_STATE_KEY = "map_layers"
    }
}

data class MapState(
    val date: LocalDate,
    val locations: LoadState<String>,
    val points: List<MapPoint> = emptyList(),
    val layers: MapLayers = MapLayers(),
    val location: MapLocationState = MapLocationState.Idle,
)

sealed interface MapLocationAction {
    data object Show : MapLocationAction
    data class Route(val latitude: Double, val longitude: Double) : MapLocationAction
}

sealed interface MapLocationState {
    data object Idle : MapLocationState
    data class AwaitingPermission(val action: MapLocationAction) : MapLocationState
    data object SettingsUnavailable : MapLocationState
    data object Locating : MapLocationState
    data class Position(val sample: LocationSample) : MapLocationState
    data class Route(val request: RouteRequest) : MapLocationState
    data class Failed(val reason: LocationOutcome.Reason) : MapLocationState
}

@Serializable
data class MapLayers(val previousPointsVisible: Boolean = true, val trackVisible: Boolean = true)

private data class MapHistory(val points: List<MapPoint>, val json: String)

sealed interface MapIntent {
    data class SelectDate(val date: LocalDate) : MapIntent

    data class PreviousPoints(val visible: Boolean) : MapIntent
    data class Track(val visible: Boolean) : MapIntent
    data class RequestLocation(val action: MapLocationAction, val permissionGranted: Boolean) : MapIntent
    data class LocationPermissionResult(val granted: Boolean) : MapIntent
    data object DismissLocation : MapIntent
    data object Retry : MapIntent
}

internal val MAP_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
