package ru.ama.whereme.presentation

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MaViewModel @Inject constructor(
    private val getLastLocationUseCase: GetLastLocationUseCase,
    private val stopLocationsUpdateUseCase: StopLocationsUpdateUseCase,
    private val runAlarmUseCase: RunAlarmUseCase
) : ViewModel() {



    init {

    }

    fun startLocationService() {
        viewModelScope.launch {
            runAlarmUseCase(10)
        }
    }


    companion object {}
}