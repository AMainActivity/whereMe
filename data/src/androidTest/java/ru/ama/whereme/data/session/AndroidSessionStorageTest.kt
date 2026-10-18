package ru.ama.whereme.data.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.ama.whereme.data.settings.SharedPreferencesLegacyAdapter
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

/** Runs in the isolated library test application, not the installed whereMe UID/database. */
@RunWith(AndroidJUnit4::class)
class AndroidSessionStorageTest {
    @Test fun androidKeystoreRoundTripSurvivesProviderRecreation() {
        val plain = "synthetic-keystore-test".toByteArray()
        val first = SessionCipher(AndroidSessionKeyProvider())
        val encrypted = first.encrypt(plain)
        val second = SessionCipher(AndroidSessionKeyProvider())
        assertArrayEquals(plain, second.decrypt(encrypted))
        assertFalse(first.encrypt(plain).contentEquals(encrypted))
    }

    @Test fun androidFileStorageMigratesAndDurablyLogsOut() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val preferences = context.getSharedPreferences("migration-test-$id", Context.MODE_PRIVATE)
        val expected = SettingsUserInfoDomModel("synthetic-token", 12, 34, null, null, true)
        assertTrue(preferences.edit().putString("jwt", Gson().toJson(expected)).commit())
        val legacy = SharedPreferencesLegacyAdapter(preferences)
        val cipher = SessionCipher(AndroidSessionKeyProvider())
        val path = File(context.noBackupFilesDir, "$id.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(migrations = listOf(SessionMigration(legacy, cipher)),
                scope = scope, produceFile = { path })
            val repository = DataStoreSessionRepository(store, cipher)
            val session = repository.current()
            assertEquals(expected, session.profile)
            assertFalse(preferences.contains("jwt"))
            assertFalse(path.readBytes().toString(Charsets.ISO_8859_1).contains(expected.tokenJwt))
            assertTrue(repository.clearIfCurrent(session))
            assertEquals(SettingsUserInfoDomModel.EMPTY, repository.current().profile)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            preferences.edit().clear().commit()
            path.delete()
        }
    }
}
