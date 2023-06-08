package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class StopLocationsUpdateUseCase @Inject constructor(
    private val repository: WmRepository
) {
    suspend operator fun invoke() = repository.stopData()
}
