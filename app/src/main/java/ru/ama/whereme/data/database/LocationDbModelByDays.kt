package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ama.whereme.data.database.LocationDbModel.Companion.tabTestInfo
import java.time.OffsetDateTime

data class LocationDbModelByDays(
  val _id: Long,
  val datestart: Long,
  val dateend: Long?=null
)