package ru.ama.whereme.data.sync

import androidx.room.withTransaction
import javax.inject.Inject
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.domain.repository.SessionRepository

class RoomPendingLocationStore @Inject constructor(
    private val database: AppDatabase,
    private val dao: LocationDao,
    private val mapper: WmMapper,
    private val sessions: SessionRepository
) : PendingLocationStore {
    override suspend fun snapshot(): PendingLocationBatch? {
        val session = sessions.current()
        val owner = session.profile.ownerKey ?: return null
        return database.withTransaction {
            val originals = dao.getPendingLocations(owner, BATCH_SIZE)
            if (originals.isEmpty()) return@withTransaction null
            // Preserve the current server coordinate transform exactly until its contract is verified.
            val posId = session.profile.posId.toString()
            val offset = if (posId.length <= OFFSET_ID_DIGITS) session.profile.posId else posId.substring(0, OFFSET_ID_DIGITS).toInt()
            val payload = mapper.mapDbModelsToEntities(originals).map { row ->
                row.copy(
                    latitude = row.latitude + coordinateOffset(offset),
                    longitude = row.longitude + coordinateOffset(offset + LONGITUDE_OFFSET_INCREMENT))
            }
            PendingLocationBatch(session, originals, payload)
        }
    }

    override suspend fun isCurrent(batch: PendingLocationBatch): Boolean =
        sessions.current().generation == batch.session.generation

    override suspend fun acknowledge(batch: PendingLocationBatch): Int = sessions.withCurrent(batch.session) {
        database.withTransaction {
            var count = 0
            for (row in batch.originals) {
                count += dao.acknowledgeUnchanged(
                    row._id, requireNotNull(row.ownerKey), row.datetime, row.datestart, row.dateend, row.info,
                    row.latitude, row.longitude, row.sourceId, row.accuracy, row.velocity
                )
            }
            count
        }
    } ?: 0
    companion object {
        const val BATCH_SIZE = 200
        private const val OFFSET_ID_DIGITS = 8
        private const val LONGITUDE_OFFSET_INCREMENT = 1
        internal fun coordinateOffset(value: Int): Double = "${value.toString().first()}.$value".toDouble()
    }
}
