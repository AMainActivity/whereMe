package ru.ama.whereme.domain.usecase

import okhttp3.RequestBody
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class CheckJwtTokenUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator suspend fun invoke(request : RequestBody) = repository.checkWmJwToken(request)
}