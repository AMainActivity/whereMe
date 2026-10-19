package ru.ama.whereme.data.database

import androidx.room.withTransaction
import javax.inject.Inject

fun interface LegacyHistoryAssignment {
    suspend fun apply(owner: String?)
}

class RoomLegacyHistoryAssignment @Inject constructor(private val database: AppDatabase) : LegacyHistoryAssignment {
    override suspend fun apply(owner: String?) {
        if (!database.locationDao().hasLegacyHistory()) return
        database.withTransaction {
            database.locationDao().assignLegacyHistory(owner)
            database.locationDao().finishLegacyAssignment()
        }
    }
}
