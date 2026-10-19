package ru.ama.whereme.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import ru.ama.whereme.data.database.LegacyHistoryAssignment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ama.whereme.data.di.SessionStore
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSessionRepository @Inject constructor(
    @SessionStore private val store: DataStore<Preferences>, cipher: SessionCipher,
    private val legacyHistory: LegacyHistoryAssignment
) : SessionRepository {
    private val codec = SessionPreferences(cipher)
    private val mutex = Mutex()
    override val session = store.data.map(codec::read).distinctUntilChanged()
        .onEach { legacyHistory.apply(it.profile.ownerKey) }.flowOn(Dispatchers.IO)
    override suspend fun signIn(profile: SettingsUserInfoDomModel) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Assign old history before changing credentials; unreadable credentials stay recoverable.
            val oldOwner = try { codec.read(store.data.first()).profile.ownerKey }
                catch (failure: java.io.IOException) { null }
            legacyHistory.apply(oldOwner)
            store.edit { codec.write(it, profile) }; Unit
        }
    }
    override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean = change(expected) { p, _ ->
        codec.write(p, SettingsUserInfoDomModel.EMPTY)
    }
    override suspend fun updateActivation(expected: SessionSnapshot, active: Boolean): Boolean = change(expected) { p, current ->
        if (current.profile.isActivate != active) codec.write(p, current.profile.copy(isActivate = active), current.generation)
    }
    private suspend fun change(expected: SessionSnapshot, action: (androidx.datastore.preferences.core.MutablePreferences, SessionSnapshot) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var changed = false
                store.edit { p ->
                    val current = codec.read(p)
                    if (current.generation == expected.generation) { action(p, current); changed = true }
                }
                changed
            }
        }
    override suspend fun <T> withCurrent(expected: SessionSnapshot, action: suspend () -> T): T? = mutex.withLock {
        if (current().generation != expected.generation) null else action()
    }
}
