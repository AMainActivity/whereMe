package ru.ama.whereme.feature.map

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.state.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapComponent(
    context: ComponentContext,
    history: LocationHistoryRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ComponentContext by context {
    private val scope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val date =
        MutableStateFlow(stateKeeper.consume("selected_date", String.serializer()) ?: today())
    private val retry = MutableStateFlow(0)
    private val json = Json { explicitNulls = false }
    private val mutable = MutableStateFlow(MapState(date.value, LoadState.Loading))
    val state = mutable.asStateFlow()
    private val observations =
        combine(date, retry) { selected, _ -> selected }
            .flatMapLatest { selected ->
                history
                    .getLocationById(selected)
                    .mapLatest { rows ->
                        withContext(defaultDispatcher) {
                            json.encodeToString(rows.map(MapPoint::from))
                        }
                    }
                    .asLoadState()
                    .map { MapState(selected, it) }
            }

    init {
        stateKeeper.register("selected_date", String.serializer()) { date.value }
        scope.launch {
            // Retaining the tab must not keep preparing map data while another tab is visible
            // or the Activity is in the background.
            lifecycle.repeatOnLifecycle {
                observations.collect { mutable.value = it }
            }
        }
    }

    fun accept(intent: MapIntent) {
        when (intent) {
            is MapIntent.SelectDate -> date.value = intent.date
            MapIntent.Retry -> retry.value++
        }
    }

    private fun today() = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date())
}

data class MapState(val date: String, val locations: LoadState<String>)

sealed interface MapIntent {
    data class SelectDate(val date: String) : MapIntent

    data object Retry : MapIntent
}
