package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ama.whereme.data.database.LocationDbModel.Companion.tabTestInfo

@Entity(tableName = tabTestInfo)
data class LocationDbModel(
  val datetime: String,
  val latitude : Long,
  val longitude: Long,
  val sourceId:Int,
  val accuracy: Int,
  val velocity: Int
)
{
	   @PrimaryKey(autoGenerate = true)
    var _id: Long = 0
	
	    companion object {
        const val tabTestInfo = "tab_locations"
		}
}