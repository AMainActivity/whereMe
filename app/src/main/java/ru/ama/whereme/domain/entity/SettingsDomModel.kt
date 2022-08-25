package ru.ama.whereme.data.database


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsDomModel(
  var days: List<String>,
  var start: String,
  var end: String,
  var minDist: Int,
  var accuracy: Int,
  var timeOfWaitAccuracy: Int,
  var timeOfWorkingWM: Int
) : Parcelable