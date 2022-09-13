package ru.ama.whereme.presentationn

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

class ViewModelSplash @Inject constructor(
) : ViewModel() {

    init {
        /*  val d1 = viewModelScope.async(Dispatchers.IO) {
              loadTestsFromNetUseCase()
          }

          viewModelScope.launch {
              val f = d1.await()
              _canStart.value = Unit
          }*/
    }

    private val _canStart = MutableLiveData<Unit>()
    val canStart: LiveData<Unit>
        get() = _canStart

    companion object {}
}
