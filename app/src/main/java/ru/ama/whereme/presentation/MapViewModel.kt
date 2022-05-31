package ru.ama.whereme.presentation

import android.location.Location
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocation: GetLocation,
    private val getLocation2: GetLocation2,
    private val getLastLocation: GetLastLocation,
    private val saveLocationOnBD: SaveLocationOnBD,
    private val stopLocationsUpdateUseCase: StopLocationsUpdateUseCase
) : ViewModel() {

     var lld2 : LiveData<Location?>?=null

    init {
      
        val sd=viewModelScope.async {
            lld2=getLastLocation() }
viewModelScope.launch {
    sd.await()
        lld2 = getLocation2()
}
		viewModelScope.launch {
			delay(1000*120)
		stopLocationsUpdateUseCase()
		}
    }






    private val _testInfo = MutableLiveData<Location>()
    val testInfo: LiveData<Location>
        get() = _testInfo

    companion object {}
}