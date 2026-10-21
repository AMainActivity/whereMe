package ru.ama.whereme.data.database

import java.time.Instant
import androidx.room.Room
import androidx.room.withTransaction
import android.content.Context
import kotlinx.coroutines.flow.flowOf
import ru.ama.whereme.domain.entity.SessionSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.sync.RoomPendingLocationStore
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SessionRepository

/** Real Room/SQLite checks, isolated from the installed application's database. */
abstract class LocationDatabaseContract {
    protected abstract val context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: LocationDao
    private lateinit var store: RoomPendingLocationStore
    private lateinit var sessions: SessionRepository
    private val initialSession = SessionSnapshot(SettingsUserInfoDomModel("test-token", 12, 34, "Test", null, true), "test-generation")
    private var session = initialSession

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        dao = database.locationDao()
        sessions = object : SessionRepository {
            override val session get() = flowOf(this@LocationDatabaseContract.session)
            override suspend fun signIn(profile: SettingsUserInfoDomModel) = error("Unexpected signIn")
            override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean = error("Unexpected logout")
            override suspend fun updateActivation(expected: SessionSnapshot, active: Boolean): Boolean = error("Unexpected activation")
            override suspend fun <T> withCurrent(expected: SessionSnapshot, action: suspend () -> T): T? =
                if (this@LocationDatabaseContract.session == expected) action() else null
        }
        store = RoomPendingLocationStore(database, dao, WmMapper(), sessions)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(): Long = dao.insertLocation(
        LocationDbModel("1000", 1000, null, "synthetic", 10.5, 20.5, 1, 3.4f, 0f, 0, initialSession.profile.ownerKey)
            .apply { _id = 2_147_483_663L }
    )

    @Test
    fun lastRecordedTimeObservesInsertsAndStationaryIntervalUpdates() = runBlocking {
        assertNull(dao.observeLastRecordedTime().first())
        val id = insert()
        assertEquals(1000L, dao.observeLastRecordedTime().first())
        val observed = CompletableDeferred<Unit>()
        val update = async {
            withTimeout(5_000) {
                dao.observeLastRecordedTime().onEach { observed.complete(Unit) }.first { it == 3000L }
            }
        }
        observed.await()
        dao.updateInterval(id, 3000, "extended")
        assertEquals(3000L, update.await())
        // A later insertion with an older fix must not make the displayed time go backwards.
        dao.insertLocation(LocationDbModel("2000", 2000, null, null, 1.0, 2.0, 1, 5f, 0f, 0))
        assertEquals(3000L, dao.observeLastRecordedTime().first())
    }

    @Test
    fun emptyHistoryReturnsNull() = runBlocking {
        assertNull(dao.getLocation(1))
        assertNull(store.snapshot())
    }

