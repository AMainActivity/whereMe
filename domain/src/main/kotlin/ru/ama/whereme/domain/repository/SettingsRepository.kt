package ru.ama.whereme.domain.repository

import ru.ama.whereme.domain.entity.*

interface SettingsRepository {
    fun getWorkingTime(): SettingsDomModel
    fun setWorkingTime(dm: SettingsDomModel)
}
