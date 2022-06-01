package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocationDb(
      val datetime: String,
  val latitude : Long,
  val longitude: Long,
  val sourceId:Int,
  val accuracy: Int,
  val velocity: Int
) : Parcelable