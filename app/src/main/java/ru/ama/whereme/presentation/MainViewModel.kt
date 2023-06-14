package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import javax.inject.Inject

class MainViewModel @Inject constructor(
    private val getJwTokenUseCase: GetJwTokenUseCase
) : ViewModel() {
    fun checkIsActivate() = getJwTokenUseCase().isActivate
}