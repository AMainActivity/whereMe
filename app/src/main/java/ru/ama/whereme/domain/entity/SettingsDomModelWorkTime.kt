package ru.ama.whereme.data.database


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsDomModelWorkTime(
  var days: List<String>,
  var start: String,
  var end: String
) : Parcelable