package ru.ama.whereme.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

interface SessionRepository {
    val session: Flow<SessionSnapshot>
    suspend fun current(): SessionSnapshot = session.first()
    suspend fun signIn(profile: SettingsUserInfoDomModel)
    suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean
    suspend fun updateActivation(expected: SessionSnapshot, active: Boolean): Boolean
    /** Serializes a short local action with login/logout, never a network request. */
    suspend fun <T> withCurrent(expected: SessionSnapshot, action: suspend () -> T): T?
}
