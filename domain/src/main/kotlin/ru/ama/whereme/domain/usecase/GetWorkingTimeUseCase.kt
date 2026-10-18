package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.SettingsRepository
import javax.inject.Inject

class GetWorkingTimeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke() = repository.getWorkingTime()
}
