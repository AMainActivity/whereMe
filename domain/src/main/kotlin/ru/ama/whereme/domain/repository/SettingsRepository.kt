package ru.ama.whereme.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.ThemeMode

interface SettingsRepository {
    val settings: Flow<SettingsDomModel>
    val themeMode: Flow<ThemeMode>
    suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel): SettingsDomModel
    suspend fun setThemeMode(mode: ThemeMode)
}
