package ru.ama.whereme.data.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
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

    @Test fun androidFileStoragePersistsAndDurablyLogsOut() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val expected = SettingsUserInfoDomModel("synthetic-token", 12, 34, null, null, true)
        val cipher = SessionCipher(AndroidSessionKeyProvider())
        val path = File(context.noBackupFilesDir, "$id.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope, produceFile = { path })
            val repository = DataStoreSessionRepository(store, cipher, ru.ama.whereme.data.database.LegacyHistoryAssignment {})
            assertEquals(SettingsUserInfoDomModel.EMPTY, repository.current().profile)
            repository.signIn(expected)
            val session = repository.current()
            assertEquals(expected, session.profile)
            assertFalse(path.readBytes().toString(Charsets.ISO_8859_1).contains(expected.tokenJwt))
            scope.coroutineContext[Job]?.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = DataStoreSessionRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { path }), cipher, ru.ama.whereme.data.database.LegacyHistoryAssignment {})
            assertEquals(session, reopened.current())
            assertTrue(reopened.clearIfCurrent(session))
            val signedOut = reopened.current()
            scope.coroutineContext[Job]?.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val loggedOut = DataStoreSessionRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { path }), cipher, ru.ama.whereme.data.database.LegacyHistoryAssignment {})
            assertEquals(signedOut, loggedOut.current())
            assertEquals(SettingsUserInfoDomModel.EMPTY, loggedOut.current().profile)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            path.delete()
        }
    }
}
