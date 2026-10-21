package ru.ama.whereme.domain.usecase

import javax.inject.Inject
import ru.ama.whereme.domain.entity.JsonEntity
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.repository.AuthRepository
import ru.ama.whereme.domain.repository.SessionRepository

class SignOut
@Inject
constructor(private val auth: AuthRepository, private val sessions: SessionRepository) {
    suspend operator fun invoke(expected: SessionSnapshot): Boolean {
        val response = auth.logOut(expected.profile.tokenJwt)
        val body = response.mBody
        if (!response.respIsSuccess || body == null || body.error || body.message != JsonEntity.POSITIVE_MESSAGE)
            return false
        // An old response must never remove a newer login.
        return sessions.clearIfCurrent(expected)
    }
}
