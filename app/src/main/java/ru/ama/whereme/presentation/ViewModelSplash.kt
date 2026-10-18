package ru.ama.whereme.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ViewModelSplash @Inject constructor() : ViewModel() {
    // Local startup must never depend on the authentication server.
    val canStart: LiveData<Unit> = MutableLiveData(Unit)
}
