package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import ru.ama.whereme.presentation.map.DayLocations
import ru.ama.whereme.domain.usecase.CheckInternetConnectionUseCase
import ru.ama.whereme.domain.usecase.GetGropingDaysUseCase
import ru.ama.whereme.domain.usecase.GetLocationsFromBdByIdUseCase
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    getGropingDaysUseCase: GetGropingDaysUseCase,
    getLocationsFromBdByIdUseCase: GetLocationsFromBdByIdUseCase,
    private val checkInternetConnectionUseCase: CheckInternetConnectionUseCase
) : ViewModel() {
    private val date = MutableStateFlow(getCurrentDate())
    val selectedDate: String get() = date.value
    val lldByDay = date.flatMapLatest { selected ->
        getLocationsFromBdByIdUseCase(selected)
            .map { DayLocations(selected, it) }
            .onStart { emit(DayLocations(selected, emptyList())) }
    }.asLiveData()
    val ld_days = getGropingDaysUseCase().asLiveData()

    fun isInternetConnected() = checkInternetConnectionUseCase()
    fun getCurrentDate(): String = SimpleDateFormat("dd.MM.yyyy").format(Date())
    fun getDataByDate(mDate: String) { date.value = mDate }
}
