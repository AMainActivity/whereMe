package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class CheckServiceUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator fun invoke(serviceClass: Class<*>) = repository.isMyServiceRunning(serviceClass)
}
