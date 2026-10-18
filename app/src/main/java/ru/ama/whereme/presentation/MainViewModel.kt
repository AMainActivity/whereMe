package ru.ama.whereme.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import ru.ama.whereme.domain.usecase.RefreshSessionUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val refreshSessionUseCase: RefreshSessionUseCase
) : ViewModel() {
    init {
        val session = getJwTokenUseCase()
        if (session.tokenJwt.isNotBlank()) {
            viewModelScope.launch {
                refreshSessionUseCase(session)
            }
        }
    }

    fun checkIsActivate() = getJwTokenUseCase().isActivate
}
