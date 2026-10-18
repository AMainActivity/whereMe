package ru.ama.whereme.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import java.io.IOException
import ru.ama.whereme.domain.entity.SettingsDomModel

class SettingsMigration(private val legacy: LegacyPreferences) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SettingsPreferences.VERSION] != 1 || legacy.contains("worktime")

    override suspend fun migrate(currentData: Preferences): Preferences {
        val version = currentData[SettingsPreferences.VERSION]
        if (version != null && version != 1) throw IOException("Unsupported settings format; data retained")
        if (currentData[SettingsPreferences.VERSION] == 1) {
            SettingsPreferences.read(currentData)
            return currentData
        }
        val previous = legacy.read("worktime")?.let(LegacyJson::settings) ?: SettingsDomModel.DEFAULT
        return currentData.toMutablePreferences().apply {
            SettingsPreferences.write(this, previous, onlyMissing = true)
            SettingsPreferences.read(this) // Do not commit an invalid combination of old/new fields.
            this[SettingsPreferences.VERSION] = 1
        }
    }
    override suspend fun cleanUp() { legacy.remove("worktime") }
}
