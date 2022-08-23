package ru.ama.whereme.data.database


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsDomnModelOther(
  var minDist: Float,
  var accuracy: Float,
  var timeOfWaitAccuracy: Int,
  var timeOfWorkingWM: Int
) : Parcelable