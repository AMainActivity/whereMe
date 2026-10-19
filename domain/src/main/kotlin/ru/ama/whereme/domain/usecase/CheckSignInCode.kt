package ru.ama.whereme.domain.usecase

import java.io.IOException
import javax.inject.Inject
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.AuthRepository

/** A rejected code is distinct from a transport/storage failure. */
class CheckSignInCode @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(code: String): SettingsUserInfoDomModel? {
        val response = auth.checkKod(code)
        if (!response.respIsSuccess) throw IOException("Code check HTTP ${response.respCode}")
        val body = response.mBody ?: throw IOException("Empty code check response")
        if (body.message == "0") return null
        if (body.error || body.message != "1" || body.tokenJwt.isBlank())
            throw IOException("Invalid code check response")
        return SettingsUserInfoDomModel(
            body.tokenJwt,
            body.posId,
            body.famId,
            body.name,
            body.url,
            body.isActivate == 1,
        )
    }
}
