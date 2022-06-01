package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject

class GetLocationsFromBd @Inject constructor(
    private val repository: TestsRepository
) {

    operator  suspend fun invoke() = repository.GetLocationsFromBd()
}
