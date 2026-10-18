package ru.ama.whereme.data.settings

import android.content.SharedPreferences
import java.io.IOException

/** Used exclusively by one-time DataStore migrations. */
interface LegacyPreferences {
    fun read(key: String): String?
    fun contains(key: String): Boolean
    fun remove(key: String)
}

class SharedPreferencesLegacyAdapter(private val preferences: SharedPreferences) : LegacyPreferences {
    override fun read(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        throw IOException("Invalid legacy preference type: $key")
    }
    override fun contains(key: String) = preferences.contains(key)
    override fun remove(key: String) {
        if (!preferences.edit().remove(key).commit()) throw IOException("Legacy migration cleanup failed")
    }
}
