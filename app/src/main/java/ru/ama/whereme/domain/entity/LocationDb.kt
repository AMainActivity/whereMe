package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocationDb(
      val datetime: String,
      val info: String?=null,
  val latitude : Double,
  val longitude: Double,
  val sourceId:Int,
  val accuracy: Float,
  val velocity: Float
) : Parcelable