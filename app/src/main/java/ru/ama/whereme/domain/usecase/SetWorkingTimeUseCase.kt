package ru.ama.whereme.domain.usecase

import ru.ama.whereme.data.database.SettingsDomainModel
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class SetWorkingTimeUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator fun invoke(dm: SettingsDomainModel) = repository.setWorkingTime(dm)
}
