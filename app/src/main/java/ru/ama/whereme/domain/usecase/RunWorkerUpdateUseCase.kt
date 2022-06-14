package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class RunWorkerUpdateUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator suspend fun invoke(timeInterval:Long) = repository.runWorker(timeInterval)
}
