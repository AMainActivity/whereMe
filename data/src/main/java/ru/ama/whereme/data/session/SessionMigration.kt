package ru.ama.whereme.data.session

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import java.io.IOException
import ru.ama.whereme.data.settings.LegacyJson
import ru.ama.whereme.data.settings.LegacyPreferences
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

class SessionMigration(private val legacy: LegacyPreferences, cipher: SessionCipher) : DataMigration<Preferences> {
    private val codec = SessionPreferences(cipher)
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SessionPreferences.VERSION] != 1 || legacy.contains("jwt")

    override suspend fun migrate(currentData: Preferences): Preferences {
        val version = currentData[SessionPreferences.VERSION]
        if (version != null && version != 1) throw IOException("Unsupported session format; data retained")
        if (currentData[SessionPreferences.VERSION] == 1) {
            codec.read(currentData) // Validate before deleting any remaining legacy credentials.
            return currentData
        }
        val profile = legacy.read("jwt")?.let(LegacyJson::profile) ?: SettingsUserInfoDomModel.EMPTY
        return currentData.toMutablePreferences().apply { codec.write(this, profile) }
    }
    override suspend fun cleanUp() { legacy.remove("jwt") }
}
