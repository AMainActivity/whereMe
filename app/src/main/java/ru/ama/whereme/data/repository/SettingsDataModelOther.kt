package ru.ama.whereme.data.database


data class SettingsDataModelOther(
  var minDist: Float,
  var accuracy: Float,
  var timeOfWaitAccuracy: Int,
  var timeOfWorkingWM: Int
)