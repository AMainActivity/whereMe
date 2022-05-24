package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject

class GetQuestionsListUseCase @Inject constructor(
    private val repository: TestsRepository
) {

    operator fun invoke(testId:Int,limit:Int) = repository.getQuestionsInfoList(testId,limit)
}
