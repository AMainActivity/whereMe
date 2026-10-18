package ru.ama.whereme.data.session

import androidx.datastore.preferences.core.*
import java.io.IOException
import java.util.UUID
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

internal class SessionPreferences(private val cipher: SessionCipher) {
    fun read(p: Preferences): SessionSnapshot {
        validateVersion(p)
        // An empty file is a new, signed-out installation; no key or write is needed to read it.
        if (p.asMap().isEmpty()) return SessionSnapshot(SettingsUserInfoDomModel.EMPTY, "empty-session")
        val generation = p[GENERATION] ?: throw IOException("Missing session generation")
        val encrypted = p[PROFILE] ?: throw IOException("Missing encrypted session")
        return SessionSnapshot(SessionProfileCodec.decode(cipher.decrypt(encrypted).toString(Charsets.UTF_8)), generation)
    }
    fun write(p: MutablePreferences, profile: SettingsUserInfoDomModel, generation: String = UUID.randomUUID().toString()) {
        // Explicit sign-in may replace credentials after a key reset; conditional edits read/verify the old session first.
        validateVersion(p)
        p[PROFILE] = cipher.encrypt(SessionProfileCodec.encode(profile).toByteArray(Charsets.UTF_8))
        p[GENERATION] = generation
        p[VERSION] = 1
    }
    private fun validateVersion(p: Preferences) {
        if (p.asMap().isNotEmpty() && p[VERSION] != 1) {
            throw IOException("Unsupported or missing session format; stored data retained")
        }
    }
    companion object {
        // Keep the established on-disk key so the existing DataStore installation remains readable.
        val VERSION = intPreferencesKey("migration_version")
        private val PROFILE = byteArrayPreferencesKey("encrypted_profile")
        private val GENERATION = stringPreferencesKey("generation")
    }
}
