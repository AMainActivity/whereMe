package ru.ama.whereme.presentation

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MaViewModel @Inject constructor(
    private val getLocationUseCase: GetLocationUseCase,
    private val getLocation2UseCase: GetLocation2UseCase,
    private val getLastLocationUseCase: GetLastLocationUseCase,
    private val saveLocationOnBDUseCase: SaveLocationOnBDUseCase,
    private val stopLocationsUpdateUseCase: StopLocationsUpdateUseCase,
    private val runWorkerUpdateUseCase: RunWorkerUpdateUseCase
) : ViewModel() {



    init {
     
    }

fun startLocationService()
{
    viewModelScope.launch {
        runWorkerUpdateUseCase(10)
    }
}


    companion object {}
}