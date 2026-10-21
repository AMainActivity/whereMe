package ru.ama.whereme.feature.map

import java.time.format.ResolverStyle
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
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
        MutableStateFlow(stateKeeper.consume(DATE_STATE_KEY, String.serializer())?.let { LocalDate.parse(it, MAP_DATE_FORMAT) } ?: LocalDate.now())
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
        stateKeeper.register(DATE_STATE_KEY, String.serializer()) { date.value.format(MAP_DATE_FORMAT) }
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


    private companion object {
        const val DATE_STATE_KEY = "selected_date"
    }
}

data class MapState(val date: LocalDate, val locations: LoadState<String>)

sealed interface MapIntent {
    data class SelectDate(val date: LocalDate) : MapIntent

    data object Retry : MapIntent
}

internal val MAP_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
