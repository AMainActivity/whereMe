package ru.ama.whereme.data.sync

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

/** Both raw rows and their legacy wire representation come from the same DB snapshot. */
data class PendingLocationBatch(
    val session: SettingsUserInfoDomModel,
    val originals: List<LocationDbModel>,
    val payload: List<LocationDb>
)

interface PendingLocationStore {
    suspend fun snapshot(): PendingLocationBatch?
    fun isCurrent(batch: PendingLocationBatch): Boolean
    suspend fun acknowledge(batch: PendingLocationBatch): Int
}
