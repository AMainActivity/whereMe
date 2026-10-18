package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject

class SetJwTokenUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    operator fun invoke(set: SettingsUserInfoDomModel) = repository.setWmUserInfoSetings(set)
}
