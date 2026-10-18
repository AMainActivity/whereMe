package ru.ama.whereme.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.RequestBody
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

/** Best-effort validation; transport failures are not a logout decision. */
class RefreshSessionUseCase @Inject constructor(
    private val repository: WmRepository
) {
    suspend operator fun invoke(
        expectedSession: SettingsUserInfoDomModel,
        request: RequestBody,
        timeoutMillis: Long = 5_000L
    ) {
        if (expectedSession.tokenJwt.isBlank() ||
            repository.getWmUserInfoSetings() != expectedSession
        ) return

        val activation = withTimeoutOrNull(timeoutMillis) {
            val response = try {
                repository.checkWmJwToken(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Includes transport and malformed server responses. Keep local credentials.
                return@withTimeoutOrNull null
            }
            try {
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
            } finally {
                response.respError?.close()
            }
        } ?: return

        currentCoroutineContext().ensureActive()
        // Called on Main: no suspension between comparison and write. Ignore stale replies.
        if (repository.getWmUserInfoSetings() == expectedSession &&
            expectedSession.isActivate != activation
        ) {
            repository.setWmUserInfoSetings(expectedSession.copy(isActivate = activation))
        }
    }
}
