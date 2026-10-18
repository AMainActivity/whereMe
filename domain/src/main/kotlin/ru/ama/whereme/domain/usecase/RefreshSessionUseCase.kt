package ru.ama.whereme.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.repository.AuthRepository
import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject

/** Best-effort validation; transport failures are not a logout decision. */
class RefreshSessionUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val sessions: SessionRepository
) {
    suspend operator fun invoke(
        expectedSession: SessionSnapshot,
        timeoutMillis: Long = 5_000L
    ) {
        try {
            refresh(expectedSession, timeoutMillis)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // A local storage failure is not authorization failure either.
        }
    }

    private suspend fun refresh(expectedSession: SessionSnapshot, timeoutMillis: Long) {
        if (expectedSession.profile.tokenJwt.isBlank() ||
            sessions.current() != expectedSession
        ) return

        val activation = withTimeoutOrNull(timeoutMillis) {
            val response = try {
                auth.checkWmJwToken(expectedSession.profile.tokenJwt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Includes transport and malformed server responses. Keep local credentials.
                return@withTimeoutOrNull null
            }
            run {
                val body = response.mBody
                if (!response.respIsSuccess || body == null || body.error) {
                    null
                } else {
                    when (body.message) {
                        "1" -> true
                        "0" -> false
                        else -> null
                    }
                }
            }
        } ?: return

        currentCoroutineContext().ensureActive()
        sessions.updateActivation(expectedSession, activation)
    }
}
