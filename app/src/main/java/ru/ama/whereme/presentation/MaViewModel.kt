package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import javax.inject.Inject

class MaViewModel @Inject constructor(
    private val getJwTokenUseCase: GetJwTokenUseCase
) : ViewModel() {
    fun checkIsActivate() = getJwTokenUseCase().isActivate
}