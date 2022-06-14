package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ama.whereme.data.database.LocationDbModel.Companion.tabTestInfo

@Entity(tableName = tabTestInfo)
data class LocationDbModel(
  val datetime: String,
  val info: String?=null,
  val latitude : Double,
  val longitude: Double,
  val sourceId:Int,
  val accuracy: Float,
  val velocity: Float
)
{
	   @PrimaryKey(autoGenerate = true)
    var _id: Long = 0
	
	    companion object {
        const val tabTestInfo = "tab_locations"
		}
}