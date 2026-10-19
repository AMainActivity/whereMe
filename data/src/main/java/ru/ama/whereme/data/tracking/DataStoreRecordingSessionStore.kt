package ru.ama.whereme.data.tracking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import ru.ama.whereme.data.di.RecordingStore
import ru.ama.whereme.domain.tracking.RecordingSession
import ru.ama.whereme.domain.tracking.RecordingSessionStore
import ru.ama.whereme.domain.tracking.TrackingMode

/** Operational state stays in noBackupFilesDir: restoring a backup must not start recording. */
@Singleton
class DataStoreRecordingSessionStore @Inject constructor(
    @param:RecordingStore private val store: DataStore<Preferences>
) : RecordingSessionStore {
    override suspend fun read(): RecordingSession? = decode(store.data.first())

    override suspend fun write(session: RecordingSession?) {
        store.edit { p ->
            decode(p) // Never overwrite an unknown or damaged format with defaults.
            p[VERSION] = 1
            if (session == null) {
                p.remove(ID); p.remove(MODE); p.remove(NEXT)
            } else {
                p[ID] = session.id; p[MODE] = session.mode.name; p[NEXT] = session.nextAtMillis
            }
        }
    }

    private fun decode(p: Preferences): RecordingSession? {
        if (p.asMap().isEmpty()) return null
        if (p[VERSION] != 1) throw IOException("Unsupported recording state format")
        val id = p[ID]
        val mode = p[MODE]
        val next = p[NEXT]
        if (id == null && mode == null && next == null) return null
        val kind = TrackingMode.entries.firstOrNull { it.name == mode }
        if (id.isNullOrBlank() || kind == null || next == null || next <= 0) {
            throw IOException("Invalid recording state; stored data retained")
        }
        return RecordingSession(id, kind, next)
    }

    private companion object {
        val VERSION = intPreferencesKey("version")
        val ID = stringPreferencesKey("recording_id")
        val MODE = stringPreferencesKey("mode")
        val NEXT = longPreferencesKey("next_at_millis")
    }
}
