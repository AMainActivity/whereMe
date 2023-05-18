package ru.ama.whereme.domain.usecase

import okhttp3.RequestBody
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class CheckKodUseCase @Inject constructor(
    private val repository: WmRepository
) {

    suspend operator fun invoke(request: RequestBody) = repository.checkKod(request)
}