    @Test
    fun unchangedSnapshotWithNullEndTimeIsAcknowledged() = runBlocking {
        val id = insert()
        val batch = requireNotNull(store.snapshot())
        assertEquals(id, batch.originals.single()._id)
        assertEquals(1, store.acknowledge(batch))
        assertEquals(0, dao.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).size)
    }

    @Test
    fun oldAckLeavesNewIntervalPending() = runBlocking {
        val id = insert()
        val batch = requireNotNull(store.snapshot())
        assertEquals(1, dao.updateInterval(id, 3000, "extended"))
        assertEquals(0, store.acknowledge(batch))
        val row = dao.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).single()
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
        assertEquals(id, dao.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).single()._id)
    }

    @Test
    fun sessionChangedAfterSnapshotDoesNotAcknowledge() = runBlocking {
        insert()
        val batch = requireNotNull(store.snapshot())
        session = initialSession.copy(profile = initialSession.profile.copy(tokenJwt = "new-test-token", posId = 99))
        assertEquals(0, store.acknowledge(batch))
        assertEquals(1, dao.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).size)
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
        val row = dao.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).single()
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

    @Test
    fun historyFlowEmitsAnInsertAfterInitialEmptyState() = runBlocking {
        withTimeout(5_000) {
            val initial = CompletableDeferred<Unit>()
            val updated = async {
                dao.getLocations(0, Long.MAX_VALUE).onEach { rows ->
                    if (rows.isEmpty()) initial.complete(Unit)
                }.first { it.isNotEmpty() }
            }
            initial.await()
            val id = insert()
            assertEquals(id, updated.await().single()._id)
        }
    }

    @Test
    fun otherProfilesAndAnonymousPointsAreNeverSelectedForUpload() = runBlocking {
        val old = insert()
        val anonymous = dao.insertLocation(LocationDbModel("2000", 2000, null, "local", 1.0, 2.0, 1, 5f, 0f, 0))
        session = initialSession.copy(profile = initialSession.profile.copy(posId = 99), generation = "new-login")
        assertNull(store.snapshot())
        session = initialSession.copy(generation = "same-owner-new-token", profile = initialSession.profile.copy(tokenJwt = "refreshed"))
        assertEquals(old, requireNotNull(store.snapshot()).originals.single()._id)
        assertNull(dao.getLocation(anonymous)?.ownerKey)
    }

    @Test
    fun backlogIsSplitIntoBoundedOrderedPages() = runBlocking {
        repeat(205) { dao.insertLocation(LocationDbModel("$it", it.toLong(), null, null, 1.0, 2.0, 1, 5f, 0f, 0, initialSession.profile.ownerKey)) }
        val first = requireNotNull(store.snapshot())
        assertEquals(200, first.originals.size)
        assertEquals(200, store.acknowledge(first))
        val second = requireNotNull(store.snapshot())
        assertEquals(5, second.originals.size)
        org.junit.Assert.assertTrue(second.originals.first()._id > first.originals.last()._id)
        assertEquals(5, store.acknowledge(second))
        assertNull(store.snapshot())
    }

    @Test
    fun dayQueryUsesAnExclusiveEndBoundary() = runBlocking {
        val first = dao.insertLocation(LocationDbModel("1000", 1000, null, null, 1.0, 2.0, 1, 5f, 0f, 0))
        dao.insertLocation(LocationDbModel("2000", 2000, null, null, 1.0, 2.0, 1, 5f, 0f, 0))
        assertEquals(listOf(first), dao.getLocations(1000, 2000).first().map { it._id })
    }

    @Test
    fun versionThreeHistorySurvivesMigrationAndIsAssignedOnlyOnce() = runBlocking {
        val name = "migration-${System.nanoTime()}.db"
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE tab_locations (datetime TEXT NOT NULL, datestart INTEGER NOT NULL, dateend INTEGER, info TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL, sourceId INTEGER NOT NULL, accuracy REAL NOT NULL, velocity REAL NOT NULL, isWrite INTEGER NOT NULL, _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        legacy.execSQL("INSERT INTO tab_locations VALUES ('1000',1000,2000,'history',1.0,2.0,1,5.0,0.0,0,42)")
        legacy.version = 3
        legacy.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4).build()
        try {
            val rows = migrated.locationDao()
            val original = requireNotNull(rows.getLocation(42))
            assertEquals("history", original.info)
            assertEquals(2000L, original.dateend)
            assertEquals(0, original.isWrite)
            // A new anonymous point must not become part of the old-history transfer.
            val newId = rows.insertLocation(LocationDbModel("3000", 3000, null, null, 1.0, 2.0, 1, 5f, 0f, 0))
            val assignment = RoomLegacyHistoryAssignment(migrated)
            assignment.apply(initialSession.profile.ownerKey)
            assignment.apply("another-owner")
            assertEquals(initialSession.profile.ownerKey, rows.getLocation(42)?.ownerKey)
            assertNull(rows.getLocation(newId)?.ownerKey)
            assertEquals(1, rows.getPendingLocations(requireNotNull(initialSession.profile.ownerKey), 200).size)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
    @Test
    fun repeatedSampleExtendsItsOwnRowEvenWhenRecordedOnAnotherCalendarDate() = runBlocking {
        val recorder = ru.ama.whereme.data.location.RoomLocationRecorder(database, dao, sessions)
        val sample = ru.ama.whereme.domain.entity.LocationSample(1.0, 2.0, 5f, 0f, Instant.ofEpochMilli(1_000L), 1L)
        val settings = ru.ama.whereme.domain.entity.SettingsDomModel.DEFAULT
        val first = recorder.record(sample, settings, null)
        assertEquals(first, recorder.record(sample.copy(time = Instant.ofEpochMilli(2_000)), settings, first))
        assertEquals(2_000L, dao.getLocation(first)?.dateend)
        assertEquals(initialSession.profile.ownerKey, dao.getLocation(first)?.ownerKey)
    }

    @Test
    fun movingBeyondMinimumDistanceCreatesANewRowWithOriginalSampleFields() = runBlocking {
        val recorder = ru.ama.whereme.data.location.RoomLocationRecorder(database, dao, sessions)
        val sample = ru.ama.whereme.domain.entity.LocationSample(1.0, 2.0, 5f, 3f, Instant.ofEpochMilli(1_000L), 1L)
        val settings = ru.ama.whereme.domain.entity.SettingsDomModel.DEFAULT
        val first = recorder.record(sample, settings, null)
        val moved = sample.copy(latitude = 1.01, accuracyMeters = 8f, speedMetersPerSecond = 4f, time = Instant.ofEpochMilli(2_000))
        val second = recorder.record(moved, settings, first)
        org.junit.Assert.assertNotEquals(first, second)
        val row = requireNotNull(dao.getLocation(second))
        assertEquals(moved.latitude, row.latitude, 0.0)
        assertEquals(moved.longitude, row.longitude, 0.0)
        assertEquals(moved.accuracyMeters, row.accuracy)
        assertEquals(moved.speedMetersPerSecond, row.velocity)
        assertEquals(moved.time.toEpochMilli(), row.datestart)
        assertEquals(0, row.isWrite)
        assertNull(dao.getLocation(first)?.dateend)
    }

    @Test
    fun gapAtLimitExtendsIntervalButLargerGapCreatesANewRow() = runBlocking {
        val recorder = ru.ama.whereme.data.location.RoomLocationRecorder(database, dao, sessions)
        val sample = ru.ama.whereme.domain.entity.LocationSample(1.0, 2.0, 5f, 0f, Instant.ofEpochMilli(1_000L), 1L)
        val settings = ru.ama.whereme.domain.entity.SettingsDomModel.DEFAULT
        val maxGap = (settings.timeOfWorkingWM.toLong() + settings.timeOfWaitAccuracy) * 1_000
        val first = recorder.record(sample, settings, null)
        val extendedAt = sample.time.toEpochMilli() + maxGap
        assertEquals(first, recorder.record(sample.copy(time = Instant.ofEpochMilli(extendedAt)), settings, first))
        val second = recorder.record(sample.copy(time = Instant.ofEpochMilli(extendedAt + maxGap + 1)), settings, first)
        org.junit.Assert.assertNotEquals(first, second)
        assertEquals(extendedAt, dao.getLocation(first)?.dateend)
    }

    @Test
    fun changingOwnerStartsANewIntervalInsteadOfExtendingTheOldUsersRow() = runBlocking {
        val recorder = ru.ama.whereme.data.location.RoomLocationRecorder(database, dao, sessions)
        val sample = ru.ama.whereme.domain.entity.LocationSample(1.0, 2.0, 5f, 0f, Instant.ofEpochMilli(1_000L), 1L)
        val settings = ru.ama.whereme.domain.entity.SettingsDomModel.DEFAULT
        val first = recorder.record(sample, settings, null)
        session = initialSession.copy(profile = initialSession.profile.copy(posId = 99), generation = "new")
        val second = recorder.record(sample.copy(time = Instant.ofEpochMilli(2_000)), settings, first)
        org.junit.Assert.assertNotEquals(first, second)
        assertNull(dao.getLocation(first)?.dateend)
        assertEquals(session.profile.ownerKey, dao.getLocation(second)?.ownerKey)
    }

    @Test
    fun midnightStartsANewHistoryRowForTheNextDay() = runBlocking {
        val recorder = ru.ama.whereme.data.location.RoomLocationRecorder(database, dao, sessions)
        val end = ru.ama.whereme.domain.usecase.DayRange.of(java.time.LocalDate.parse("2026-10-19")).end.toEpochMilli()
        val sample = ru.ama.whereme.domain.entity.LocationSample(1.0, 2.0, 5f, 0f, Instant.ofEpochMilli(end - 1_000), 1L)
        val settings = ru.ama.whereme.domain.entity.SettingsDomModel.DEFAULT
        val first = recorder.record(sample, settings, null)
        val second = recorder.record(sample.copy(time = Instant.ofEpochMilli(end)), settings, first)
        org.junit.Assert.assertNotEquals(first, second)
        assertEquals(listOf(second), dao.getLocations(end, end + 1_000).first().map { it._id })
    }

    @Test
    fun upgradeWithoutASavedProfileKeepsLegacyHistoryLocalAfterALaterLogin() = runBlocking {
        val id = dao.insertLocation(LocationDbModel("1000", 1000, null, "old-local", 1.0, 2.0, 1, 5f, 0f, 0))
        database.openHelper.writableDatabase.execSQL("INSERT INTO legacy_history VALUES (1, $id)")
        val assignment = RoomLegacyHistoryAssignment(database)
        assignment.apply(null)
        assignment.apply(initialSession.profile.ownerKey)
        val row = requireNotNull(dao.getLocation(id))
        assertEquals("old-local", row.info)
        assertNull(row.ownerKey)
        assertNull(store.snapshot())
    }

}
