package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class RunAlarmUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator fun invoke(timeInterval: Long) = repository.runAlarm(timeInterval)
}
