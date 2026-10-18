package ru.ama.whereme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import androidx.datastore.preferences.core.*
import java.io.File
import java.io.IOException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.ama.whereme.data.session.*
import ru.ama.whereme.domain.entity.*

class StorageRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()
    private val profile = SettingsUserInfoDomModel("synthetic-token", 12, 34, "Тест", "https://example.com/profile", true)
    private val previous = SettingsDomModel(listOf("0", "1", "0", "1", "0", "1", "0"), "06:15", "22:45", 123, 76, 88, 321, true)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = SessionCipher { key }

    private fun store(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        // The Android FileStorage uses SDK_INT=0 in local JVM tests. Okio supplies portable atomic replacement.
        return PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.toOkioPath() }),
            scope = scope)
    }
    private fun file() = File(temporary.newFolder(), "test.preferences_pb")
    private fun settings(file: File) = DataStoreSettingsRepository(store(file))
    private fun sessions(file: File, encryption: SessionCipher = cipher) = DataStoreSessionRepository(store(file), encryption)
    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        scopes.clear()
    }
    @After fun close() = runBlocking { closeStores() }
    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected storage failure") } catch (_: IOException) { }
    }

    @Test fun newInstallHasSafeDefaultsWithoutNetwork() = runBlocking {
        val settings = settings(file())
        assertEquals(SettingsDomModel.DEFAULT, settings.settings.first())
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        val current = sessions(file()).current()
        assertEquals(SettingsUserInfoDomModel.EMPTY, current.profile)
        assertTrue(current.generation.isNotBlank())
    }

    @Test fun concurrentFieldUpdatesDoNotLoseEachOther() = runBlocking {
        val repository = settings(file())
        coroutineScope {
            repeat(20) {
                launch { repository.updateSettings { it.copy(minDist = it.minDist + 1) } }
                launch { repository.updateSettings { it.copy(accuracy = it.accuracy + 1) } }
            }
        }
        assertEquals(40, repository.settings.first().minDist)
        assertEquals(70, repository.settings.first().accuracy)
    }

    @Test fun failedUpdateDoesNotChangePersistedSettings() = runBlocking {
        val repository = settings(file())
        try { repository.updateSettings { throw IllegalArgumentException("injected transform failure") }; fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(SettingsDomModel.DEFAULT, repository.settings.first())
    }

    @Test fun eachThemeSurvivesReopeningAndDoesNotEnableSchedule() = runBlocking {
        val path = file()
        for (theme in ThemeMode.entries) {
            settings(path).setThemeMode(theme)
            closeStores()
            val repository = settings(path)
            assertEquals(theme, repository.themeMode.first())
            assertFalse(repository.settings.first().isEnable)
            closeStores()
        }
    }

    @Test fun reloginWithSameCredentialsInvalidatesPreviousGeneration() = runBlocking {
        val repository = sessions(file())
        repository.signIn(profile)
        val first = repository.current()
        repository.signIn(profile)
        val second = repository.current()
        assertEquals(first.profile, second.profile)
        assertNotEquals(first.generation, second.generation)
        assertFalse(repository.clearIfCurrent(first))
        assertFalse(repository.updateActivation(first, false))
        assertEquals(second, repository.current())
    }

    @Test fun activationKeepsGenerationAndLogoutChangesIt() = runBlocking {
        val repository = sessions(file())
        repository.signIn(profile)
        val expected = repository.current()
        assertTrue(repository.updateActivation(expected, false))
        val refreshed = repository.current()
        assertEquals(expected.generation, refreshed.generation)
        assertFalse(refreshed.profile.isActivate)
        assertTrue(repository.clearIfCurrent(refreshed))
        assertNotEquals(expected.generation, repository.current().generation)
        assertEquals(SettingsUserInfoDomModel.EMPTY, repository.current().profile)
    }

    @Test fun activationRefreshDoesNotInvalidateCurrentLogout() = runBlocking {
        val repository = sessions(file())
        repository.signIn(profile)
        val beforeRefresh = repository.current()
        assertTrue(repository.updateActivation(beforeRefresh, false))
        assertTrue(repository.clearIfCurrent(beforeRefresh))
        assertEquals(SettingsUserInfoDomModel.EMPTY, repository.current().profile)
    }

    @Test fun activationUpdateComparesLatestStoredValue() = runBlocking {
        val repository = sessions(file())
        repository.signIn(profile)
        val expected = repository.current()
        assertTrue(repository.updateActivation(expected, false))
        assertTrue(repository.updateActivation(expected, true))
        assertTrue(repository.current().profile.isActivate)
    }

    @Test fun sessionLeaseSerializesLocalAckWithSignIn() = runBlocking {
        withTimeout(5_000) {
            val repository = sessions(file())
            repository.signIn(profile)
            val expected = repository.current()
            val acquired = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val ack = async { repository.withCurrent(expected) { acquired.complete(Unit); release.await(); 42 } }
            acquired.await()
            val started = CompletableDeferred<Unit>()
            val login = async { started.complete(Unit); repository.signIn(profile.copy(tokenJwt = "new-token")) }
            started.await()
            yield()
            assertFalse(login.isCompleted)
            release.complete(Unit)
            assertEquals(42, ack.await())
            login.await()
            assertEquals("new-token", repository.current().profile.tokenJwt)
            assertEquals(null, repository.withCurrent(expected) { fail("Stale ACK action"); 0 })
        }
    }

    @Test fun corruptCiphertextIsNotReplacedWithAnonymousSession() = runBlocking {
        val path = file()
        val datastore = store(path)
        val repository = DataStoreSessionRepository(datastore, cipher)
        repository.signIn(profile)
        val broken = byteArrayOf(1, 2, 3)
        datastore.edit { it[byteArrayPreferencesKey("encrypted_profile")] = broken }
        expectFailure { repository.current() }
        assertArrayEquals(broken, datastore.data.first()[byteArrayPreferencesKey("encrypted_profile")])
    }

    @Test fun unknownSettingsVersionIsNotOverwrittenDuringDowngrade() = runBlocking {
        val path = file()
        store(path).edit { SettingsPreferences.write(it, previous); it[SettingsPreferences.VERSION] = 2 }
        closeStores()
        expectFailure { settings(path).settings.first() }
        closeStores()
        val persisted = store(path).data.first()
        assertEquals(2, persisted[SettingsPreferences.VERSION])
        assertEquals(previous.minDist, persisted[intPreferencesKey("min_distance")])
    }

    @Test fun corruptPreferencesFileIsRetainedWithoutDefaultReplacement() = runBlocking {
        val path = file()
        val raw = byteArrayOf(1, 2, 3)
        path.writeBytes(raw)
        expectFailure { settings(path).settings.first() }
        assertArrayEquals(raw, path.readBytes())
    }

    @Test fun unknownSessionVersionIsNotOverwrittenDuringDowngrade() = runBlocking {
        val path = file()
        val codec = SessionPreferences(cipher)
        val original = store(path)
        original.edit { codec.write(it, profile); it[SessionPreferences.VERSION] = 2 }
        val before = original.data.first()[byteArrayPreferencesKey("encrypted_profile")]
        closeStores()
        expectFailure { sessions(path).current() }
        closeStores()
        val persisted = store(path).data.first()
        assertEquals(2, persisted[SessionPreferences.VERSION])
        assertArrayEquals(before, persisted[byteArrayPreferencesKey("encrypted_profile")])
    }

    @Test fun existingVersionOneSettingsRemainReadableWithoutMigration() = runBlocking {
        val path = file()
        store(path).edit { p ->
            p[intPreferencesKey("migration_version")] = 1
            p[intPreferencesKey("days_mask")] = 42
            p[stringPreferencesKey("start")] = "06:15"
            p[stringPreferencesKey("end")] = "22:45"
            p[intPreferencesKey("min_distance")] = 123
            p[intPreferencesKey("accuracy")] = 76
            p[intPreferencesKey("wait_seconds")] = 88
            p[intPreferencesKey("period_seconds")] = 321
            p[booleanPreferencesKey("schedule_enabled")] = true
        }
        closeStores()
        val repository = settings(path)
        assertEquals(previous, repository.settings.first())
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        repository.setThemeMode(ThemeMode.DARK)
        closeStores()
        assertEquals(previous, settings(path).settings.first())
    }

    @Test fun existingEncryptedVersionOneSessionKeepsProfileAndGeneration() = runBlocking {
        val path = file()
        // Independent fixture of the JSON emitted by the previous installed version.
        val raw = """{"tokenJwt":"synthetic-token","posId":12,"famId":34,"name":"Тест","url":"https://example.com/profile","isActivate":true}"""
        store(path).edit { p ->
            p[intPreferencesKey("migration_version")] = 1
            p[stringPreferencesKey("generation")] = "previous-login-generation"
            p[byteArrayPreferencesKey("encrypted_profile")] = cipher.encrypt(raw.toByteArray())
        }
        closeStores()
        val current = sessions(path).current()
        assertEquals(profile, current.profile)
        assertEquals("previous-login-generation", current.generation)
    }

    @Test fun loginFailureRetainsPreviouslyPersistedSession() = runBlocking {
        val path = file()
        val original = sessions(path)
        original.signIn(profile)
        val expected = original.current()
        closeStores()
        val broken = sessions(path, SessionCipher { throw IOException("injected key failure") })
        expectFailure { broken.signIn(profile.copy(tokenJwt = "replacement")) }
        closeStores()
        assertEquals(expected, sessions(path).current())
    }

    @Test fun unknownFormatCannotBeOverwrittenBySettingsOrThemeWrites() = runBlocking {
        val path = file()
        store(path).edit { SettingsPreferences.write(it, previous); it[SettingsPreferences.VERSION] = 2 }
        closeStores()
        val repository = settings(path)
        expectFailure { repository.updateSettings { SettingsDomModel.DEFAULT } }
        expectFailure { repository.setThemeMode(ThemeMode.DARK) }
        closeStores()
        val raw = store(path).data.first()
        assertEquals(2, raw[SettingsPreferences.VERSION])
        assertEquals(123, raw[intPreferencesKey("min_distance")])
    }

    @Test fun missingSessionFieldsDoNotProduceAnonymousSession() = runBlocking {
        val path = file()
        store(path).edit { it[SessionPreferences.VERSION] = 1 }
        closeStores()
        val repository = sessions(path)
        expectFailure { repository.current() }
        closeStores()
        assertEquals(1, store(path).data.first().asMap().size)
    }

    @Test fun logoutRemainsDurableAfterReopeningStore() = runBlocking {
        val path = file()
        val repository = sessions(path)
        repository.signIn(profile)
        val expected = repository.current()
        assertTrue(repository.clearIfCurrent(expected))
        val signedOut = repository.current()
        closeStores()
        assertEquals(signedOut, sessions(path).current())
        assertEquals(SettingsUserInfoDomModel.EMPTY, signedOut.profile)
        assertNotEquals(expected.generation, signedOut.generation)
    }


    @Test fun explicitSignInCanReplaceSessionAfterKeyReplacement() = runBlocking {
        val path = file()
        val original = sessions(path)
        original.signIn(profile)
        val oldGeneration = original.current().generation
        closeStores()
        val replacementKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val recovered = sessions(path, SessionCipher { replacementKey })
        expectFailure { recovered.current() }
        val newProfile = profile.copy(tokenJwt = "new-explicit-login")
        recovered.signIn(newProfile)
        assertEquals(newProfile, recovered.current().profile)
        assertNotEquals(oldGeneration, recovered.current().generation)
        closeStores()
        assertEquals(newProfile, sessions(path, SessionCipher { replacementKey }).current().profile)
    }

}
