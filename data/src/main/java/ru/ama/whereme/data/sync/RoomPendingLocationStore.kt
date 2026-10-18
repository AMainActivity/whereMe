package ru.ama.whereme.data.sync

import androidx.room.withTransaction
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject

class RoomPendingLocationStore @Inject constructor(
    private val database: AppDatabase,
    private val dao: LocationDao,
    private val mapper: WmMapper,
    private val sessions: SessionRepository
) : PendingLocationStore {
    override suspend fun snapshot(): PendingLocationBatch? {
        val session = sessions.current()
        if (session.profile.tokenJwt.isBlank()) return null
        return database.withTransaction {
            val originals = dao.getPendingLocations()
            if (originals.isEmpty()) return@withTransaction null
            // Preserve the current server coordinate transform exactly until its contract is verified.
            val posId = session.profile.posId.toString()
            val offset = if (posId.length <= 8) session.profile.posId else posId.substring(0, 8).toInt()
            val payload = dao.getLocations4Net(offset).map(mapper::mapDbModelToEntity)
            check(originals.map { it._id } == payload.map { it._id })
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
                    row._id, row.datetime, row.datestart, row.dateend, row.info,
                    row.latitude, row.longitude, row.sourceId, row.accuracy, row.velocity
                )
            }
            count
        }
    } ?: 0
}
