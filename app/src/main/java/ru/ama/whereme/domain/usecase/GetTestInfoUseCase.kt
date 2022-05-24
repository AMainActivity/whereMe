package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject

class GetTestInfoUseCase @Inject constructor(
    private val repository: TestsRepository
) {

    operator fun invoke() = repository.getTestInfo()
}
