package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class SetWorkingTimeUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator fun invoke(dm: SettingsDomModel) = repository.setWorkingTime(dm)
}
