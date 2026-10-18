package ru.ama.whereme.data.database

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.sync.RoomPendingLocationStore
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.WmRepository

/** Real Room/SQLite checks, isolated from the installed application's database. */
@RunWith(AndroidJUnit4::class)
class LocationSynchronizationTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: LocationDao
    private lateinit var store: RoomPendingLocationStore
    private val initialSession = SettingsUserInfoDomModel("test-token", 12, 34, "Test", null, true)
    private var session = initialSession

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = database.locationDao()
        val sessions = Proxy.newProxyInstance(
            WmRepository::class.java.classLoader, arrayOf(WmRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getWmUserInfoSetings") session
            else throw AssertionError("Unexpected call: ${method.name}")
        } as WmRepository
        store = RoomPendingLocationStore(database, dao, WmMapper(), sessions)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(): Long = dao.insertLocation(
        LocationDbModel("1000", 1000, null, "synthetic", 10.5, 20.5, 1, 3.4f, 0f, 0)
            .apply { _id = 2_147_483_663L }
    )

    @Test
    fun emptyHistoryReturnsNull() = runBlocking {
        assertNull(dao.getLastValue("01.01.1970"))
        assertNull(store.snapshot())
    }

    @Test
    fun unchangedSnapshotWithNullEndTimeIsAcknowledged() = runBlocking {
        val id = insert()
        val batch = requireNotNull(store.snapshot())
        assertEquals(id, batch.originals.single()._id)
        assertEquals(1, store.acknowledge(batch))
        assertEquals(0, dao.getPendingLocations().size)
    }

    @Test
    fun oldAckLeavesNewIntervalPending() = runBlocking {
        val id = insert()
        val batch = requireNotNull(store.snapshot())
        assertEquals(1, dao.updateInterval(id, 3000, "extended"))
        assertEquals(0, store.acknowledge(batch))
        val row = dao.getPendingLocations().single()
        assertEquals(id, row._id)
        assertEquals(3000L, row.dateend)
        assertEquals("extended", row.info)
        assertEquals(1, store.acknowledge(requireNotNull(store.snapshot())))
    }

    @Test
    fun intervalChangeAfterAckBecomesPendingAgain() = runBlocking {
        val id = insert()
        assertEquals(1, store.acknowledge(requireNotNull(store.snapshot())))
        dao.updateInterval(id, 3000, "extended")
        assertEquals(id, dao.getPendingLocations().single()._id)
    }

    @Test
    fun sessionChangedAfterSnapshotDoesNotAcknowledge() = runBlocking {
        insert()
        val batch = requireNotNull(store.snapshot())
        session = initialSession.copy(tokenJwt = "new-test-token", posId = 99)
        assertEquals(0, store.acknowledge(batch))
        assertEquals(1, dao.getPendingLocations().size)
    }

    @Test
    fun intervalTransactionRollsBackBothFields() = runBlocking {
        val id = insert()
        try {
            database.withTransaction {
                dao.updateInterval(id, 3000, "extended")
                throw IllegalStateException("synthetic rollback")
            }
        } catch (_: IllegalStateException) {
            // The injected failure must roll back the real Room transaction.
        }
        val row = dao.getPendingLocations().single()
        assertNull(row.dateend)
        assertEquals("synthetic", row.info)
    }

    @Test
    fun snapshotPreservesLegacyCoordinateTransform() = runBlocking {
        insert()
        val batch = requireNotNull(store.snapshot())
        val original = batch.originals.single()
        val payload = batch.payload.single()
        assertEquals(original._id, payload._id)
        assertEquals(original.latitude + 1.12, payload.latitude, 1e-9)
        assertEquals(original.longitude + 1.13, payload.longitude, 1e-9)
    }
}
