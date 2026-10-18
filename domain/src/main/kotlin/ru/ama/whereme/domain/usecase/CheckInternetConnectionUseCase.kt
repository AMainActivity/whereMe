package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.NetworkStatus
import javax.inject.Inject

class CheckInternetConnectionUseCase @Inject constructor(
    private val repository: NetworkStatus
) {
    operator fun invoke() = repository.isInternetConnected()
}