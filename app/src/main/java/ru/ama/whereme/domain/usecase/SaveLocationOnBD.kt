package ru.ama.whereme.domain.usecase

import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject

class SaveLocationOnBD @Inject constructor(
    private val repository: TestsRepository
) {

    operator suspend fun invoke(lld: LocationLiveData) = repository.saveLocationOnBD(lld)
}
