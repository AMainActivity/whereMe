package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class SetJwTokenUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator fun invoke(jwt:String) = repository.setWmJwToken(jwt)
}
