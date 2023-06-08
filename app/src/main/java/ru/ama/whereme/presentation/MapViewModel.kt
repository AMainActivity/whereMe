package ru.ama.whereme.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.LocationDbByDays
import ru.ama.whereme.domain.usecase.CheckInternetConnectionUseCase
import ru.ama.whereme.domain.usecase.GetGropingDaysUseCase
import ru.ama.whereme.domain.usecase.GetLocationsFromBdByIdUseCase
import ru.ama.whereme.domain.usecase.GetLocationsFromBdUseCase
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocationsFromBdUseCase: GetLocationsFromBdUseCase,
    private val getGropingDaysUseCase: GetGropingDaysUseCase,
    private val getLocationsFromBdByIdUseCase: GetLocationsFromBdByIdUseCase,
    private val checkInternetConnectionUseCase: CheckInternetConnectionUseCase
) : ViewModel() {

    var lld2: LiveData<List<LocationDb>>? = null
    var lldByDay: LiveData<List<LocationDb>>? = null
    private val _ld_days = MutableLiveData<List<LocationDbByDays>>()
    val ld_days: LiveData<List<LocationDbByDays>>
        get() = _ld_days
    init {
        viewModelScope.launch {
            lld2 = getLocationsFromBdUseCase()
            _ld_days.value = getGropingDaysUseCase()
        }
        getDataByDate(getCurrentDate())
    }

    fun isInternetConnected() = checkInternetConnectionUseCase()

    fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    fun getDataByDate(mDate: String) {
        viewModelScope.launch {
            lldByDay = getLocationsFromBdByIdUseCase(mDate)
        }
    }
}