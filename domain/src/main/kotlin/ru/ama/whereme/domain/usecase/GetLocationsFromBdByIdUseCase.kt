package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.LocationHistoryRepository
import javax.inject.Inject

class GetLocationsFromBdByIdUseCase @Inject constructor(
    private val repository: LocationHistoryRepository
) {
    operator fun invoke(mDate: String) = repository.getLocationById(mDate)
}