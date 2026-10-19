package ru.ama.whereme.data.tracking

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.ama.whereme.domain.tracking.RecordingSession
import ru.ama.whereme.domain.tracking.TrackingMode

class RecordingSessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()
    private fun store(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        return PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.toOkioPath() }), scope = scope)
    }
    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }; scopes.clear()
    }
    @After fun close() = runBlocking { closeStores() }

    @Test fun newInstallDoesNotStartRecording() = runBlocking {
        assertNull(DataStoreRecordingSessionStore(store(File(temporary.newFolder(), "recording.preferences_pb"))).read())
    }

    @Test fun bothModesAndTheirExactAlarmDeadlineSurviveProcessReopening() = runBlocking {
        val file = File(temporary.newFolder(), "recording.preferences_pb")
        for (mode in TrackingMode.entries) {
            val expected = RecordingSession("session-${mode.name}", mode, 1_792_400_015_000)
            DataStoreRecordingSessionStore(store(file)).write(expected)
            closeStores()
            assertEquals(expected, DataStoreRecordingSessionStore(store(file)).read())
            closeStores()
        }
    }

    @Test fun stopIsDurableAndCannotRestoreThePreviousRecording() = runBlocking {
        val file = File(temporary.newFolder(), "recording.preferences_pb")
        val repository = DataStoreRecordingSessionStore(store(file))
        repository.write(RecordingSession("active", TrackingMode.MANUAL, 1_792_400_015_000))
        repository.write(null)
        closeStores()
        assertNull(DataStoreRecordingSessionStore(store(file)).read())
    }

    @Test fun unsupportedFormatIsRetainedInsteadOfResettingOrEnablingRecording() = runBlocking {
        val file = File(temporary.newFolder(), "recording.preferences_pb")
        val data = store(file)
        val expected = data.edit { it[intPreferencesKey("version")] = 99 }
        val repository = DataStoreRecordingSessionStore(data)
        try { repository.read(); fail("Unknown format must fail") } catch (_: IOException) { }
        try { repository.write(null); fail("Unknown format must be retained") } catch (_: IOException) { }
        assertEquals(expected, data.data.first())
    }

    @Test fun partialSessionCannotSilentlyBecomeStoppedOrAnActiveDefault() = runBlocking {
        val data = store(File(temporary.newFolder(), "recording.preferences_pb"))
        val expected = data.edit {
            it[intPreferencesKey("version")] = 1
            it[stringPreferencesKey("recording_id")] = "partial"
        }
        val repository = DataStoreRecordingSessionStore(data)
        try { repository.read(); fail("Incomplete state must fail") } catch (_: IOException) { }
        assertEquals(expected, data.data.first())
    }
}
