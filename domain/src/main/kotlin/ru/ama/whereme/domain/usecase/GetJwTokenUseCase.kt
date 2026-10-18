package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject

class GetJwTokenUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    operator fun invoke() = repository.getWmUserInfoSetings()
}