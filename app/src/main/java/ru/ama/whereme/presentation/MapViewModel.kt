package ru.ama.whereme.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.ama.whereme.data.di.DefaultDispatcher
import ru.ama.whereme.data.network.model.LocationDto
import ru.ama.whereme.presentation.map.DayLocations
import ru.ama.whereme.domain.repository.NetworkStatus
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    history: LocationHistoryRepository,
    private val network: NetworkStatus,
    private val savedState: SavedStateHandle,
    private val json: Json,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val date = savedState.getStateFlow("selected_date", SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date()))
    private val retry = MutableStateFlow(0)
    val selectedDate: String get() = date.value
    val locations = combine(date, retry) { selected, _ -> selected }.flatMapLatest { selected ->
        history.getLocationById(selected)
            .mapLatest { rows ->
                withContext(defaultDispatcher) {
                    DayLocations(selected, json.encodeToString(rows.map(LocationDto::from)))
                }
            }
            .asLoadState()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadState.Loading)

    fun retryLocations() { retry.value++ }
    fun isInternetConnected() = network.isInternetConnected()
    fun getDataByDate(value: String) { savedState["selected_date"] = value }
}
