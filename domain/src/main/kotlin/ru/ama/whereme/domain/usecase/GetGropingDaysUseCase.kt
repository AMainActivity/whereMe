package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.LocationHistoryRepository
import javax.inject.Inject

class GetGropingDaysUseCase @Inject constructor(
    private val repository: LocationHistoryRepository
) {
    operator fun invoke() = repository.getGropingDays()
}