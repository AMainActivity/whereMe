package ru.ama.whereme.domain.usecase

import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class SaveLocationOnBDUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator suspend fun invoke(lld: LocationLiveData) = repository.saveLocationOnBD(lld)
}
