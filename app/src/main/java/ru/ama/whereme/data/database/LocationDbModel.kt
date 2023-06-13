package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ama.whereme.data.database.LocationDbModel.Companion.tabLocations

@Entity(tableName = tabLocations)
data class LocationDbModel(
    val datetime: String,
    val datestart: Long,
    val dateend: Long? = null,
    val info: String? = null,
    val latitude: Double,
    val longitude: Double,
    val sourceId: Int,
    val accuracy: Float,
    val velocity: Float,
    val isWrite: Int
) {
    @PrimaryKey(autoGenerate = true)
    var _id: Long = 0

    companion object {
        const val tabLocations = "tab_locations"
    }
}