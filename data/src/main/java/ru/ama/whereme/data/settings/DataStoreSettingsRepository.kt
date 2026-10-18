package ru.ama.whereme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.ama.whereme.data.di.SettingsStore
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.ThemeMode
import ru.ama.whereme.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @SettingsStore private val store: DataStore<Preferences>
) : SettingsRepository {
    override val settings = store.data.map(SettingsPreferences::read).distinctUntilChanged()
    override val themeMode = store.data.map(SettingsPreferences::theme).distinctUntilChanged()
    override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel): SettingsDomModel {
        val updated = store.edit { p -> SettingsPreferences.write(p, transform(SettingsPreferences.read(p))) }
        return SettingsPreferences.read(updated)
    }
    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { SettingsPreferences.theme(it, mode) } }
}
