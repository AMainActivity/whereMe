package ru.ama.whereme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import androidx.datastore.preferences.core.*
import com.google.gson.Gson
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

class StorageMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()
    private val profile = SettingsUserInfoDomModel("synthetic-token", 12, 34, "Тест", "https://example.com/profile", true)
    private val previous = SettingsDomModel(listOf("0", "1", "0", "1", "0", "1", "0"), "06:15", "22:45", 123, 76, 88, 321, true)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = SessionCipher { key }

    private class Legacy : LegacyPreferences {
        val values = mutableMapOf<String, String>()
        var failCleanup = false
        override fun read(key: String) = values[key]
        override fun contains(key: String) = values.containsKey(key)
        override fun remove(key: String) {
            if (failCleanup) throw IOException("injected cleanup failure")
            values.remove(key)
        }
    }
    private fun store(file: File, migrations: List<DataMigration<Preferences>> = emptyList()): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        // The Android FileStorage uses SDK_INT=0 in local JVM tests. Okio supplies portable atomic replacement.
        return PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.toOkioPath() }),
            migrations = migrations, scope = scope)
    }
    private fun file() = File(temporary.newFolder(), "test.preferences_pb")
    private fun settings(file: File, legacy: Legacy) = DataStoreSettingsRepository(store(file, listOf(SettingsMigration(legacy))))
    private fun sessions(file: File, legacy: Legacy, encryption: SessionCipher = cipher) =
        DataStoreSessionRepository(store(file, listOf(SessionMigration(legacy, encryption))), encryption)
    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        scopes.clear()
    }
    @After fun close() = runBlocking { closeStores() }
    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected storage failure") } catch (_: IOException) { }
    }

    @Test fun newInstallHasSafeDefaultsWithoutNetwork() = runBlocking {
        val legacy = Legacy()
        val settings = settings(file(), legacy)
        assertEquals(SettingsDomModel.DEFAULT, settings.settings.first())
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        val current = sessions(file(), legacy).current()
        assertEquals(SettingsUserInfoDomModel.EMPTY, current.profile)
        assertTrue(current.generation.isNotBlank())
    }

    @Test fun allLegacySettingsIncludingExcludedWeekdaysSurvive() = runBlocking {
        val legacy = Legacy().apply { values["worktime"] = Gson().toJson(previous) }
        assertEquals(previous, settings(file(), legacy).settings.first())
        assertFalse(legacy.contains("worktime"))
    }

    @Test fun profileIsMigratedCompletelyAndOnlyEncryptedBytesArePersisted() = runBlocking {
        val legacy = Legacy().apply { values["jwt"] = Gson().toJson(profile) }
        val path = file()
        assertEquals(profile, sessions(path, legacy).current().profile)
        assertFalse(legacy.contains("jwt"))
        assertFalse(path.readBytes().toString(Charsets.ISO_8859_1).contains(profile.tokenJwt))
        closeStores()
        assertEquals(profile, sessions(path, Legacy()).current().profile)
    }

    @Test fun invalidSettingsAreRetainedInsteadOfSilentlyReset() = runBlocking {
        for (raw in listOf("{", "null", "[]", "{}", Gson().toJson(previous).replace("\"06:15\"", "\"28:15\""),
            Gson().toJson(previous).replace("\"minDist\":123", "\"minDist\":1.5"),
            Gson().toJson(previous).replace("\"minDist\":123", "\"minDist\":2147483648"),
            Gson().toJson(previous).replace("\"minDist\":123", "\"minDist\":\"123\""),
            Gson().toJson(previous).replace("\"days\":[\"0\",\"1\",\"0\",\"1\",\"0\",\"1\",\"0\"]", "\"days\":[\"1\"]"))) {
            val legacy = Legacy().apply { values["worktime"] = raw }
            expectFailure { settings(file(), legacy).settings.first() }
            assertEquals(raw, legacy.values["worktime"])
        }
    }

    @Test fun invalidSessionDoesNotBlockIndependentSettingsMigration() = runBlocking {
        val legacy = Legacy().apply { values["jwt"] = "broken"; values["worktime"] = Gson().toJson(previous) }
        expectFailure { sessions(file(), legacy).current() }
        assertEquals("broken", legacy.values["jwt"])
        assertEquals(previous, settings(file(), legacy).settings.first())
    }

    @Test fun invalidSettingsDoNotBlockIndependentSessionMigration() = runBlocking {
        val legacy = Legacy().apply { values["worktime"] = "broken"; values["jwt"] = Gson().toJson(profile) }
        expectFailure { settings(file(), legacy).settings.first() }
        assertEquals(profile, sessions(file(), legacy).current().profile)
        assertEquals("broken", legacy.values["worktime"])
    }

    @Test fun failedEncryptionLeavesLegacyCredentialsUntouched() = runBlocking {
        val legacy = Legacy().apply { values["jwt"] = Gson().toJson(profile) }
        val path = file()
        expectFailure { sessions(path, legacy, SessionCipher { throw IOException("key unavailable") }).current() }
        assertEquals(Gson().toJson(profile), legacy.values["jwt"])
        closeStores()
        assertEquals(profile, sessions(path, legacy).current().profile)
    }

    @Test fun failedCleanupIsRetriedWithoutOverwritingCommittedProfile() = runBlocking {
        val legacy = Legacy().apply { values["jwt"] = Gson().toJson(profile); failCleanup = true }
        val path = file()
        expectFailure { sessions(path, legacy).current() }
        assertTrue(path.exists())
        assertTrue(legacy.contains("jwt"))
        closeStores()
        legacy.values["jwt"] = Gson().toJson(profile.copy(tokenJwt = "stale-legacy-token"))
        legacy.failCleanup = false
        assertEquals(profile, sessions(path, legacy).current().profile)
        assertFalse(legacy.contains("jwt"))
    }

    @Test fun failedCleanupDoesNotOverwriteCommittedSettingsOnRetry() = runBlocking {
        val legacy = Legacy().apply { values["worktime"] = Gson().toJson(previous); failCleanup = true }
        val path = file()
        expectFailure { settings(path, legacy).settings.first() }
        closeStores()
        legacy.values["worktime"] = Gson().toJson(SettingsDomModel.DEFAULT)
        legacy.failCleanup = false
        assertEquals(previous, settings(path, legacy).settings.first())
    }

    @Test fun migrationKeepsAlreadyWrittenDataStoreValues() = runBlocking {
        val path = file()
        store(path).edit { SettingsPreferences.write(it, previous.copy(minDist = 999)) }
        closeStores()
        val legacy = Legacy().apply { values["worktime"] = Gson().toJson(previous) }
        assertEquals(previous.copy(minDist = 999), settings(path, legacy).settings.first())
    }

    @Test fun concurrentFieldUpdatesDoNotLoseEachOther() = runBlocking {
        val repository = settings(file(), Legacy())
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
        val repository = settings(file(), Legacy())
        try { repository.updateSettings { throw IllegalArgumentException("injected transform failure") }; fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(SettingsDomModel.DEFAULT, repository.settings.first())
    }

    @Test fun eachThemeSurvivesReopeningAndDoesNotEnableSchedule() = runBlocking {
        val path = file()
        for (theme in ThemeMode.entries) {
            settings(path, Legacy()).setThemeMode(theme)
            closeStores()
            val repository = settings(path, Legacy())
            assertEquals(theme, repository.themeMode.first())
            assertFalse(repository.settings.first().isEnable)
            closeStores()
        }
    }

    @Test fun reloginWithSameCredentialsInvalidatesPreviousGeneration() = runBlocking {
        val repository = sessions(file(), Legacy())
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
        val repository = sessions(file(), Legacy())
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

    @Test fun staleLegacyTokenCannotResurrectLoggedOutSession() = runBlocking {
        val path = file()
        val legacy = Legacy().apply { values["jwt"] = Gson().toJson(profile) }
        val repository = sessions(path, legacy)
        assertTrue(repository.clearIfCurrent(repository.current()))
        closeStores()
        legacy.values["jwt"] = Gson().toJson(profile)
        assertEquals(SettingsUserInfoDomModel.EMPTY, sessions(path, legacy).current().profile)
        assertFalse(legacy.contains("jwt"))
    }

    @Test fun activationRefreshDoesNotInvalidateCurrentLogout() = runBlocking {
        val repository = sessions(file(), Legacy())
        repository.signIn(profile)
        val beforeRefresh = repository.current()
        assertTrue(repository.updateActivation(beforeRefresh, false))
        assertTrue(repository.clearIfCurrent(beforeRefresh))
        assertEquals(SettingsUserInfoDomModel.EMPTY, repository.current().profile)
    }

    @Test fun activationUpdateComparesLatestStoredValue() = runBlocking {
        val repository = sessions(file(), Legacy())
        repository.signIn(profile)
        val expected = repository.current()
        assertTrue(repository.updateActivation(expected, false))
        assertTrue(repository.updateActivation(expected, true))
        assertTrue(repository.current().profile.isActivate)
    }

    @Test fun sessionLeaseSerializesLocalAckWithSignIn() = runBlocking {
        withTimeout(5_000) {
            val repository = sessions(file(), Legacy())
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
        val datastore = store(path, listOf(SessionMigration(Legacy(), cipher)))
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
        val legacy = Legacy().apply { values["worktime"] = Gson().toJson(SettingsDomModel.DEFAULT) }
        expectFailure { settings(path, legacy).settings.first() }
        assertTrue(legacy.contains("worktime"))
        closeStores()
        val persisted = store(path).data.first()
        assertEquals(2, persisted[SettingsPreferences.VERSION])
        assertEquals(previous, SettingsPreferences.read(persisted))
    }

    @Test fun corruptPreferencesFileIsRetainedWithoutDefaultReplacement() = runBlocking {
        val path = file()
        val raw = byteArrayOf(1, 2, 3)
        path.writeBytes(raw)
        val legacy = Legacy().apply { values["worktime"] = Gson().toJson(previous) }
        expectFailure { settings(path, legacy).settings.first() }
        assertArrayEquals(raw, path.readBytes())
        assertTrue(legacy.contains("worktime"))
    }

    @Test fun unknownSessionVersionIsNotOverwrittenDuringDowngrade() = runBlocking {
        val path = file()
        val codec = SessionPreferences(cipher)
        store(path).edit { codec.write(it, profile); it[SessionPreferences.VERSION] = 2 }
        closeStores()
        val legacy = Legacy().apply { values["jwt"] = Gson().toJson(profile.copy(tokenJwt = "stale-token")) }
        expectFailure { sessions(path, legacy).current() }
        assertTrue(legacy.contains("jwt"))
        closeStores()
        val persisted = store(path).data.first()
        assertEquals(2, persisted[SessionPreferences.VERSION])
        assertEquals(profile, codec.read(persisted).profile)
    }
}
