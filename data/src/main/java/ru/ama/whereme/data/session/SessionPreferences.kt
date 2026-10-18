package ru.ama.whereme.data.session

import androidx.datastore.preferences.core.*
import com.google.gson.Gson
import java.io.IOException
import java.util.UUID
import ru.ama.whereme.data.settings.LegacyJson
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

internal class SessionPreferences(private val cipher: SessionCipher) {
    fun read(p: Preferences): SessionSnapshot {
        val generation = p[GENERATION] ?: throw IOException("Missing session generation")
        val encrypted = p[PROFILE] ?: throw IOException("Missing encrypted session")
        return SessionSnapshot(LegacyJson.profile(cipher.decrypt(encrypted).toString(Charsets.UTF_8)), generation)
    }
    fun write(p: MutablePreferences, profile: SettingsUserInfoDomModel, generation: String = UUID.randomUUID().toString()) {
        p[PROFILE] = cipher.encrypt(Gson().toJson(profile).toByteArray(Charsets.UTF_8))
        p[GENERATION] = generation
        p[VERSION] = 1
    }
    companion object {
        val VERSION = intPreferencesKey("migration_version")
        private val PROFILE = byteArrayPreferencesKey("encrypted_profile")
        private val GENERATION = stringPreferencesKey("generation")
    }
}
