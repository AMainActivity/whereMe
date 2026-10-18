package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.AuthRepository
import javax.inject.Inject

class CheckJwtTokenUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(request: String) = repository.checkWmJwToken(request)
}